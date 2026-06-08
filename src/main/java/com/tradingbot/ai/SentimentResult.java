package com.tradingbot.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    public SentimentType getSentimentType() {
        if (sentimentRaw == null) return SentimentType.NEUTRAL;
        return switch (sentimentRaw.toUpperCase().trim()) {
            case "BULLISH" -> SentimentType.BULLISH;
            case "BEARISH" -> SentimentType.BEARISH;
            default -> SentimentType.NEUTRAL;
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
