package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "news")
public class NewsProperties {
    private List<String> rssFeeds;
    private int maxAgeMinutes = 5;      // only analyze articles published within this window
    private int maxPerCycle = 10;       // max AI calls per scheduler run (rate/cost control)
}
