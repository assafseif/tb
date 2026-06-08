package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "risk")
public class RiskProperties {
    private double maxRiskPerTrade = 0.01;
    private double maxDailyLoss = 0.03;
    private int maxOpenTrades = 3;
    private int minimumConfidence = 80;
}
