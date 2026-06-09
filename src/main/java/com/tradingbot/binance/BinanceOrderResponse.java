package com.tradingbot.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceOrderResponse {

    @JsonProperty("orderId")
    private Long orderId;

    // Populated for algo orders (/fapi/v1/algoOrder)
    @JsonProperty("algoId")
    private Long algoId;

    @JsonProperty("algoStatus")
    private String algoStatus;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("status")
    private String status;

    @JsonProperty("side")
    private String side;

    @JsonProperty("type")
    private String type;

    @JsonProperty("origQty")
    private BigDecimal origQty;

    @JsonProperty("executedQty")
    private BigDecimal executedQty;

    @JsonProperty("avgPrice")
    private BigDecimal avgPrice;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("clientOrderId")
    private String clientOrderId;

    @JsonProperty("transactTime")
    private Long transactTime;
}
