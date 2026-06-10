package com.tradingbot.risk;

import com.tradingbot.config.AccountProperties;
import com.tradingbot.config.RiskProperties;
import com.tradingbot.entity.TradeSignal;
import com.tradingbot.repository.ExecutedTradeRepository;
import com.tradingbot.util.RedisAvailabilityTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskManager {

    private final RiskProperties riskProperties;
    private final AccountProperties accountProperties;
    private final ExecutedTradeRepository tradeRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisAvailabilityTracker redisTracker;

    private static final String DAILY_LOSS_KEY = "risk:daily_loss:";
    private static final String DAILY_GAIN_KEY = "risk:daily_gain:";

    public RiskCheckResult evaluate(TradeSignal signal) {
        // Check minimum AI sentiment confidence
        if (signal.getConfidence() < riskProperties.getMinimumConfidence()) {
            return RiskCheckResult.rejected(
                    "AI confidence %d below minimum %d".formatted(
                            signal.getConfidence(), riskProperties.getMinimumConfidence()));
        }

        // Check max open trades
        long openTrades = tradeRepository.countOpenTrades();
        if (openTrades >= riskProperties.getMaxOpenTrades()) {
            return RiskCheckResult.rejected(
                    "Max open trades reached (%d/%d)".formatted(
                            openTrades, riskProperties.getMaxOpenTrades()));
        }

        // One position per symbol — Binance one-way mode merges same-symbol orders
        // into a single position, so a second entry would just increase size, not open a grid level.
        boolean symbolAlreadyOpen = !tradeRepository
                .findBySymbolAndStatusOrderByCreatedAtDesc(signal.getSymbol(), com.tradingbot.entity.enums.TradeStatus.OPEN)
                .isEmpty();
        if (symbolAlreadyOpen) {
            return RiskCheckResult.rejected(
                    "Position already open for %s — wait for SL/TP close".formatted(signal.getSymbol()));
        }

        // Check daily loss limit
        double dailyLoss = getDailyLoss();
        double maxAllowedLoss = accountProperties.getBalance() * riskProperties.getMaxDailyLoss();
        if (dailyLoss >= maxAllowedLoss) {
            return RiskCheckResult.rejected(
                    "Daily loss limit reached: $%.2f / $%.2f".formatted(dailyLoss, maxAllowedLoss));
        }

        // Check entry/stop-loss validity
        if (signal.getEntryPrice() == null || signal.getStopLoss() == null) {
            return RiskCheckResult.rejected("Missing entry price or stop loss");
        }

        // Calculate position size based on 1% risk rule
        double riskAmount = accountProperties.getBalance() * riskProperties.getMaxRiskPerTrade();
        double entryPrice = signal.getEntryPrice().doubleValue();
        double stopLossPrice = signal.getStopLoss().doubleValue();
        double riskPerUnit = Math.abs(entryPrice - stopLossPrice);

        if (riskPerUnit <= 0) {
            return RiskCheckResult.rejected("Invalid stop loss distance");
        }

        // position size in contracts (quantity of asset)
        double positionSize = riskAmount / riskPerUnit;
        // Apply leverage
        positionSize = positionSize * accountProperties.getLeverage();

        // Never let the notional value exceed available margin × leverage
        double maxPositionByMargin = (accountProperties.getBalance() * accountProperties.getLeverage()) / entryPrice;
        positionSize = Math.min(positionSize, maxPositionByMargin);

        // Enforce minimum lot size — but never let it push past the margin cap
        double minQty = minimumQuantity(signal.getSymbol(), entryPrice);
        if (minQty > maxPositionByMargin) {
            return RiskCheckResult.rejected(
                    "Insufficient balance for minimum notional on %s".formatted(signal.getSymbol()));
        }
        positionSize = Math.max(positionSize, minQty);

        // Snap to the symbol's step size
        double step = stepSize(signal.getSymbol());
        positionSize = Math.floor(positionSize / step) * step;

        if (positionSize <= 0) {
            return RiskCheckResult.rejected("Calculated position size is zero");
        }

        log.info("Risk check APPROVED for {} - position={}, risk=${}, openTrades={}/{}",
                signal.getSymbol(), positionSize,
                String.format("%.2f", riskAmount), openTrades,
                riskProperties.getMaxOpenTrades());

        return RiskCheckResult.approved(positionSize, riskAmount);
    }

    public void recordLoss(double lossAmount) {
        if (lossAmount <= 0 || !redisTracker.isAvailable()) return;
        String key = DAILY_LOSS_KEY + LocalDate.now();
        try {
            redisTemplate.opsForValue().increment(key, lossAmount);
            redisTemplate.expire(key, Duration.ofHours(25));
            redisTracker.markAvailable();
        } catch (Exception e) {
            redisTracker.markUnavailable("recordLoss");
        }
    }

    public void recordGain(double gainAmount) {
        if (gainAmount <= 0 || !redisTracker.isAvailable()) return;
        String key = DAILY_GAIN_KEY + LocalDate.now();
        try {
            redisTemplate.opsForValue().increment(key, gainAmount);
            redisTemplate.expire(key, Duration.ofHours(25));
            redisTracker.markAvailable();
        } catch (Exception e) {
            redisTracker.markUnavailable("recordGain");
        }
    }

    private double getDailyLoss() {
        if (!redisTracker.isAvailable()) return 0.0;
        String key = DAILY_LOSS_KEY + LocalDate.now();
        try {
            Object val = redisTemplate.opsForValue().get(key);
            redisTracker.markAvailable();
            if (val instanceof Number n) return n.doubleValue();
            if (val instanceof String s) return Double.parseDouble(s);
        } catch (Exception e) {
            redisTracker.markUnavailable("getDailyLoss");
        }
        return 0.0;
    }

    public double getDailyLossPercent() {
        return getDailyLoss() / accountProperties.getBalance() * 100.0;
    }

    public boolean isDailyLimitBreached() {
        return getDailyLoss() >= accountProperties.getBalance() * riskProperties.getMaxDailyLoss();
    }

    private double stepSize(String symbol) {
        return switch (symbol) {
            case "BTCUSDT", "ETHUSDT" -> 0.001;
            case "BNBUSDT", "SOLUSDT" -> 0.01;
            default -> 0.01;
        };
    }

    private double minimumQuantity(String symbol, double entryPrice) {
        double step = switch (symbol) {
            case "BTCUSDT" -> 0.001;
            case "ETHUSDT" -> 0.001;
            case "BNBUSDT" -> 0.01;
            case "SOLUSDT" -> 0.01;
            default        -> 0.01;
        };
        double minNotional = switch (symbol) {
            case "BTCUSDT" -> 50.0;
            case "ETHUSDT" -> 20.0;
            case "BNBUSDT" -> 5.0;
            case "SOLUSDT" -> 5.0;
            default        -> 10.0;
        };
        // Smallest multiple of step that meets the minimum notional
        double minQtyForNotional = Math.ceil(minNotional / entryPrice / step) * step;
        return Math.max(step, minQtyForNotional);
    }
}
