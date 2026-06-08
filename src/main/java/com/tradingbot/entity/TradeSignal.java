package com.tradingbot.entity;

import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.entity.enums.SignalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_signals", indexes = {
        @Index(name = "idx_signal_symbol", columnList = "symbol"),
        @Index(name = "idx_signal_status", columnList = "status"),
        @Index(name = "idx_signal_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TradeSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeSide side;

    @Column(nullable = false)
    private double score;

    @Column(name = "entry_price", precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @Column(name = "take_profit", precision = 20, scale = 8)
    private BigDecimal takeProfit;

    @Column(nullable = false)
    private int confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SignalStatus status = SignalStatus.PENDING;

    @Column(name = "sentiment_score")
    private double sentimentScore;

    @Column(name = "trend_score")
    private double trendScore;

    @Column(name = "volume_score")
    private double volumeScore;

    @Column(name = "rsi_score")
    private double rsiScore;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
