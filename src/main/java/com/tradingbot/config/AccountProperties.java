package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "account")
public class AccountProperties {
    private double balance = 10000.0;
    private int leverage = 5;
}
