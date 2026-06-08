package com.tradingbot.scheduler;

import com.tradingbot.binance.BinanceFuturesApiClient;
import com.tradingbot.config.TradingProperties;
import com.tradingbot.entity.ExecutedTrade;
import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.entity.enums.TradeStatus;
import com.tradingbot.market.MarketDataService;
import com.tradingbot.repository.ExecutedTradeRepository;
import com.tradingbot.risk.RiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveTradeMonitorScheduler {

    private final ExecutedTradeRepository tradeRepository;
    private final MarketDataService marketDataService;
    private final BinanceFuturesApiClient binanceApiClient;
    private final RiskManager riskManager;
    private final TradingProperties tradingProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void monitorLiveTrades() {
        if (!tradingProperties.isTradingActive() || tradingProperties.isPaperTradingActive()) return;
        if (!running.compareAndSet(false, true)) return;

        List<ExecutedTrade> openTrades = tradeRepository.findByStatusOrderByCreatedAtDesc(TradeStatus.OPEN)
                .stream()
                .filter(t -> !t.isPaperTrade())
                .toList();

        if (openTrades.isEmpty()) {
            running.set(false);
            return;
        }

        Flux.fromIterable(openTrades)
                .flatMap(trade -> marketDataService.getSnapshot(trade.getSymbol())
                        .flatMap(snapshot -> checkAndClose(trade, snapshot.getCurrentPrice()))
                        .onErrorResume(ex -> {
                            log.error("Live monitor error for trade {}: {}", trade.getId(), ex.getMessage());
                            return Mono.just(false);
                        }), 2)
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(s -> running.set(false))
                .subscribe();
    }

    private Mono<Boolean> checkAndClose(ExecutedTrade trade, BigDecimal currentPrice) {
        if (trade.getStopLoss() == null || trade.getTakeProfit() == null) return Mono.just(false);

        boolean isBuy = trade.getSide() == TradeSide.BUY;
        boolean slHit = isBuy
                ? currentPrice.compareTo(trade.getStopLoss()) <= 0
                : currentPrice.compareTo(trade.getStopLoss()) >= 0;
        boolean tpHit = isBuy
                ? currentPrice.compareTo(trade.getTakeProfit()) >= 0
                : currentPrice.compareTo(trade.getTakeProfit()) <= 0;

        if (!slHit && !tpHit) return Mono.just(false);

        String reason = tpHit ? "TP" : "SL";
        String closeSide = isBuy ? "SELL" : "BUY";
        String qty = trade.getQuantity().toPlainString();

        log.info("[LIVE] {} hit for {} {} @ {} (sl={} tp={})",
                reason, trade.getSymbol(), trade.getSide(), currentPrice,
                trade.getStopLoss(), trade.getTakeProfit());

        return binanceApiClient.marketClose(trade.getSymbol(), closeSide, qty)
                .flatMap(response -> Mono.fromCallable(() -> {
                    BigDecimal pnl = calculatePnl(trade, currentPrice);
                    trade.setStatus(TradeStatus.CLOSED);
                    trade.setClosePrice(currentPrice);
                    trade.setRealizedPnl(pnl);
                    tradeRepository.save(trade);
                    if (pnl.doubleValue() < 0) riskManager.recordLoss(Math.abs(pnl.doubleValue()));
                    else riskManager.recordGain(pnl.doubleValue());
                    log.info("[LIVE CLOSED] {} {} {} @ {} | PnL: ${} | closeOrderId={}",
                            reason, trade.getSymbol(), trade.getSide(), currentPrice,
                            String.format("%.2f", pnl), response.getOrderId());
                    return true;
                }).subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(ex -> {
                    log.error("Failed to close live trade {}: {}", trade.getId(), ex.getMessage());
                    return Mono.just(false);
                });
    }

    private BigDecimal calculatePnl(ExecutedTrade trade, BigDecimal exitPrice) {
        BigDecimal diff = exitPrice.subtract(trade.getEntryPrice());
        if (trade.getSide() == TradeSide.SELL) diff = diff.negate();
        return diff.multiply(trade.getQuantity());
    }
}
