package com.tradingbot.risk;

import com.tradingbot.config.AccountProperties;
import com.tradingbot.config.RiskProperties;
import com.tradingbot.entity.TradeSignal;
import com.tradingbot.entity.enums.SignalStatus;
import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.repository.ExecutedTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.tradingbot.util.RedisAvailabilityTracker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskManagerTest {

    @Mock
    private ExecutedTradeRepository tradeRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private RedisAvailabilityTracker redisTracker;

    private RiskManager riskManager;
    private RiskProperties riskProperties;
    private AccountProperties accountProperties;

    @BeforeEach
    void setUp() {
        riskProperties = new RiskProperties();
        riskProperties.setMaxRiskPerTrade(0.01);
        riskProperties.setMaxDailyLoss(0.03);
        riskProperties.setMaxOpenTrades(3);
        riskProperties.setMinimumConfidence(80);

        accountProperties = new AccountProperties();
        accountProperties.setBalance(10000.0);
        accountProperties.setLeverage(5);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        riskManager = new RiskManager(riskProperties, accountProperties, tradeRepository, redisTemplate, redisTracker);
    }

    @Test
    void evaluate_approvedWhenAllChecksPassed() {
        when(tradeRepository.countOpenTrades()).thenReturn(1L);

        TradeSignal signal = TradeSignal.builder()
                .symbol("BTCUSDT")
                .side(TradeSide.BUY)
                .confidence(90)
                .entryPrice(new BigDecimal("40000"))
                .stopLoss(new BigDecimal("39200"))
                .takeProfit(new BigDecimal("41600"))
                .status(SignalStatus.PENDING)
                .build();

        RiskCheckResult result = riskManager.evaluate(signal);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getPositionSize()).isGreaterThan(0);
    }

    @Test
    void evaluate_rejectedWhenConfidenceLow() {
        TradeSignal signal = TradeSignal.builder()
                .symbol("BTCUSDT")
                .confidence(50) // below 80
                .entryPrice(new BigDecimal("40000"))
                .stopLoss(new BigDecimal("39200"))
                .build();

        RiskCheckResult result = riskManager.evaluate(signal);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getReason()).contains("Confidence");
    }

    @Test
    void evaluate_rejectedWhenMaxTradesReached() {
        when(tradeRepository.countOpenTrades()).thenReturn(3L);

        TradeSignal signal = TradeSignal.builder()
                .symbol("BTCUSDT")
                .confidence(90)
                .entryPrice(new BigDecimal("40000"))
                .stopLoss(new BigDecimal("39200"))
                .build();

        RiskCheckResult result = riskManager.evaluate(signal);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getReason()).contains("Max open trades");
    }
}
