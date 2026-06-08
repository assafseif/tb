package com.tradingbot.trading;

import com.tradingbot.binance.BinanceFuturesApiClient;
import com.tradingbot.binance.BinanceOrderRequest;
import com.tradingbot.binance.BinanceOrderResponse;
import com.tradingbot.config.TradingProperties;
import com.tradingbot.entity.ExecutedTrade;
import com.tradingbot.entity.TradeSignal;
import com.tradingbot.entity.enums.SignalStatus;
import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.entity.enums.TradeStatus;
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
            // Risk evaluation
            RiskCheckResult riskResult = riskManager.evaluate(signal);
            if (!riskResult.isApproved()) {
                log.warn("Signal {} rejected by risk manager: {}", signal.getId(), riskResult.getReason());
                signalRepository.updateStatus(signal.getId(), SignalStatus.REJECTED, LocalDateTime.now());
                return null;
            }

            boolean isPaper = !tradingProperties.isTradingActive()
                    || tradingProperties.isPaperTradingActive();

            ExecutedTrade trade = buildTrade(signal, riskResult, isPaper);
            ExecutedTrade saved = tradeRepository.save(trade);

            log.info("[{}] Executing {} {} {} qty={} entry={} sl={} tp={}",
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
                return executeLiveTrade(trade, signal);
            }
            // Paper trade: mark signal executed, trade as OPEN
            return Mono.fromCallable(() -> {
                signalRepository.updateStatus(signal.getId(), SignalStatus.EXECUTED, LocalDateTime.now());
                trade.setStatus(TradeStatus.OPEN);
                return tradeRepository.save(trade);
            }).subscribeOn(Schedulers.boundedElastic());
        })
        .onErrorResume(ex -> {
            log.error("Trade processing error for signal {}: {}", signal.getId(), ex.getMessage());
            return Mono.fromCallable(() -> {
                signalRepository.updateStatus(signal.getId(), SignalStatus.REJECTED, LocalDateTime.now());
                return null;
            }).subscribeOn(Schedulers.boundedElastic()).then(Mono.empty());
        });
    }

    private Mono<ExecutedTrade> executeLiveTrade(ExecutedTrade trade, TradeSignal signal) {
        BinanceOrderRequest request = BinanceOrderRequest.builder()
                .symbol(trade.getSymbol())
                .side(trade.getSide().name())
                .type("MARKET")
                .quantity(trade.getQuantity().toPlainString())
                .build();

        return binanceApiClient.placeOrder(request)
                .flatMap(response -> Mono.fromCallable(() -> {
                    trade.setBinanceOrderId(response.getOrderId());
                    trade.setStatus(TradeStatus.OPEN);
                    signalRepository.updateStatus(signal.getId(), SignalStatus.EXECUTED, LocalDateTime.now());
                    log.info("Live order placed: orderId={} for {}", response.getOrderId(), trade.getSymbol());
                    return tradeRepository.save(trade);
                }).subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(ex -> {
                    log.error("Binance order placement failed: {}", ex.getMessage());
                    return Mono.fromCallable(() -> {
                        trade.setStatus(TradeStatus.FAILED);
                        trade.setErrorMessage(ex.getMessage());
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
