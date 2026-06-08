package com.tradingbot.indicators;

import com.tradingbot.market.CandleData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
public class Ta4jIndicatorCalculator {

    public IndicatorResult calculate(String symbol, List<CandleData> candles) {
        try {
            if (candles == null || candles.size() < 20) {
                return null;
            }

            BarSeries series = buildSeries(symbol, candles);
            if (series.isEmpty()) return null;

            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            int endIdx = series.getEndIndex();

            RSIIndicator rsi14 = new RSIIndicator(closePrice, 14);
            EMAIndicator ema20 = new EMAIndicator(closePrice, 20);
            EMAIndicator ema50 = new EMAIndicator(closePrice, 50);
            EMAIndicator ema200 = new EMAIndicator(closePrice, 200);
            ATRIndicator atr14 = new ATRIndicator(series, 14);
            VolumeIndicator volumeIndicator = new VolumeIndicator(series);

            // Volume average (manual from ta4j VolumeIndicator)
            double volSum = 0;
            int volPeriod = Math.min(20, endIdx + 1);
            for (int i = endIdx - volPeriod + 1; i <= endIdx; i++) {
                volSum += volumeIndicator.getValue(i).doubleValue();
            }
            double volAvg = volPeriod > 0 ? volSum / volPeriod : 0;
            double currVol = volumeIndicator.getValue(endIdx).doubleValue();

            return IndicatorResult.builder()
                    .symbol(symbol)
                    .rsi14(rsi14.getValue(endIdx).doubleValue())
                    .ema20(ema20.getValue(endIdx).doubleValue())
                    .ema50(ema50.getValue(endIdx).doubleValue())
                    .ema200(ema200.getValue(endIdx).doubleValue())
                    .atr14(atr14.getValue(endIdx).doubleValue())
                    .volumeAvg20(volAvg)
                    .currentVolume(currVol)
                    .calculatedWithTa4j(true)
                    .build();

        } catch (Exception e) {
            log.warn("ta4j calculation failed for {}: {} — falling back to manual", symbol, e.getMessage());
            return null;
        }
    }

    private BarSeries buildSeries(String symbol, List<CandleData> candles) {
        BaseBarSeries series = new BaseBarSeries(symbol);
        series.setMaximumBarCount(500);

        for (CandleData c : candles) {
            try {
                ZonedDateTime endTime = Instant.ofEpochMilli(c.getCloseTime())
                        .atZone(ZoneId.systemDefault());
                Bar bar = new BaseBar(
                        Duration.ofMinutes(1),
                        endTime,
                        DecimalNum.valueOf(c.getOpen()),
                        DecimalNum.valueOf(c.getHigh()),
                        DecimalNum.valueOf(c.getLow()),
                        DecimalNum.valueOf(c.getClose()),
                        DecimalNum.valueOf(c.getVolume()),
                        DecimalNum.valueOf(0)
                );
                series.addBar(bar);
            } catch (Exception e) {
                log.trace("Skipped malformed candle: {}", e.getMessage());
            }
        }
        return series;
    }
}
