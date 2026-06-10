package com.tradingbot.service;

import com.tradingbot.ai.AiAnalysisService;
import com.tradingbot.ai.SentimentResult;
import com.tradingbot.config.NewsProperties;
import com.tradingbot.entity.NewsEvent;
import com.tradingbot.entity.SentimentAnalysis;
import com.tradingbot.entity.enums.NewsUrgency;
import com.tradingbot.repository.NewsEventRepository;
import com.tradingbot.repository.SentimentAnalysisRepository;
import com.tradingbot.trading.BreakingNewsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentService {

    private final AiAnalysisService aiAnalysisService;
    private final NewsEventRepository newsEventRepository;
    private final SentimentAnalysisRepository sentimentRepository;
    private final NewsProperties newsProperties;
    private final BreakingNewsProcessor breakingNewsProcessor;

    public Mono<Integer> analyzeUnprocessedNews() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(newsProperties.getMaxAgeMinutes());

        return Mono.fromCallable(() ->
                newsEventRepository.findByProcessedFalseOrderByPublishedAtDesc()
                        .stream()
                        .filter(n -> n.getPublishedAt() == null || n.getPublishedAt().isAfter(cutoff))
                        .filter(n -> !sentimentRepository.existsByNewsId(n.getId()))
                        .limit(newsProperties.getMaxPerCycle())
                        .toList())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::analyzeNewsItem, 3)
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> {
                    if (count > 0) log.info("Analyzed {} news items (max {}/cycle, cutoff: last {}min)",
                            count, newsProperties.getMaxPerCycle(), newsProperties.getMaxAgeMinutes());
                });
    }

    private Mono<SentimentAnalysis> analyzeNewsItem(NewsEvent news) {
        return aiAnalysisService.analyzeSentiment(news)
                .flatMap(result -> Mono.fromCallable(() -> {
                    SentimentAnalysis analysis = buildAnalysis(news, result);
                    SentimentAnalysis saved = sentimentRepository.save(analysis);
                    newsEventRepository.markAsProcessed(news.getId());
                    log.info("Sentiment for news id={}: {} ({}) confidence={} urgency={}",
                            news.getId(), result.getSentimentRaw(), result.getSymbol(),
                            result.getConfidence(), result.getUrgencyType());

                    if (result.getUrgencyType() == NewsUrgency.IMMEDIATE
                            && !"NEUTRAL".equalsIgnoreCase(result.getSentimentRaw())
                            && result.getConfidence() >= 70) {
                        breakingNewsProcessor.processImmediately(result.getSymbol(), NewsUrgency.IMMEDIATE);
                    }
                    return saved;
                }).subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(ex -> {
                    log.error("Analysis failed for news id={}: {}", news.getId(), ex.getMessage());
                    return Mono.empty();
                });
    }

    private SentimentAnalysis buildAnalysis(NewsEvent news, SentimentResult result) {
        return SentimentAnalysis.builder()
                .newsId(news.getId())
                .symbol(result.getSymbol() != null ? result.getSymbol() : "BTCUSDT")
                .sentiment(result.getSentimentType())
                .confidence(Math.max(0, Math.min(100, result.getConfidence())))
                .impact(Math.max(0, Math.min(100, result.getImpact())))
                .urgency(result.getUrgencyType())
                .reason(result.getReason())
                .build();
    }
}
