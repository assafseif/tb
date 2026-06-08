package com.tradingbot.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingbot.config.BinanceProperties;
import com.tradingbot.config.TradingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceFuturesClient {

    @Qualifier("binanceWebClient")
    private final WebClient binanceWebClient;
    private final BinanceProperties binanceProperties;
    private final TradingProperties tradingProperties;
    private final ObjectMapper objectMapper;

    private String baseUrl() {
        return tradingProperties.isTradingActive()
                ? binanceProperties.getLiveBaseUrl()
                : binanceProperties.getTestnetBaseUrl();
    }

    public Mono<BigDecimal> getCurrentPrice(String symbol) {
        return binanceWebClient.get()
                .uri(baseUrl() + "/fapi/v1/ticker/price?symbol={symbol}", symbol)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> new BigDecimal(node.get("price").asText()))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .onErrorResume(ex -> {
                    log.error("Failed to fetch price for {}: {}", symbol, ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<List<CandleData>> getKlines(String symbol, String interval, int limit) {
        return binanceWebClient.get()
                .uri(baseUrl() + "/fapi/v1/klines?symbol={symbol}&interval={interval}&limit={limit}",
                        symbol, interval, limit)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseKlines)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .onErrorResume(ex -> {
                    log.error("Failed to fetch klines for {} {}: {}", symbol, interval, ex.getMessage());
                    return Mono.just(List.of());
                });
    }

    public Mono<MarketSnapshot> getMarketSnapshot(String symbol) {
        Mono<BigDecimal> priceMono = getCurrentPrice(symbol);
        Mono<List<CandleData>> candles1m = getKlines(symbol, "1m", binanceProperties.getKlineLimit());
        Mono<List<CandleData>> candles5m = getKlines(symbol, "5m", binanceProperties.getKlineLimit());
        Mono<List<CandleData>> candles15m = getKlines(symbol, "15m", 100);
        Mono<List<CandleData>> candles1h = getKlines(symbol, "1h", 50);

        return Mono.zip(priceMono.defaultIfEmpty(BigDecimal.ZERO),
                        candles1m, candles5m, candles15m, candles1h)
                .map(tuple -> MarketSnapshot.builder()
                        .symbol(symbol)
                        .currentPrice(tuple.getT1())
                        .candles1m(tuple.getT2())
                        .candles5m(tuple.getT3())
                        .candles15m(tuple.getT4())
                        .candles1h(tuple.getT5())
                        .fetchedAt(java.time.LocalDateTime.now())
                        .build());
    }

    private List<CandleData> parseKlines(JsonNode node) {
        List<CandleData> candles = new ArrayList<>();
        if (!node.isArray()) return candles;

        for (JsonNode bar : node) {
            try {
                CandleData candle = CandleData.builder()
                        .openTime(bar.get(0).asLong())
                        .open(Double.parseDouble(bar.get(1).asText()))
                        .high(Double.parseDouble(bar.get(2).asText()))
                        .low(Double.parseDouble(bar.get(3).asText()))
                        .close(Double.parseDouble(bar.get(4).asText()))
                        .volume(Double.parseDouble(bar.get(5).asText()))
                        .closeTime(bar.get(6).asLong())
                        .quoteVolume(Double.parseDouble(bar.get(7).asText()))
                        .trades(bar.get(8).asInt())
                        .build();
                candles.add(candle);
            } catch (Exception e) {
                log.warn("Failed to parse candle: {}", e.getMessage());
            }
        }
        return candles;
    }
}
