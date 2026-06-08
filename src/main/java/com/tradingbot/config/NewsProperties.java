package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "news")
public class NewsProperties {
    private List<String> rssFeeds;
    private int maxAgeHours = 4;        // only analyze articles within the scoring lookback window
    private int maxPerCycle = 10;       // max AI calls per scheduler run (rate/cost control)
}
