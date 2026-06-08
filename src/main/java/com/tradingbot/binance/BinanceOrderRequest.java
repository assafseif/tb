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
    private String stopPrice;     // required for STOP_MARKET / TAKE_PROFIT_MARKET
    private String reduceOnly;
    private String closePosition; // "true" closes entire position (use instead of qty+reduceOnly for SL/TP)
    private String positionSide;  // BOTH, LONG, SHORT (for hedge mode)
}
