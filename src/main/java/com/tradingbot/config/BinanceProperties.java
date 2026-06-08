package com.tradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "binance")
public class BinanceProperties {

    private String apiKey = "";
    private String apiSecret = "";
    private String testnetBaseUrl = "https://testnet.binancefuture.com";
    private String liveBaseUrl = "https://fapi.binance.com";
    private List<String> symbols = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT");
    private long recvWindow = 5000;
    private int klineLimit = 200;
}
