package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "scoring")
public class ScoringProperties {
    private Weights weights = new Weights();
    private double buyThreshold = 80.0;
    private double sellThreshold = 20.0;

    @Data
    public static class Weights {
        private double sentiment = 0.40;
        private double trend = 0.25;
        private double volume = 0.15;
        private double rsi = 0.20;
    }
}
