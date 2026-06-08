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

    public Mono<SentimentResult> confirmWithCandles(
            String symbol, String proposedAction,
            com.tradingbot.market.MarketSnapshot snapshot,
            com.tradingbot.indicators.IndicatorResult indicators) {

        String prompt = buildCandlePrompt(symbol, proposedAction, snapshot, indicators);
        AiRequestDto request = new AiRequestDto(prompt);

        return aiWebClient.post()
                .uri(aiProperties.getGeneratePath())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiResponseDto.class)
                .map(response -> parseCandleResponse(response, symbol))
                .retryWhen(Retry.backoff(1, Duration.ofSeconds(1))
                        .filter(ex -> !(ex instanceof WebClientResponseException.BadRequest))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .onErrorResume(ex -> {
                    log.error("Candle confirmation AI call failed for {} {}: {}",
                            proposedAction, symbol, ex.getMessage());
                    // On AI failure, allow trade to proceed (don't block on AI unavailability)
                    SentimentResult fallback = new SentimentResult();
                    fallback.setSymbol(symbol);
                    fallback.setSentimentRaw(proposedAction.equals("BUY") ? "BULLISH" : "BEARISH");
                    fallback.setConfidence(50);
                    fallback.setImpact(50);
                    fallback.setReason("AI unavailable — proceeding with technical signal");
                    return Mono.just(fallback);
                });
    }

    private SentimentResult parseCandleResponse(AiResponseDto response, String symbol) {
        if (response == null || response.getResponse() == null) {
            log.warn("Empty AI candle confirmation response for {}", symbol);
            return SentimentResult.neutral(symbol);
        }
        String raw = response.getResponse().trim();
        try {
            SentimentResult result = objectMapper.readValue(raw, SentimentResult.class);
            if (result != null && result.getSentimentRaw() != null) {
                normalizeConfidence(result);
                return result;
            }
        } catch (Exception ignored) {}

        Matcher matcher = JSON_PATTERN.matcher(raw);
        if (matcher.find()) {
            try {
                SentimentResult result = objectMapper.readValue(matcher.group(), SentimentResult.class);
                if (result != null && result.getSentimentRaw() != null) {
                    normalizeConfidence(result);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Candle confirmation JSON parse failed for {}: {}", symbol, e.getMessage());
            }
        }
        return SentimentResult.neutral(symbol);
    }

    private String buildCandlePrompt(String symbol, String proposedAction,
            com.tradingbot.market.MarketSnapshot snapshot,
            com.tradingbot.indicators.IndicatorResult indicators) {

        List<com.tradingbot.market.CandleData> candles = snapshot.getCandles15m();
        if (candles == null || candles.isEmpty()) candles = snapshot.getCandles5m();
        int limit = Math.min(20, candles == null ? 0 : candles.size());

        StringBuilder candleTable = new StringBuilder();
        if (candles != null && !candles.isEmpty()) {
            List<com.tradingbot.market.CandleData> recent = candles.subList(
                    Math.max(0, candles.size() - limit), candles.size());
            for (com.tradingbot.market.CandleData c : recent) {
                candleTable.append(String.format("  O:%.2f H:%.2f L:%.2f C:%.2f V:%.1f%n",
                        c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), c.getVolume()));
            }
        }

        return """
                You are a professional crypto futures trading assistant.
                The scoring engine has proposed a %s trade. Confirm or reject it based on candle data.

                Symbol: %s
                Proposed action: %s
                Current price: %.2f

                Technical indicators:
                - RSI(14): %.1f %s
                - EMA(20): %.2f | EMA(50): %.2f
                - Trend: %s
                - ATR(14): %.4f
                - Volume: %s average

                Last %d candles (15m timeframe) — oldest to newest:
                %s
                Look for: trend direction, momentum, candle patterns (engulfing, doji, pin bar),
                volume confirmation, support/resistance levels.

                Should this %s trade be executed given the candle data?
                Return ONLY valid JSON, no other text:
                {
                  "symbol": "%s",
                  "sentiment": "BULLISH",
                  "confidence": 75,
                  "impact": 70,
                  "reason": "Brief explanation (max 120 chars)"
                }
                Use "BULLISH" to confirm a BUY, "BEARISH" to confirm a SELL, "NEUTRAL" to reject.
                """.formatted(
                proposedAction, symbol, proposedAction,
                snapshot.getCurrentPrice().doubleValue(),
                indicators.getRsi14(),
                indicators.getRsi14() < 30 ? "(oversold)" : indicators.getRsi14() > 70 ? "(overbought)" : "(neutral)",
                indicators.getEma20(), indicators.getEma50(),
                indicators.getTrend(),
                indicators.getAtr14(),
                indicators.isHighVolume() ? "ABOVE" : "below",
                limit, candleTable,
                proposedAction, symbol
        );
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
