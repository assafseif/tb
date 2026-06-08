package com.tradingbot.indicators;

import com.tradingbot.market.CandleData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ManualIndicatorCalculator {

    public double calculateRSI(List<Double> closes, int period) {
        if (closes.size() < period + 1) {
            return 50.0;
        }

        double avgGain = 0.0;
        double avgLoss = 0.0;

        // Initial average gain/loss
        for (int i = 1; i <= period; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        // Wilder's smoothing for remaining bars
        for (int i = period + 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0.0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public double calculateEMA(List<Double> closes, int period) {
        if (closes.isEmpty()) return 0.0;
        if (closes.size() < period) {
            return closes.stream().mapToDouble(d -> d).average().orElse(0.0);
        }

        double k = 2.0 / (period + 1.0);

        // Seed with SMA of first `period` values
        double ema = 0.0;
        for (int i = 0; i < period; i++) {
            ema += closes.get(i);
        }
        ema /= period;

        // Apply EMA formula
        for (int i = period; i < closes.size(); i++) {
            ema = closes.get(i) * k + ema * (1.0 - k);
        }
        return ema;
    }

    public double calculateATR(List<CandleData> candles, int period) {
        if (candles.size() < period + 1) return 0.0;

        List<Double> trueRanges = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData curr = candles.get(i);
            double prevClose = candles.get(i - 1).getClose();
            double tr = Math.max(
                    curr.getHigh() - curr.getLow(),
                    Math.max(
                            Math.abs(curr.getHigh() - prevClose),
                            Math.abs(curr.getLow() - prevClose)
                    )
            );
            trueRanges.add(tr);
        }

        // Initial ATR = SMA of first `period` true ranges
        double atr = 0.0;
        for (int i = 0; i < period && i < trueRanges.size(); i++) {
            atr += trueRanges.get(i);
        }
        atr /= Math.min(period, trueRanges.size());

        // Wilder's smoothing
        for (int i = period; i < trueRanges.size(); i++) {
            atr = (atr * (period - 1) + trueRanges.get(i)) / period;
        }
        return atr;
    }

    public double calculateVolumeAverage(List<CandleData> candles, int period) {
        if (candles.isEmpty()) return 0.0;
        int limit = Math.min(period, candles.size());
        List<CandleData> recent = candles.subList(candles.size() - limit, candles.size());
        return recent.stream().mapToDouble(CandleData::getVolume).average().orElse(0.0);
    }

    public IndicatorResult calculate(String symbol, List<CandleData> candles) {
        if (candles == null || candles.size() < 2) {
            log.warn("Insufficient candle data for {} to calculate indicators", symbol);
            return IndicatorResult.builder()
                    .symbol(symbol)
                    .rsi14(50.0)
                    .ema20(0.0)
                    .ema50(0.0)
                    .ema200(0.0)
                    .atr14(0.0)
                    .volumeAvg20(0.0)
                    .currentVolume(0.0)
                    .calculatedWithTa4j(false)
                    .build();
        }

        List<Double> closes = candles.stream()
                .map(CandleData::getClose)
                .collect(Collectors.toList());

        double rsi = calculateRSI(closes, 14);
        double ema20 = calculateEMA(closes, 20);
        double ema50 = calculateEMA(closes, 50);
        double ema200 = calculateEMA(closes, 200);
        double atr = calculateATR(candles, 14);
        double volAvg = calculateVolumeAverage(candles, 20);
        double currVol = candles.get(candles.size() - 1).getVolume();

        return IndicatorResult.builder()
                .symbol(symbol)
                .rsi14(rsi)
                .ema20(ema20)
                .ema50(ema50)
                .ema200(ema200)
                .atr14(atr)
                .volumeAvg20(volAvg)
                .currentVolume(currVol)
                .calculatedWithTa4j(false)
                .build();
    }
}
