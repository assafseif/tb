package com.tradingbot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingbot.entity.enums.SignalStatus;
import com.tradingbot.entity.enums.TradeSide;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TradeSignalDto {
    private Long id;
    private String symbol;
    private TradeSide side;
    private double score;
    private BigDecimal entryPrice;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    private int confidence;
    private SignalStatus status;
    private double sentimentScore;
    private double trendScore;
    private double volumeScore;
    private double rsiScore;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;
}
