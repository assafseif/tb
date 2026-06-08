package com.tradingbot.indicators;

import com.tradingbot.market.CandleData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ManualIndicatorCalculatorTest {

    private ManualIndicatorCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ManualIndicatorCalculator();
    }

    @Test
    void rsi_returnsNeutralWhenInsufficientData() {
        List<Double> closes = List.of(100.0, 101.0, 102.0);
        double rsi = calculator.calculateRSI(closes, 14);
        assertThat(rsi).isEqualTo(50.0);
    }

    @Test
    void rsi_returns100WhenAllGains() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i <= 20; i++) closes.add(100.0 + i);
        double rsi = calculator.calculateRSI(closes, 14);
        assertThat(rsi).isGreaterThan(90.0);
    }

    @Test
    void rsi_returns0WhenAllLosses() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i <= 20; i++) closes.add(120.0 - i);
        double rsi = calculator.calculateRSI(closes, 14);
        assertThat(rsi).isLessThan(10.0);
    }

    @Test
    void ema_correctlySmooths() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 30; i++) closes.add(100.0);
        // Flat data: EMA should equal the flat price
        double ema = calculator.calculateEMA(closes, 20);
        assertThat(ema).isCloseTo(100.0, within(0.001));
    }

    @Test
    void atr_returnsZeroWhenInsufficientData() {
        List<CandleData> candles = List.of(
                CandleData.builder().open(100).high(105).low(98).close(102).volume(1000).build()
        );
        double atr = calculator.calculateATR(candles, 14);
        assertThat(atr).isEqualTo(0.0);
    }

    @Test
    void calculate_returnsValidResultForNormalData() {
        List<CandleData> candles = buildSampleCandles(250);
        IndicatorResult result = calculator.calculate("BTCUSDT", candles);

        assertThat(result.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(result.getRsi14()).isBetween(0.0, 100.0);
        assertThat(result.getEma20()).isGreaterThan(0.0);
        assertThat(result.getAtr14()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.isCalculatedWithTa4j()).isFalse();
    }

    private List<CandleData> buildSampleCandles(int count) {
        List<CandleData> candles = new ArrayList<>();
        double price = 40000.0;
        for (int i = 0; i < count; i++) {
            double change = (Math.random() - 0.5) * 200;
            price += change;
            candles.add(CandleData.builder()
                    .openTime(System.currentTimeMillis() - (count - i) * 60000L)
                    .open(price - 50)
                    .high(price + 100)
                    .low(price - 100)
                    .close(price)
                    .volume(1000 + Math.random() * 5000)
                    .closeTime(System.currentTimeMillis() - (count - i - 1) * 60000L)
                    .build());
        }
        return candles;
    }
}
