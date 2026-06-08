package com.tradingbot.trading;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeScore {
    private String symbol;
    private double totalScore;       // 0-100
    private double sentimentScore;   // 0-100 component
    private double trendScore;       // 0-100 component
    private double volumeScore;      // 0-100 component
    private double rsiScore;         // 0-100 component
    private int confidence;
    private String action;           // BUY / SELL / HOLD

    public boolean isBuySignal(double buyThreshold) {
        return totalScore >= buyThreshold;
    }

    public boolean isSellSignal(double sellThreshold) {
        return totalScore <= sellThreshold;
    }
}
