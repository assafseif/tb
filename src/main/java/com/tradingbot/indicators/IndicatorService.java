package com.tradingbot.indicators;

import com.tradingbot.market.CandleData;
import com.tradingbot.market.MarketSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private final Ta4jIndicatorCalculator ta4jCalculator;
    private final ManualIndicatorCalculator manualCalculator;

    public IndicatorResult calculate(MarketSnapshot snapshot) {
        // Use 1h candles for EMA200; use 1m candles for RSI and ATR for faster signals
        List<CandleData> primaryCandles = snapshot.getCandles1h();
        if (primaryCandles == null || primaryCandles.isEmpty()) {
            primaryCandles = snapshot.getCandles15m();
        }
        if (primaryCandles == null || primaryCandles.isEmpty()) {
            primaryCandles = snapshot.getCandles5m();
        }
        if (primaryCandles == null || primaryCandles.isEmpty()) {
            primaryCandles = snapshot.getCandles1m();
        }

        if (primaryCandles == null || primaryCandles.size() < 14) {
            log.warn("Insufficient candle data for {}, using defaults", snapshot.getSymbol());
            return IndicatorResult.builder()
                    .symbol(snapshot.getSymbol())
                    .rsi14(50.0)
                    .build();
        }

        // Try ta4j first; fall back to manual if it fails
        IndicatorResult result = ta4jCalculator.calculate(snapshot.getSymbol(), primaryCandles);

        if (result == null) {
            log.debug("Using manual indicators for {}", snapshot.getSymbol());
            result = manualCalculator.calculate(snapshot.getSymbol(), primaryCandles);
        } else {
            log.debug("Using ta4j indicators for {}", snapshot.getSymbol());
        }

        log.info("Indicators for {} — RSI={}, EMA20={}, EMA50={}, ATR={}, Trend={}",
                snapshot.getSymbol(),
                String.format("%.1f", result.getRsi14()),
                String.format("%.2f", result.getEma20()),
                String.format("%.2f", result.getEma50()),
                String.format("%.4f", result.getAtr14()),
                result.getTrend());

        return result;
    }
}
