package com.tradingbot.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSnapshot {
    private String symbol;
    private BigDecimal currentPrice;
    private List<CandleData> candles1m;
    private List<CandleData> candles5m;
    private List<CandleData> candles15m;
    private List<CandleData> candles1h;
    private LocalDateTime fetchedAt;
}
