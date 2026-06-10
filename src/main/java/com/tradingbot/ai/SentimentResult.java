package com.tradingbot.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tradingbot.entity.enums.NewsUrgency;
import com.tradingbot.entity.enums.SentimentType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentimentResult {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("sentiment")
    private String sentimentRaw;

    @JsonProperty("confidence")
    private int confidence;

    @JsonProperty("impact")
    private int impact;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("urgency")
    private String urgency;

    public SentimentType getSentimentType() {
        if (sentimentRaw == null) return SentimentType.NEUTRAL;
        return switch (sentimentRaw.toUpperCase().trim()) {
            case "BULLISH" -> SentimentType.BULLISH;
            case "BEARISH" -> SentimentType.BEARISH;
            default -> SentimentType.NEUTRAL;
        };
    }

    public NewsUrgency getUrgencyType() {
        if (urgency == null) return NewsUrgency.WITHIN_4H;
        return switch (urgency.toUpperCase().trim()) {
            case "IMMEDIATE" -> NewsUrgency.IMMEDIATE;
            case "WITHIN_1H" -> NewsUrgency.WITHIN_1H;
            default          -> NewsUrgency.WITHIN_4H;
        };
    }

    public static SentimentResult neutral(String symbol) {
        SentimentResult result = new SentimentResult();
        result.setSymbol(symbol);
        result.setSentimentRaw("NEUTRAL");
        result.setConfidence(0);
        result.setImpact(0);
        result.setReason("Default neutral - AI analysis unavailable");
        return result;
    }
}
