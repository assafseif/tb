package com.tradingbot.trading;

import com.tradingbot.config.ScoringProperties;
import com.tradingbot.config.TradingProperties;
import com.tradingbot.entity.enums.NewsUrgency;
import com.tradingbot.entity.TradeSignal;
import com.tradingbot.entity.enums.SignalStatus;
import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.entity.enums.TradeStatus;
import com.tradingbot.indicators.IndicatorResult;
import com.tradingbot.indicators.IndicatorService;
import com.tradingbot.market.MarketDataService;
import com.tradingbot.market.MarketSnapshot;
import com.tradingbot.repository.ExecutedTradeRepository;
import com.tradingbot.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalGenerator {

    private final MarketDataService marketDataService;
    private final IndicatorService indicatorService;
    private final ScoringEngine scoringEngine;
    private final ScoringProperties scoringProperties;
    private final TradingProperties tradingProperties;
    private final TradeSignalRepository signalRepository;
    private final ExecutedTradeRepository tradeRepository;

    public Flux<TradeSignal> generateSignals() {
        return marketDataService.getAllSnapshots()
                .flatMap(snapshot -> processSnapshot(snapshot, NewsUrgency.WITHIN_4H))
                .onErrorContinue((ex, obj) ->
                        log.error("Signal generation failed for {}: {}", obj, ex.getMessage()));
    }

    public Mono<TradeSignal> generateSignalForSymbol(String symbol, NewsUrgency urgency) {
        return marketDataService.getAllSnapshots()
                .filter(s -> s.getSymbol().equals(symbol))
                .next()
                .flatMap(snapshot -> processSnapshot(snapshot, urgency))
                .onErrorResume(ex -> {
                    log.error("Immediate signal generation failed for {}: {}", symbol, ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<TradeSignal> processSnapshot(MarketSnapshot snapshot, NewsUrgency urgency) {
        return Mono.fromCallable(() ->
                        !tradeRepository.findBySymbolAndStatusOrderByCreatedAtDesc(
                                snapshot.getSymbol(), TradeStatus.OPEN).isEmpty())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(hasOpenPosition -> {
                    if (hasOpenPosition) {
                        log.debug("{}: skipping signal generation — position already open", snapshot.getSymbol());
                        return Mono.empty();
                    }
                    return processSnapshotInner(snapshot, urgency);
                });
    }

    private Mono<TradeSignal> processSnapshotInner(MarketSnapshot snapshot, NewsUrgency urgency) {
        return Mono.fromCallable(() -> {
            IndicatorResult indicators = indicatorService.calculate(snapshot);
            TradeScore score = scoringEngine.score(snapshot.getSymbol(), indicators);

            if ("HOLD".equals(score.getAction())) {
                log.debug("{}: HOLD (score={})", snapshot.getSymbol(), String.format("%.1f", score.getTotalScore()));
                return null;
            }
            return new Object[]{indicators, score};
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(arr -> {
            if (arr == null) return Mono.empty();
            IndicatorResult indicators = (IndicatorResult) arr[0];
            TradeScore score = (TradeScore) arr[1];

            String techReject = technicalGateReject(score.getAction(), indicators);
            if (techReject != null) {
                log.debug("{}: technical gate rejected — {}", snapshot.getSymbol(), techReject);
                return Mono.empty();
            }

            return Mono.fromCallable(() -> {
                TradeSignal signal = buildSignal(snapshot, indicators, score, urgency);
                TradeSignal saved = signalRepository.save(signal);
                log.info("Generated {} signal for {} (score={}, confidence={}) — passed technical gate",
                        score.getAction(), snapshot.getSymbol(),
                        String.format("%.1f", score.getTotalScore()), score.getConfidence());
                return saved;
            }).subscribeOn(Schedulers.boundedElastic());
        })
        .onErrorResume(ex -> {
            log.error("Signal generation error for {}: {}", snapshot.getSymbol(), ex.getMessage());
            return Mono.empty();
        })
        .filter(signal -> signal != null);
    }

    /**
     * Returns a rejection reason if technical indicators do not support the action, null if all pass.
     *
     * BUY requires: RSI not overbought, trend not bearish, volume acceptable
     * SELL requires: RSI not oversold, trend not bullish, volume acceptable
     */
    private String technicalGateReject(String action, IndicatorResult ind) {
        String trend = ind.getTrend();
        boolean isBuy = "BUY".equals(action);

        if (isBuy) {
            if (ind.getRsi14() > 75)
                return "RSI overbought (" + String.format("%.1f", ind.getRsi14()) + " > 75)";
            if ("STRONG_DOWNTREND".equals(trend) || "DOWNTREND".equals(trend))
                return "Trend bearish (" + trend + ") for BUY";
            if (ind.getVolumeAvg20() > 0 && ind.getCurrentVolume() < ind.getVolumeAvg20() * 0.5)
                return "Volume too low (< 50% of average)";
        } else {
            if (ind.getRsi14() < 25)
                return "RSI oversold (" + String.format("%.1f", ind.getRsi14()) + " < 25) for SELL";
            if ("STRONG_UPTREND".equals(trend) || "UPTREND".equals(trend))
                return "Trend bullish (" + trend + ") for SELL";
            if (ind.getVolumeAvg20() > 0 && ind.getCurrentVolume() < ind.getVolumeAvg20() * 0.5)
                return "Volume too low (< 50% of average)";
        }
        return null;
    }

    private TradeSignal buildSignal(MarketSnapshot snapshot, IndicatorResult indicators, TradeScore score, NewsUrgency urgency) {
        BigDecimal entryPrice = snapshot.getCurrentPrice();
        boolean isBuy = "BUY".equals(score.getAction());

        double atr = indicators.getAtr14();
        if (atr <= 0) {
            atr = entryPrice.doubleValue() * 0.01; // Fallback: 1% of price
        }

        // In testing mode use small but viable multipliers — large enough to survive
        // the 2-4s between entry fill and algo order placement without immediately triggering
        double slMultiplier = tradingProperties.isTestingMode() ? 0.5 : 2.0;
        double tpMultiplier = tradingProperties.isTestingMode() ? 1.0 : 4.0;

        BigDecimal atrBD = BigDecimal.valueOf(atr);
        BigDecimal sl, tp;

        if (isBuy) {
            sl = entryPrice.subtract(atrBD.multiply(BigDecimal.valueOf(slMultiplier)));
            tp = entryPrice.add(atrBD.multiply(BigDecimal.valueOf(tpMultiplier)));
        } else {
            sl = entryPrice.add(atrBD.multiply(BigDecimal.valueOf(slMultiplier)));
            tp = entryPrice.subtract(atrBD.multiply(BigDecimal.valueOf(tpMultiplier)));
        }

        return TradeSignal.builder()
                .symbol(snapshot.getSymbol())
                .side(isBuy ? TradeSide.BUY : TradeSide.SELL)
                .score(score.getTotalScore())
                .entryPrice(entryPrice.setScale(8, RoundingMode.HALF_UP))
                .stopLoss(sl.setScale(8, RoundingMode.HALF_UP))
                .takeProfit(tp.setScale(8, RoundingMode.HALF_UP))
                .confidence((int) score.getConfidence())
                .status(SignalStatus.PENDING)
                .urgency(urgency)
                .sentimentScore(score.getSentimentScore())
                .trendScore(score.getTrendScore())
                .volumeScore(score.getVolumeScore())
                .rsiScore(score.getRsiScore())
                .build();
    }
}
