package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.atomic.AtomicBoolean;

@Data
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    private boolean enabled = false;

    // Runtime-mutable flags (modified via REST API at runtime)
    private final AtomicBoolean tradingEnabledRuntime = new AtomicBoolean(false);
    private final AtomicBoolean paperTradingEnabledRuntime = new AtomicBoolean(true);

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.tradingEnabledRuntime.set(enabled);
    }

    public boolean isTradingActive() {
        return tradingEnabledRuntime.get();
    }

    public boolean isPaperTradingActive() {
        return paperTradingEnabledRuntime.get();
    }

    public void enableTrading() {
        tradingEnabledRuntime.set(true);
    }

    public void disableTrading() {
        tradingEnabledRuntime.set(false);
    }
}
