package com.tradingbot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.entity.enums.TradeStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExecutedTradeDto {
    private Long id;
    private String symbol;
    private TradeSide side;
    private BigDecimal quantity;
    private BigDecimal entryPrice;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    private TradeStatus status;
    private Long binanceOrderId;
    private Long signalId;
    private boolean paperTrade;
    private BigDecimal closePrice;
    private BigDecimal realizedPnl;
    private String errorMessage;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
