package com.tradingbot.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardDto {

    private SystemStatus systemStatus;
    private TradingStats tradingStats;
    private List<MarketSummary> marketSummaries;
    private List<TradeSignalDto> recentSignals;
    private List<ExecutedTradeDto> recentTrades;

    @Data
    @Builder
    public static class SystemStatus {
        private boolean tradingEnabled;
        private boolean paperTradingMode;
        private long unprocessedNews;
        private long pendingSignals;
        private long openTrades;
    }

    @Data
    @Builder
    public static class TradingStats {
        private long totalTrades;
        private long openTrades;
        private long closedTrades;
        private BigDecimal totalPnl;
        private BigDecimal todayPnl;
        private double winRate;
    }

    @Data
    @Builder
    public static class MarketSummary {
        private String symbol;
        private BigDecimal currentPrice;
        private double rsi;
        private double ema20;
        private double ema50;
        private String trend;
        private String sentiment;
    }
}
