package com.tradingbot.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingbot.binance.BinanceFuturesApiClient;
import com.tradingbot.entity.ExecutedTrade;
import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.entity.enums.TradeStatus;
import com.tradingbot.repository.ExecutedTradeRepository;
import com.tradingbot.risk.RiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveTradeStatusSyncScheduler {

    private final ExecutedTradeRepository tradeRepository;
    private final BinanceFuturesApiClient binanceApiClient;
    private final RiskManager riskManager;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void syncLiveTradeStatus() {
        if (!running.compareAndSet(false, true)) return;

        List<ExecutedTrade> openLiveTrades = tradeRepository.findByStatusOrderByCreatedAtDesc(TradeStatus.OPEN)
                .stream()
                .filter(t -> !t.isPaperTrade())
                .toList();

        if (openLiveTrades.isEmpty()) {
            running.set(false);
            return;
        }

        Flux.fromIterable(openLiveTrades)
                .groupBy(ExecutedTrade::getSymbol)
                .flatMap(group -> binanceApiClient.getPositionRisk(group.key())
                        .flatMapMany(positionData -> {
                            BigDecimal positionAmt = resolvePositionAmt(positionData);
                            if (positionAmt == null || positionAmt.compareTo(BigDecimal.ZERO) != 0) {
                                return group; // position still open, nothing to do
                            }
                            BigDecimal markPrice = resolveMarkPrice(positionData);
                            return group.doOnNext(trade -> closeTrade(trade, markPrice));
                        })
                        .onErrorResume(ex -> {
                            log.error("Sync error for {}: {}", group.key(), ex.getMessage());
                            return group;
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(s -> running.set(false))
                .subscribe();
    }

    private BigDecimal resolvePositionAmt(JsonNode data) {
        // positionRisk returns an array when queried per-symbol
        JsonNode node = data.isArray() ? data.get(0) : data;
        if (node == null || !node.has("positionAmt")) return null;
        return new BigDecimal(node.get("positionAmt").asText());
    }

    private BigDecimal resolveMarkPrice(JsonNode data) {
        JsonNode node = data.isArray() ? data.get(0) : data;
        if (node == null || !node.has("markPrice")) return null;
        String val = node.get("markPrice").asText();
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void closeTrade(ExecutedTrade trade, BigDecimal closePrice) {
        if (closePrice == null) closePrice = trade.getEntryPrice();

        BigDecimal diff = closePrice.subtract(trade.getEntryPrice());
        if (trade.getSide() == TradeSide.SELL) diff = diff.negate();
        BigDecimal pnl = diff.multiply(trade.getQuantity());

        trade.setStatus(TradeStatus.CLOSED);
        trade.setClosePrice(closePrice);
        trade.setRealizedPnl(pnl);
        trade.setUpdatedAt(LocalDateTime.now());
        tradeRepository.save(trade);

        if (pnl.doubleValue() < 0) riskManager.recordLoss(Math.abs(pnl.doubleValue()));
        else riskManager.recordGain(pnl.doubleValue());

        log.info("[LIVE CLOSED] {} {} @ {} | PnL: ${} | tradeId={}",
                trade.getSymbol(), trade.getSide(), closePrice,
                String.format("%.2f", pnl), trade.getId());
    }
}
