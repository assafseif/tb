package com.tradingbot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingbot.entity.enums.SentimentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SentimentDto {
    private Long id;
    private Long newsId;
    private String symbol;
    private SentimentType sentiment;
    private int confidence;
    private int impact;
    private String reason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
