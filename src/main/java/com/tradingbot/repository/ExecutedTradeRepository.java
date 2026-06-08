package com.tradingbot.repository;

import com.tradingbot.entity.ExecutedTrade;
import com.tradingbot.entity.enums.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExecutedTradeRepository extends JpaRepository<ExecutedTrade, Long> {

    List<ExecutedTrade> findTop50ByOrderByCreatedAtDesc();

    List<ExecutedTrade> findByStatusOrderByCreatedAtDesc(TradeStatus status);

    List<ExecutedTrade> findBySymbolAndStatusOrderByCreatedAtDesc(String symbol, TradeStatus status);

    long countByStatus(TradeStatus status);

    long countByStatusIn(List<TradeStatus> statuses);

    @Query("SELECT SUM(t.realizedPnl) FROM ExecutedTrade t WHERE t.createdAt > :after AND t.realizedPnl IS NOT NULL")
    BigDecimal sumRealizedPnlAfter(@Param("after") LocalDateTime after);

    @Query("SELECT COUNT(t) FROM ExecutedTrade t WHERE t.status = 'OPEN'")
    long countOpenTrades();

    @Query("SELECT t FROM ExecutedTrade t WHERE t.status = 'OPEN' ORDER BY t.createdAt DESC")
    List<ExecutedTrade> findOpenTrades();
}
