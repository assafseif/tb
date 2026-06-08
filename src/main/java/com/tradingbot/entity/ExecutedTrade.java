package com.tradingbot.entity;

import com.tradingbot.entity.enums.TradeSide;
import com.tradingbot.entity.enums.TradeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "executed_trades", indexes = {
        @Index(name = "idx_trade_symbol", columnList = "symbol"),
        @Index(name = "idx_trade_status", columnList = "status"),
        @Index(name = "idx_trade_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExecutedTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeSide side;

    @Column(precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "entry_price", precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @Column(name = "take_profit", precision = 20, scale = 8)
    private BigDecimal takeProfit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TradeStatus status = TradeStatus.PENDING;

    @Column(name = "binance_order_id")
    private Long binanceOrderId;

    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "paper_trade")
    @Builder.Default
    private boolean paperTrade = true;

    @Column(name = "close_price", precision = 20, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "realized_pnl", precision = 20, scale = 8)
    private BigDecimal realizedPnl;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
