package com.tradingbot.service;

import com.tradingbot.ai.AiAnalysisService;
import com.tradingbot.ai.SentimentResult;
import com.tradingbot.entity.NewsEvent;
import com.tradingbot.entity.SentimentAnalysis;
import com.tradingbot.repository.NewsEventRepository;
import com.tradingbot.repository.SentimentAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentService {

    private final AiAnalysisService aiAnalysisService;
    private final NewsEventRepository newsEventRepository;
    private final SentimentAnalysisRepository sentimentRepository;

    public Mono<Integer> analyzeUnprocessedNews() {
        return Mono.fromCallable(() ->
                newsEventRepository.findByProcessedFalseOrderByPublishedAtDesc())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .filter(news -> !sentimentRepository.existsByNewsId(news.getId()))
                .flatMap(this::analyzeNewsItem, 3) // max 3 concurrent AI calls
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> log.info("Analyzed {} news items", count));
    }

    private Mono<SentimentAnalysis> analyzeNewsItem(NewsEvent news) {
        return aiAnalysisService.analyzeSentiment(news)
                .flatMap(result -> Mono.fromCallable(() -> {
                    SentimentAnalysis analysis = buildAnalysis(news, result);
                    SentimentAnalysis saved = sentimentRepository.save(analysis);
                    newsEventRepository.markAsProcessed(news.getId());
                    log.info("Sentiment for news id={}: {} ({}) confidence={}",
                            news.getId(), result.getSentimentRaw(), result.getSymbol(), result.getConfidence());
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
                .reason(result.getReason())
                .build();
    }
}
