package com.tradingbot.trading;

import com.tradingbot.ai.AiAnalysisService;
import com.tradingbot.ai.SentimentResult;
import com.tradingbot.binance.BinanceFuturesApiClient;
import com.tradingbot.binance.BinanceOrderRequest;
import com.tradingbot.binance.BinanceOrderResponse;
import com.tradingbot.config.AccountProperties;
import com.tradingbot.config.PaperTradingProperties;
import com.tradingbot.config.TradingProperties;
import jakarta.annotation.PostConstruct;
import com.tradingbot.entity.ExecutedTrade;
import com.tradingbot.entity.TradeSignal;
import com.tradingbot.entity.enums.SignalStatus;
import com.tradingbot.entity.enums.TradeStatus;
import com.tradingbot.indicators.IndicatorResult;
import com.tradingbot.indicators.IndicatorService;
import com.tradingbot.market.MarketDataService;
import com.tradingbot.market.MarketSnapshot;
import com.tradingbot.repository.ExecutedTradeRepository;
import com.tradingbot.repository.TradeSignalRepository;
import com.tradingbot.risk.RiskCheckResult;
import com.tradingbot.risk.RiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingEngine {

    private final TradeSignalRepository signalRepository;
    private final ExecutedTradeRepository tradeRepository;
    private final RiskManager riskManager;
    private final BinanceFuturesApiClient binanceApiClient;
    private final TradingProperties tradingProperties;
    private final PaperTradingProperties paperTradingProperties;
    private final AccountProperties accountProperties;
    private final AiAnalysisService aiAnalysisService;
    private final MarketDataService marketDataService;
    private final IndicatorService indicatorService;

    @PostConstruct
    void init() {
        if (paperTradingProperties.isEnabled()) {
            tradingProperties.getPaperTradingEnabledRuntime().set(true);
            log.info("Paper trading mode enabled from configuration");
        }
    }

    public Flux<ExecutedTrade> processPendingSignals() {
        return Mono.fromCallable(() ->
                signalRepository.findByStatusOrderByCreatedAtDesc(SignalStatus.PENDING))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::processSignal, 1) // serial to avoid race conditions
                .onErrorContinue((ex, obj) ->
                        log.error("Trade execution failed: {}", ex.getMessage()));
    }

    private Mono<ExecutedTrade> processSignal(TradeSignal signal) {
        return Mono.fromCallable(() -> {
            log.info("[ORDER ATTEMPT] signal={} {} {} | score={} confidence={} | entry={} sl={} tp={}",
                    signal.getId(), signal.getSymbol(), signal.getSide(),
                    String.format("%.1f", signal.getScore()), signal.getConfidence(),
                    signal.getEntryPrice(), signal.getStopLoss(), signal.getTakeProfit());

            RiskCheckResult riskResult = riskManager.evaluate(signal);
            if (!riskResult.isApproved()) {
                log.warn("[ORDER SKIPPED] signal={} {} {} → RISK REJECTED: {}",
                        signal.getId(), signal.getSymbol(), signal.getSide(), riskResult.getReason());
                signalRepository.updateStatus(signal.getId(), SignalStatus.REJECTED, LocalDateTime.now());
                return null;
            }

            boolean isPaper = !tradingProperties.isTradingActive()
                    || tradingProperties.isPaperTradingActive();

            if (!tradingProperties.isTradingActive()) {
                log.warn("[ORDER SKIPPED] signal={} {} {} → trading is DISABLED",
                        signal.getId(), signal.getSymbol(), signal.getSide());
                signalRepository.updateStatus(signal.getId(), SignalStatus.REJECTED, LocalDateTime.now());
                return null;
            }

            ExecutedTrade trade = buildTrade(signal, riskResult, isPaper);
            ExecutedTrade saved = tradeRepository.save(trade);

            log.info("[{}] Executing {} {} signal={} qty={} entry={} sl={} tp={}",
                    isPaper ? "PAPER" : "LIVE",
                    signal.getSymbol(), signal.getSide(),
                    signal.getId(), riskResult.getPositionSize(),
                    signal.getEntryPrice(), signal.getStopLoss(), signal.getTakeProfit());

            return saved;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(trade -> {
            if (trade == null) return Mono.empty();

            if (!trade.isPaperTrade()) {
                return binanceApiClient.getAvailableBalance()
                        .doOnNext(bal -> {
                            if (bal != null && bal > 0) accountProperties.setBalance(bal);
                        })
                        .onErrorResume(ex -> {
                            log.warn("Could not refresh balance before trade, using cached ${}: {}",
                                    String.format("%.2f", accountProperties.getBalance()), ex.getMessage());
                            return Mono.empty();
                        })
                        .then(executeLiveTrade(trade, signal));
            }
            return Mono.fromCallable(() -> {
                signalRepository.updateStatus(signal.getId(), SignalStatus.EXECUTED, LocalDateTime.now());
                trade.setStatus(TradeStatus.OPEN);
                return tradeRepository.save(trade);
            }).subscribeOn(Schedulers.boundedElastic());
        })
        .onErrorResume(ex -> {
            log.error("[ORDER SKIPPED] signal={} {} {} → EXCEPTION: {}",
                    signal.getId(), signal.getSymbol(), signal.getSide(), ex.getMessage());
            return Mono.fromCallable(() -> {
                signalRepository.updateStatus(signal.getId(), SignalStatus.REJECTED, LocalDateTime.now());
                return null;
            }).subscribeOn(Schedulers.boundedElastic()).then(Mono.empty());
        });
    }

    private Mono<ExecutedTrade> executeLiveTrade(ExecutedTrade trade, TradeSignal signal) {
        BinanceOrderRequest entryRequest = BinanceOrderRequest.builder()
                .symbol(trade.getSymbol())
                .side(trade.getSide().name())
                .type("MARKET")
                .quantity(trade.getQuantity().toPlainString())
                .build();

        String closeSide = trade.getSide() == com.tradingbot.entity.enums.TradeSide.BUY ? "SELL" : "BUY";
        String tradeSide = trade.getSide().name();

        return marketDataService.getSnapshot(trade.getSymbol())
                .flatMap(snapshot -> {
                    IndicatorResult indicators = indicatorService.calculate(snapshot);
                    return aiAnalysisService.confirmFinalOrder(
                            trade.getSymbol(), tradeSide,
                            trade.getStopLoss(), trade.getTakeProfit(),
                            snapshot, indicators);
                })
                .flatMap(confirmation -> {
                    boolean approved = tradeSide.equals("BUY")
                            ? "BULLISH".equalsIgnoreCase(confirmation.getSentimentRaw())
                            : "BEARISH".equalsIgnoreCase(confirmation.getSentimentRaw());

                    log.info("[FINAL GATE] {} {} — AI strategy check: {} confidence={} | {} → {}",
                            tradeSide, trade.getSymbol(),
                            confirmation.getSentimentRaw(), confirmation.getConfidence(),
                            confirmation.getReason(),
                            approved ? "APPROVED" : "REJECTED");

                    if (!approved) {
                        return Mono.fromCallable(() -> {
                            trade.setStatus(TradeStatus.FAILED);
                            trade.setErrorMessage("Final AI strategy gate rejected: " + confirmation.getReason());
                            signalRepository.updateStatus(signal.getId(), SignalStatus.REJECTED, LocalDateTime.now());
                            return tradeRepository.save(trade);
                        }).subscribeOn(Schedulers.boundedElastic()).then(Mono.empty());
                    }
                    return binanceApiClient.placeOrder(entryRequest)
                .flatMap(response -> Mono.fromCallable(() -> {
                    trade.setBinanceOrderId(response.getOrderId());
                    trade.setStatus(TradeStatus.OPEN);

                    // Recalculate SL/TP from actual fill price so stale signal price doesn't cause
                    // immediate-trigger errors — preserve the same absolute distance from the signal
                    BigDecimal fillPrice = (response.getAvgPrice() != null && response.getAvgPrice().compareTo(BigDecimal.ZERO) > 0)
                            ? response.getAvgPrice()
                            : trade.getEntryPrice();
                    boolean isBuy = trade.getSide() == com.tradingbot.entity.enums.TradeSide.BUY;
                    BigDecimal slDist = trade.getEntryPrice().subtract(trade.getStopLoss()).abs();
                    BigDecimal tpDist = trade.getTakeProfit().subtract(trade.getEntryPrice()).abs();
                    trade.setEntryPrice(fillPrice);
                    trade.setStopLoss(isBuy ? fillPrice.subtract(slDist) : fillPrice.add(slDist));
                    trade.setTakeProfit(isBuy ? fillPrice.add(tpDist) : fillPrice.subtract(tpDist));

                    signalRepository.updateStatus(signal.getId(), SignalStatus.EXECUTED, LocalDateTime.now());
                    return tradeRepository.save(trade);
                }).subscribeOn(Schedulers.boundedElastic()))
                // Give Binance ~2s head-start; placeAlgoOrder retries on -4509 if still not ready
                .delayElement(java.time.Duration.ofMillis(2000))
                .flatMap(saved -> binanceApiClient.placeStopLoss(saved.getSymbol(), closeSide, saved.getStopLoss())
                        .doOnSuccess(r -> log.info("SL order placed: orderId={} symbol={} stopPrice={}", r.getOrderId(), saved.getSymbol(), saved.getStopLoss()))
                        .onErrorResume(ex -> {
                            log.error("SL placement failed for trade {} — closing position immediately: {}", saved.getId(), ex.getMessage());
                            return binanceApiClient.closePositionMarket(saved.getSymbol(), closeSide)
                                    .flatMap(r -> Mono.fromCallable(() -> {
                                        saved.setStatus(TradeStatus.CLOSED);
                                        saved.setErrorMessage("SL placement failed; position closed at market: " + ex.getMessage());
                                        return tradeRepository.save(saved);
                                    }).subscribeOn(Schedulers.boundedElastic()))
                                    .onErrorResume(closeEx -> {
                                        log.error("Emergency close also failed for trade {} {}: {}", saved.getId(), saved.getSymbol(), closeEx.getMessage());
                                        return Mono.fromCallable(() -> {
                                            saved.setErrorMessage("SL failed AND emergency close failed: " + closeEx.getMessage());
                                            return tradeRepository.save(saved);
                                        }).subscribeOn(Schedulers.boundedElastic());
                                    })
                                    .then(Mono.error(new RuntimeException("SL failed — position closed, skipping TP")));
                        })
                        .thenReturn(saved))
                .flatMap(saved -> binanceApiClient.placeTakeProfit(saved.getSymbol(), closeSide, saved.getTakeProfit())
                        .doOnSuccess(r -> log.info("TP order placed: orderId={} symbol={} takeProfitPrice={}", r.getOrderId(), saved.getSymbol(), saved.getTakeProfit()))
                        .onErrorResume(ex -> {
                            log.error("Failed to place TP order for trade {}: {}", saved.getId(), ex.getMessage());
                            return Mono.empty();
                        })
                        .thenReturn(saved))
                .onErrorResume(ex -> {
                    log.error("Binance order placement failed: {}", ex.getMessage());
                    return Mono.fromCallable(() -> {
                        trade.setStatus(TradeStatus.FAILED);
                        trade.setErrorMessage(ex.getMessage());
                        signalRepository.updateStatus(signal.getId(), SignalStatus.REJECTED, LocalDateTime.now());
                        return tradeRepository.save(trade);
                    }).subscribeOn(Schedulers.boundedElastic());
                });
                }) // closes flatMap(confirmation ->
                .onErrorResume(ex -> {
                    log.error("Final gate or snapshot failed for signal={} {}: {}",
                            signal.getId(), trade.getSymbol(), ex.getMessage());
                    return Mono.fromCallable(() -> {
                        trade.setStatus(TradeStatus.FAILED);
                        trade.setErrorMessage("Pre-order gate failed: " + ex.getMessage());
                        signalRepository.updateStatus(signal.getId(), SignalStatus.REJECTED, LocalDateTime.now());
                        return tradeRepository.save(trade);
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    private ExecutedTrade buildTrade(TradeSignal signal, RiskCheckResult risk, boolean isPaper) {
        return ExecutedTrade.builder()
                .symbol(signal.getSymbol())
                .side(signal.getSide())
                .quantity(BigDecimal.valueOf(risk.getPositionSize()).setScale(6, java.math.RoundingMode.HALF_UP))
                .entryPrice(signal.getEntryPrice())
                .stopLoss(signal.getStopLoss())
                .takeProfit(signal.getTakeProfit())
                .status(TradeStatus.PENDING)
                .signalId(signal.getId())
                .paperTrade(isPaper)
                .build();
    }
}
