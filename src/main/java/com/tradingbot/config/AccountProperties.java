package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "account")
public class AccountProperties {
    private int leverage;
    // Populated at runtime by AccountBalanceSyncService — not config-bound
    private volatile double balance = 0.0;
}
