package com.tradingbot.repository;

import com.tradingbot.entity.TradeSignal;
import com.tradingbot.entity.enums.SignalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeSignalRepository extends JpaRepository<TradeSignal, Long> {

    List<TradeSignal> findByStatusOrderByCreatedAtDesc(SignalStatus status);

    List<TradeSignal> findTop50ByOrderByCreatedAtDesc();

    List<TradeSignal> findBySymbolAndStatusOrderByCreatedAtDesc(String symbol, SignalStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE TradeSignal s SET s.status = :status, s.processedAt = :processedAt WHERE s.id = :id")
    void updateStatus(@Param("id") Long id,
                      @Param("status") SignalStatus status,
                      @Param("processedAt") LocalDateTime processedAt);

    @Modifying
    @Transactional
    @Query("UPDATE TradeSignal s SET s.status = 'EXPIRED', s.processedAt = :now " +
            "WHERE s.status = 'PENDING' AND s.createdAt < :expiry")
    int expireOldSignals(@Param("now") LocalDateTime now, @Param("expiry") LocalDateTime expiry);

    long countByStatus(SignalStatus status);
}
