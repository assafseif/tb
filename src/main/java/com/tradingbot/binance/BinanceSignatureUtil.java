package com.tradingbot.binance;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

@Slf4j
public final class BinanceSignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private BinanceSignatureUtil() {}

    public static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to generate Binance signature: {}", e.getMessage());
            throw new RuntimeException("Signature generation failed", e);
        }
    }

    public static String buildQueryString(BinanceOrderRequest req, long timestamp, long recvWindow) {
        StringBuilder sb = new StringBuilder();
        append(sb, "symbol", req.getSymbol());
        append(sb, "side", req.getSide());
        append(sb, "type", req.getType());
        append(sb, "quantity", req.getQuantity());

        if (req.getPrice() != null && !req.getPrice().isBlank()) {
            append(sb, "price", req.getPrice());
        }
        if (req.getTimeInForce() != null && !req.getTimeInForce().isBlank()) {
            append(sb, "timeInForce", req.getTimeInForce());
        }
        if (req.getStopPrice() != null && !req.getStopPrice().isBlank()) {
            append(sb, "stopPrice", req.getStopPrice());
        }
        if (req.getReduceOnly() != null && !req.getReduceOnly().isBlank()) {
            append(sb, "reduceOnly", req.getReduceOnly());
        }
        if (req.getClosePosition() != null && !req.getClosePosition().isBlank()) {
            append(sb, "closePosition", req.getClosePosition());
        }

        append(sb, "timestamp", String.valueOf(timestamp));
        append(sb, "recvWindow", String.valueOf(recvWindow));

        // Remove trailing &
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '&') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(key).append("=").append(value).append("&");
        }
    }
}
