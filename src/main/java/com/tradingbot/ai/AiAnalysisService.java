package com.tradingbot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingbot.config.AiProperties;
import com.tradingbot.config.BinanceProperties;
import com.tradingbot.dto.AiRequestDto;
import com.tradingbot.dto.AiResponseDto;
import com.tradingbot.entity.NewsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    @Qualifier("aiWebClient")
    private final WebClient aiWebClient;
    private final AiProperties aiProperties;
    private final BinanceProperties binanceProperties;
    private final ObjectMapper objectMapper;

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^{}]*\"symbol\"[^{}]*\\}",
            Pattern.DOTALL);

    public Mono<SentimentResult> analyzeSentiment(NewsEvent newsEvent) {
        String prompt = buildPrompt(newsEvent);
        AiRequestDto request = new AiRequestDto(prompt);

        return aiWebClient.post()
                .uri(aiProperties.getGeneratePath())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiResponseDto.class)
                .map(response -> parseResponse(response, newsEvent))
                .retryWhen(Retry.backoff(aiProperties.getRetryAttempts(), Duration.ofSeconds(2))
                        .filter(ex -> !(ex instanceof WebClientResponseException.BadRequest))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .onErrorResume(ex -> {
                    log.error("AI analysis failed for news id={}: {}", newsEvent.getId(), ex.getMessage());
                    return Mono.just(SentimentResult.neutral(determineFallbackSymbol(newsEvent)));
                });
    }

    private String buildPrompt(NewsEvent newsEvent) {
        List<String> symbols = binanceProperties.getSymbols();
        String symbolList = String.join(", ", symbols);

        return """
                Analyze this cryptocurrency news article for trading sentiment.
                Available trading symbols: %s

                News Title: %s
                News Content: %s

                Determine:
                1. The most relevant trading symbol from the list above
                2. Sentiment: BULLISH, BEARISH, or NEUTRAL
                3. Confidence: 0-100 (how certain you are)
                4. Impact: 0-100 (how significant this news is)
                5. Brief reason (max 150 chars)

                Return ONLY valid JSON in this exact format, no other text:
                {
                  "symbol": "BTCUSDT",
                  "sentiment": "BULLISH",
                  "confidence": 75,
                  "impact": 60,
                  "reason": "Brief explanation here"
                }
                """.formatted(
                symbolList,
                newsEvent.getTitle(),
                truncate(newsEvent.getContent(), 800)
        );
    }

    private SentimentResult parseResponse(AiResponseDto response, NewsEvent newsEvent) {
        if (response == null || response.getResponse() == null) {
            log.warn("Empty AI response for news id={}", newsEvent.getId());
            return SentimentResult.neutral(determineFallbackSymbol(newsEvent));
        }

        String rawResponse = response.getResponse().trim();
        log.debug("AI raw response: {}", rawResponse);

        // Try direct JSON parse first
        try {
            SentimentResult result = objectMapper.readValue(rawResponse, SentimentResult.class);
            if (isValidResult(result)) {
                normalizeConfidence(result);
                return result;
            }
        } catch (Exception ignored) {
            // Fall through to regex extraction
        }

        // Try to extract JSON from response text
        Matcher matcher = JSON_PATTERN.matcher(rawResponse);
        if (matcher.find()) {
            try {
                SentimentResult result = objectMapper.readValue(matcher.group(), SentimentResult.class);
                if (isValidResult(result)) {
                    normalizeConfidence(result);
                    return result;
                }
            } catch (Exception e) {
                log.warn("JSON extraction failed for news id={}: {}", newsEvent.getId(), e.getMessage());
            }
        }

        log.warn("Could not parse AI response for news id={}, using NEUTRAL", newsEvent.getId());
        return SentimentResult.neutral(determineFallbackSymbol(newsEvent));
    }

    private boolean isValidResult(SentimentResult result) {
        return result != null
                && result.getSymbol() != null
                && result.getSentimentRaw() != null
                && binanceProperties.getSymbols().contains(result.getSymbol());
    }

    private void normalizeConfidence(SentimentResult result) {
        result.setConfidence(Math.max(0, Math.min(100, result.getConfidence())));
        result.setImpact(Math.max(0, Math.min(100, result.getImpact())));
    }

    private String determineFallbackSymbol(NewsEvent newsEvent) {
        if (newsEvent.getCategories() != null) {
            String cats = newsEvent.getCategories().toUpperCase();
            if (cats.contains("ETH")) return "ETHUSDT";
            if (cats.contains("BNB")) return "BNBUSDT";
            if (cats.contains("SOL")) return "SOLUSDT";
        }
        return "BTCUSDT";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
