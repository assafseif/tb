package com.tradingbot.entity;

import com.tradingbot.entity.enums.SentimentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sentiment_analyses", indexes = {
        @Index(name = "idx_sentiment_news", columnList = "news_id"),
        @Index(name = "idx_sentiment_symbol", columnList = "symbol"),
        @Index(name = "idx_sentiment_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SentimentAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SentimentType sentiment = SentimentType.NEUTRAL;

    @Column(nullable = false)
    private int confidence;

    @Column(nullable = false)
    private int impact;

    @Column(length = 1000)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
