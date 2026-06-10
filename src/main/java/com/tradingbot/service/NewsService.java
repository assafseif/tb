package com.tradingbot.service;

import com.tradingbot.config.NewsProperties;
import com.tradingbot.dto.NewsEventDto;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsEventRepository newsEventRepository;
    private final SentimentAnalysisRepository sentimentRepository;
    private final NewsProperties newsProperties;

    public Flux<NewsEventDto> getLatestNews() {
        return Mono.fromCallable(this::buildEnrichedList)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    private List<NewsEventDto> buildEnrichedList() {
        List<NewsEvent> events = newsEventRepository.findTop50ByOrderByPublishedAtDesc();
        List<Long> ids = events.stream().map(NewsEvent::getId).toList();

        Map<Long, SentimentAnalysis> sentimentByNewsId = sentimentRepository.findByNewsIdIn(ids)
                .stream()
                .collect(Collectors.toMap(SentimentAnalysis::getNewsId, s -> s, (a, b) -> a));

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(newsProperties.getMaxAgeMinutes());

        return events.stream().map(e -> enrich(e, sentimentByNewsId.get(e.getId()), cutoff)).toList();
    }

    private NewsEventDto enrich(NewsEvent e, SentimentAnalysis sa, LocalDateTime cutoff) {
        NewsEventDto.NewsEventDtoBuilder b = NewsEventDto.builder()
                .id(e.getId())
                .title(e.getTitle())
                .source(e.getSource())
                .publishedAt(e.getPublishedAt())
                .processed(e.isProcessed())
                .categories(e.getCategories())
                .createdAt(e.getCreatedAt());

        if (sa != null) {
            b.symbol(sa.getSymbol())
             .sentiment(sa.getSentiment().name())
             .confidence(sa.getConfidence())
             .impact(sa.getImpact())
             .urgency(sa.getUrgency().name())
             .aiReason(sa.getReason());
        } else if (e.getPublishedAt() != null && !e.getPublishedAt().isAfter(cutoff)) {
            long minutesOld = ChronoUnit.MINUTES.between(e.getPublishedAt(), LocalDateTime.now());
            b.rejectionReason("Too old — " + minutesOld + "m ago (limit: " + newsProperties.getMaxAgeMinutes() + "m)");
        }
        // else: pending — within window but not yet analyzed this cycle

        return b.build();
    }

    public Mono<Long> countUnprocessed() {
        return Mono.fromCallable(newsEventRepository::countUnprocessed)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
