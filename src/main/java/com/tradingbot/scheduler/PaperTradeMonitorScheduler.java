package com.tradingbot.scheduler;

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
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaperTradeMonitorScheduler {

    private final ExecutedTradeRepository tradeRepository;
    private final MarketDataService marketDataService;
    private final RiskManager riskManager;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 15000, initialDelay = 10000)
    public void monitorOpenTrades() {
        if (!running.compareAndSet(false, true)) return;

        Flux.fromIterable(fetchOpenPaperTrades())
                .flatMap(trade -> marketDataService.getSnapshot(trade.getSymbol())
                        .map(snapshot -> {
                            BigDecimal price = snapshot.getCurrentPrice();
                            return checkAndClose(trade, price);
                        })
                        .onErrorResume(ex -> {
                            log.error("Monitor error for trade {}: {}", trade.getId(), ex.getMessage());
                            return reactor.core.publisher.Mono.just(false);
                        }), 4)
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(s -> running.set(false))
                .subscribe();
    }

    private List<ExecutedTrade> fetchOpenPaperTrades() {
        return tradeRepository.findByStatusOrderByCreatedAtDesc(TradeStatus.OPEN)
                .stream()
                .filter(ExecutedTrade::isPaperTrade)
                .toList();
    }

    private boolean checkAndClose(ExecutedTrade trade, BigDecimal currentPrice) {
        if (trade.getStopLoss() == null || trade.getTakeProfit() == null) return false;

        boolean isBuy = trade.getSide() == TradeSide.BUY;
        boolean slHit = isBuy
                ? currentPrice.compareTo(trade.getStopLoss()) <= 0
                : currentPrice.compareTo(trade.getStopLoss()) >= 0;
        boolean tpHit = isBuy
                ? currentPrice.compareTo(trade.getTakeProfit()) >= 0
                : currentPrice.compareTo(trade.getTakeProfit()) <= 0;

        if (!slHit && !tpHit) return false;

        String reason = tpHit ? "TP" : "SL";
        BigDecimal pnl = calculatePnl(trade, currentPrice);

        trade.setStatus(TradeStatus.CLOSED);
        trade.setClosePrice(currentPrice);
        trade.setRealizedPnl(pnl);
        tradeRepository.save(trade);

        if (pnl.doubleValue() < 0) riskManager.recordLoss(Math.abs(pnl.doubleValue()));
        else riskManager.recordGain(pnl.doubleValue());

        log.info("[PAPER CLOSED] {} {} {} hit @ {} | PnL: ${} | qty={}",
                reason, trade.getSymbol(), trade.getSide(),
                currentPrice, String.format("%.2f", pnl), trade.getQuantity());
        return true;
    }

    private BigDecimal calculatePnl(ExecutedTrade trade, BigDecimal exitPrice) {
        BigDecimal diff = exitPrice.subtract(trade.getEntryPrice());
        if (trade.getSide() == TradeSide.SELL) diff = diff.negate();
        return diff.multiply(trade.getQuantity());
    }
}
