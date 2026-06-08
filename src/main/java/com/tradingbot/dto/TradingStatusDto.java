package com.tradingbot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TradingStatusDto {
    private boolean tradingEnabled;
    private boolean paperTradingMode;
    private String message;
}
