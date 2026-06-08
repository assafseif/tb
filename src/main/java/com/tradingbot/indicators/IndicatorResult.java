package com.tradingbot.indicators;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorResult {
    private String symbol;
    private double rsi14;
    private double ema20;
    private double ema50;
    private double ema200;
    private double atr14;
    private double volumeAvg20;
    private double currentVolume;
    private boolean calculatedWithTa4j;

    public String getTrend() {
        if (ema20 > ema50 && ema50 > ema200) return "STRONG_UPTREND";
        if (ema20 > ema50) return "UPTREND";
        if (ema20 < ema50 && ema50 < ema200) return "STRONG_DOWNTREND";
        if (ema20 < ema50) return "DOWNTREND";
        return "SIDEWAYS";
    }

    public boolean isOverbought() {
        return rsi14 > 70;
    }

    public boolean isOversold() {
        return rsi14 < 30;
    }

    public boolean isHighVolume() {
        return volumeAvg20 > 0 && currentVolume > volumeAvg20 * 1.5;
    }
}
