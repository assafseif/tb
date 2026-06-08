package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private String baseUrl = "http://localhost:8889";
    private String generatePath = "/generate";
    private int timeoutSeconds = 30;
    private int retryAttempts = 3;
}
