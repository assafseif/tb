package com.tradingbot.repository;

import com.tradingbot.entity.NewsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsEventRepository extends JpaRepository<NewsEvent, Long> {

    List<NewsEvent> findByProcessedFalseOrderByPublishedAtDesc();

    List<NewsEvent> findTop50ByOrderByPublishedAtDesc();

    Optional<NewsEvent> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    List<NewsEvent> findByPublishedAtAfterOrderByPublishedAtDesc(LocalDateTime after);

    @Modifying
    @Transactional
    @Query("UPDATE NewsEvent n SET n.processed = true WHERE n.id = :id")
    void markAsProcessed(@Param("id") Long id);

    @Query("SELECT COUNT(n) FROM NewsEvent n WHERE n.processed = false")
    long countUnprocessed();

    @Modifying
    @Transactional
    @Query("DELETE FROM NewsEvent n WHERE n.publishedAt < :cutoff AND n.processed = true")
    int deleteOldProcessedNews(@Param("cutoff") LocalDateTime cutoff);
}
