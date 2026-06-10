package com.tradingbot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NewsEventDto {
    private Long id;
    private String title;
    private String source;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;
    private boolean processed;
    private String categories;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // sentiment analysis fields (null when not yet analyzed)
    private String symbol;
    private String sentiment;
    private Integer confidence;
    private Integer impact;
    private String urgency;
    private String aiReason;

    // null when analyzed or pending; set when article was skipped due to age
    private String rejectionReason;
}
