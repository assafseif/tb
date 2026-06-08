package com.tradingbot.binance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinanceOrderRequest {
    private String symbol;
    private String side;       // BUY / SELL
    private String type;       // MARKET / LIMIT
    private String quantity;
    private String price;      // required for LIMIT
    private String timeInForce; // GTC, IOC (required for LIMIT)
    private String reduceOnly;
    private String positionSide; // BOTH, LONG, SHORT (for hedge mode)
}
