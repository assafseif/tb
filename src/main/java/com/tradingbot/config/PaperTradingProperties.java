package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "paper-trading")
public class PaperTradingProperties {
    private boolean enabled = true;
}
