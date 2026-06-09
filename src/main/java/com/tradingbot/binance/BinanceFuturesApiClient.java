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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceFuturesApiClient {

    // tickSize per symbol — determines max decimal places for triggerPrice
    private static final Map<String, BigDecimal> TICK_SIZE = Map.of(
            "BTCUSDT", new BigDecimal("0.10"),
            "ETHUSDT", new BigDecimal("0.01"),
            "BNBUSDT", new BigDecimal("0.010"),
            "SOLUSDT", new BigDecimal("0.0100")
    );

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

        return binanceWebClient.post()
                .uri(binanceProperties.getActiveBaseUrl() + "/fapi/v1/order")
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

        return binanceWebClient.delete()
                .uri(binanceProperties.getActiveBaseUrl() + "/fapi/v1/order?" + queryString + "&signature=" + signature)
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

        return binanceWebClient.get()
                .uri(binanceProperties.getActiveBaseUrl() + "/fapi/v2/account?" + queryString + "&signature=" + signature)
                .header("X-MBX-APIKEY", binanceProperties.getApiKey())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(ex -> {
                    log.error("Failed to get account info: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<JsonNode> getPositionRisk(String symbol) {
        long timestamp = System.currentTimeMillis();
        String queryString = "symbol=" + symbol
                + "&timestamp=" + timestamp
                + "&recvWindow=" + binanceProperties.getRecvWindow();
        String signature = BinanceSignatureUtil.sign(queryString, binanceProperties.getApiSecret());

        return binanceWebClient.get()
                .uri(binanceProperties.getActiveBaseUrl() + "/fapi/v2/positionRisk?" + queryString + "&signature=" + signature)
                .header("X-MBX-APIKEY", binanceProperties.getApiKey())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(ex -> {
                    log.error("Failed to get position risk for {}: {}", symbol, ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<BigDecimal> getTodayRealizedPnl() {
        long startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        long timestamp = now;
        String queryString = "incomeType=REALIZED_PNL"
                + "&startTime=" + startOfDay
                + "&endTime=" + now
                + "&limit=1000"
                + "&timestamp=" + timestamp
                + "&recvWindow=" + binanceProperties.getRecvWindow();
        String signature = BinanceSignatureUtil.sign(queryString, binanceProperties.getApiSecret());

        return binanceWebClient.get()
                .uri(binanceProperties.getActiveBaseUrl() + "/fapi/v1/income?" + queryString + "&signature=" + signature)
                .header("X-MBX-APIKEY", binanceProperties.getApiKey())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    BigDecimal total = BigDecimal.ZERO;
                    if (node.isArray()) {
                        for (JsonNode entry : node) {
                            total = total.add(new BigDecimal(entry.get("income").asText("0")));
                        }
                    }
                    return total;
                })
                .onErrorResume(ex -> {
                    log.error("Failed to fetch today's realized PnL from Binance: {}", ex.getMessage());
                    return Mono.just(BigDecimal.ZERO);
                });
    }

    public Mono<Double> getAvailableBalance() {
        return getAccountInfo()
                .map(node -> node.get("availableBalance").asDouble())
                .onErrorResume(ex -> {
                    log.error("Failed to parse available balance: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    // SL/TP use /fapi/v1/algoOrder with algoType=CONDITIONAL and triggerPrice (not /fapi/v1/order)
    // closeSide = opposite of position side (SELL for BUY position, BUY for SELL position)
    public Mono<BinanceOrderResponse> placeStopLoss(String symbol, String closeSide, BigDecimal stopPrice) {
        return placeAlgoOrder(symbol, closeSide, "STOP_MARKET", stopPrice, "SL");
    }

    public Mono<BinanceOrderResponse> placeTakeProfit(String symbol, String closeSide, BigDecimal takeProfitPrice) {
        return placeAlgoOrder(symbol, closeSide, "TAKE_PROFIT_MARKET", takeProfitPrice, "TP");
    }

    public Mono<BinanceOrderResponse> closePositionMarket(String symbol, String closeSide) {
        log.info("Placing emergency market close: symbol={} side={}", symbol, closeSide);
        return Mono.defer(() -> {
            long timestamp = System.currentTimeMillis();
            String queryString = "symbol=" + symbol
                    + "&side=" + closeSide
                    + "&type=MARKET"
                    + "&closePosition=true"
                    + "&timestamp=" + timestamp
                    + "&recvWindow=" + binanceProperties.getRecvWindow();
            String signature = BinanceSignatureUtil.sign(queryString, binanceProperties.getApiSecret());

            return binanceWebClient.post()
                    .uri(binanceProperties.getActiveBaseUrl() + "/fapi/v1/order")
                    .header("X-MBX-APIKEY", binanceProperties.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .body(BodyInserters.fromValue(queryString + "&signature=" + signature))
                    .retrieve()
                    .bodyToMono(BinanceOrderResponse.class);
        })
        .doOnSuccess(resp -> log.info("Emergency close filled: orderId={} symbol={}", resp.getOrderId(), symbol))
        .onErrorMap(WebClientResponseException.class, ex -> {
            log.error("Emergency close failed: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return new RuntimeException("Emergency close failed: " + ex.getResponseBodyAsString(), ex);
        });
    }

    private BigDecimal roundToTickSize(String symbol, BigDecimal price) {
        BigDecimal tick = TICK_SIZE.getOrDefault(symbol, new BigDecimal("0.01"));
        int scale = tick.stripTrailingZeros().scale();
        return price.setScale(Math.max(scale, 0), RoundingMode.FLOOR);
    }

    private Mono<BinanceOrderResponse> placeAlgoOrder(String symbol, String side, String orderType,
                                                       BigDecimal triggerPrice, String label) {
        BigDecimal roundedPrice = roundToTickSize(symbol, triggerPrice);

        log.info("Placing {} algo order: symbol={} side={} triggerPrice={} (raw {})",
                label, symbol, side, roundedPrice, triggerPrice);

        // Mono.defer ensures a fresh timestamp (and signature) on every retry attempt
        return Mono.defer(() -> {
                    long timestamp = System.currentTimeMillis();
                    String queryString = "symbol=" + symbol
                            + "&side=" + side
                            + "&algoType=CONDITIONAL"
                            + "&type=" + orderType
                            + "&triggerPrice=" + roundedPrice.toPlainString()
                            + "&closePosition=true"
                            + "&timestamp=" + timestamp
                            + "&recvWindow=" + binanceProperties.getRecvWindow();
                    String signature = BinanceSignatureUtil.sign(queryString, binanceProperties.getApiSecret());

                    return binanceWebClient.post()
                            .uri(binanceProperties.getActiveBaseUrl() + "/fapi/v1/algoOrder")
                            .header("X-MBX-APIKEY", binanceProperties.getApiKey())
                            .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                            .body(BodyInserters.fromValue(queryString + "&signature=" + signature))
                            .retrieve()
                            .bodyToMono(BinanceOrderResponse.class);
                })
                // Binance takes ~3s to register a new position; retry up to 4x at 1.5s intervals on -4509
                .retryWhen(Retry.fixedDelay(4, Duration.ofMillis(1500))
                        .filter(ex -> ex instanceof WebClientResponseException &&
                                ((WebClientResponseException) ex).getResponseBodyAsString().contains("-4509"))
                        .doBeforeRetry(rs -> log.warn("{} position not yet available (-4509), retrying ({}/4)...",
                                symbol, rs.totalRetries() + 1)))
                .doOnSuccess(resp -> log.info("{} algo order placed: algoId={} symbol={} triggerPrice={}",
                        label, resp.getAlgoId(), symbol, roundedPrice))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("Binance {} algo order failed: {} - {}", label, ex.getStatusCode(), ex.getResponseBodyAsString());
                    return new RuntimeException("Binance " + label + " order failed: " + ex.getResponseBodyAsString(), ex);
                });
    }
}
