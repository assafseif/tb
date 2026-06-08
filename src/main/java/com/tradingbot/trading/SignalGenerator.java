package com.tradingbot.trading;

import com.tradingbot.ai.AiAnalysisService;
import com.tradingbot.ai.SentimentResult;
import com.tradingbot.config.ScoringProperties;
import com.tradingbot.entity.TradeSignal;
import com.tradingbot.entity.enums.SignalStatus;
import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.indicators.IndicatorResult;
import com.tradingbot.indicators.IndicatorService;
import com.tradingbot.market.MarketDataService;
import com.tradingbot.market.MarketSnapshot;
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
    private final TradeSignalRepository signalRepository;
    private final AiAnalysisService aiAnalysisService;

    public Flux<TradeSignal> generateSignals() {
        return marketDataService.getAllSnapshots()
                .flatMap(this::processSnapshot)
                .onErrorContinue((ex, obj) ->
                        log.error("Signal generation failed for {}: {}", obj, ex.getMessage()));
    }

    private Mono<TradeSignal> processSnapshot(MarketSnapshot snapshot) {
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

            // Ask AI to confirm with candle data before committing the signal
            return aiAnalysisService.confirmWithCandles(
                            snapshot.getSymbol(), score.getAction(), snapshot, indicators)
                    .flatMap(confirmation -> Mono.fromCallable(() -> {
                        boolean confirmed = isConfirmed(score.getAction(), confirmation);
                        log.info("Candle AI confirmation for {} {}: sentiment={} confidence={} reason={} → {}",
                                score.getAction(), snapshot.getSymbol(),
                                confirmation.getSentimentRaw(), confirmation.getConfidence(),
                                confirmation.getReason(),
                                confirmed ? "APPROVED" : "REJECTED");

                        if (!confirmed) return null;

                        TradeSignal signal = buildSignal(snapshot, indicators, score);
                        TradeSignal saved = signalRepository.save(signal);
                        log.info("Generated {} signal for {} (score={}, confidence={})",
                                score.getAction(), snapshot.getSymbol(),
                                String.format("%.1f", score.getTotalScore()), score.getConfidence());
                        return saved;
                    }).subscribeOn(Schedulers.boundedElastic()));
        })
        .onErrorResume(ex -> {
            log.error("Signal generation error for {}: {}", snapshot.getSymbol(), ex.getMessage());
            return Mono.empty();
        })
        .filter(signal -> signal != null);
    }

    private boolean isConfirmed(String action, SentimentResult confirmation) {
        String sentiment = confirmation.getSentimentRaw();
        if (sentiment == null) return false;
        return switch (action) {
            case "BUY"  -> "BULLISH".equalsIgnoreCase(sentiment);
            case "SELL" -> "BEARISH".equalsIgnoreCase(sentiment);
            default     -> false;
        };
    }

    private TradeSignal buildSignal(MarketSnapshot snapshot, IndicatorResult indicators, TradeScore score) {
        BigDecimal entryPrice = snapshot.getCurrentPrice();
        boolean isBuy = "BUY".equals(score.getAction());

        // SL = Entry ± 2 * ATR; TP = Entry ± 4 * ATR
        double atr = indicators.getAtr14();
        if (atr <= 0) {
            atr = entryPrice.doubleValue() * 0.01; // Fallback: 1% of price
        }

        BigDecimal atrBD = BigDecimal.valueOf(atr);
        BigDecimal sl, tp;

        if (isBuy) {
            sl = entryPrice.subtract(atrBD.multiply(BigDecimal.valueOf(2)));
            tp = entryPrice.add(atrBD.multiply(BigDecimal.valueOf(4)));
        } else {
            sl = entryPrice.add(atrBD.multiply(BigDecimal.valueOf(2)));
            tp = entryPrice.subtract(atrBD.multiply(BigDecimal.valueOf(4)));
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
                .sentimentScore(score.getSentimentScore())
                .trendScore(score.getTrendScore())
                .volumeScore(score.getVolumeScore())
                .rsiScore(score.getRsiScore())
                .build();
    }
}
