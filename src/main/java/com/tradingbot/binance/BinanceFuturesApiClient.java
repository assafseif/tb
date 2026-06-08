package com.tradingbot.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingbot.config.BinanceProperties;
import com.tradingbot.config.TradingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceFuturesApiClient {

    @Qualifier("binanceWebClient")
    private final WebClient binanceWebClient;
    private final BinanceProperties binanceProperties;
    private final TradingProperties tradingProperties;

    public Mono<BinanceOrderResponse> placeOrder(BinanceOrderRequest request) {
        if (!tradingProperties.isTradingActive()) {
            log.warn("Attempted to place live order while trading is disabled");
            return Mono.error(new IllegalStateException("Trading is disabled"));
        }

        long timestamp = System.currentTimeMillis();
        String queryString = BinanceSignatureUtil.buildQueryString(
                request, timestamp, binanceProperties.getRecvWindow());
        String signature = BinanceSignatureUtil.sign(queryString, binanceProperties.getApiSecret());
        String fullBody = queryString + "&signature=" + signature;

        log.info("Placing order: symbol={} side={} qty={}", request.getSymbol(),
                request.getSide(), request.getQuantity());

        String baseUrl = tradingProperties.isTradingActive()
                ? binanceProperties.getLiveBaseUrl() : binanceProperties.getTestnetBaseUrl();

        return binanceWebClient.post()
                .uri(baseUrl + "/fapi/v1/order")
                .header("X-MBX-APIKEY", binanceProperties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(BodyInserters.fromValue(fullBody))
                .retrieve()
                .bodyToMono(BinanceOrderResponse.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(ex -> !(ex instanceof WebClientResponseException.BadRequest)
                                && !(ex instanceof WebClientResponseException.Unauthorized)))
                .doOnSuccess(resp -> log.info("Order placed: orderId={} status={}", resp.getOrderId(), resp.getStatus()))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("Binance API error: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return new RuntimeException("Binance API error: " + ex.getResponseBodyAsString(), ex);
                });
    }

    public Mono<JsonNode> cancelOrder(String symbol, Long orderId) {
        long timestamp = System.currentTimeMillis();
        String queryString = "symbol=" + symbol + "&orderId=" + orderId
                + "&timestamp=" + timestamp
                + "&recvWindow=" + binanceProperties.getRecvWindow();
        String signature = BinanceSignatureUtil.sign(queryString, binanceProperties.getApiSecret());

        String baseUrl = tradingProperties.isTradingActive()
                ? binanceProperties.getLiveBaseUrl() : binanceProperties.getTestnetBaseUrl();

        return binanceWebClient.delete()
                .uri(baseUrl + "/fapi/v1/order?" + queryString + "&signature=" + signature)
                .header("X-MBX-APIKEY", binanceProperties.getApiKey())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(ex -> {
                    log.error("Failed to cancel order {}: {}", orderId, ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<JsonNode> getAccountInfo() {
        long timestamp = System.currentTimeMillis();
        String queryString = "timestamp=" + timestamp
                + "&recvWindow=" + binanceProperties.getRecvWindow();
        String signature = BinanceSignatureUtil.sign(queryString, binanceProperties.getApiSecret());

        String baseUrl2 = tradingProperties.isTradingActive()
                ? binanceProperties.getLiveBaseUrl() : binanceProperties.getTestnetBaseUrl();

        return binanceWebClient.get()
                .uri(baseUrl2 + "/fapi/v2/account?" + queryString + "&signature=" + signature)
                .header("X-MBX-APIKEY", binanceProperties.getApiKey())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(ex -> {
                    log.error("Failed to get account info: {}", ex.getMessage());
                    return Mono.empty();
                });
    }
}
