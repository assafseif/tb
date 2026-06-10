package com.tradingbot.repository;

import com.tradingbot.entity.SentimentAnalysis;
import com.tradingbot.entity.enums.SentimentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SentimentAnalysisRepository extends JpaRepository<SentimentAnalysis, Long> {

    List<SentimentAnalysis> findTop20BySymbolOrderByCreatedAtDesc(String symbol);

    List<SentimentAnalysis> findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(
            String symbol, LocalDateTime after);

    boolean existsByNewsId(Long newsId);

    @Query("SELECT AVG(s.confidence) FROM SentimentAnalysis s WHERE s.symbol = :symbol AND s.createdAt > :after")
    Double avgConfidenceBySymbolAfter(@Param("symbol") String symbol, @Param("after") LocalDateTime after);

    @Query("SELECT s.sentiment, COUNT(s) FROM SentimentAnalysis s " +
            "WHERE s.symbol = :symbol AND s.createdAt > :after " +
            "GROUP BY s.sentiment")
    List<Object[]> countBySentimentAndSymbolAfter(@Param("symbol") String symbol,
                                                   @Param("after") LocalDateTime after);

    List<SentimentAnalysis> findTop50ByOrderByCreatedAtDesc();

    List<SentimentAnalysis> findByNewsIdIn(java.util.Collection<Long> newsIds);
}
