package com.tradingbot.news;

import com.tradingbot.entity.NewsEvent;
import com.tradingbot.repository.NewsEventRepository;
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
public class NewsAggregatorService {

    private final List<NewsCollector> collectors;
    private final NewsEventRepository newsEventRepository;

    public Mono<Integer> fetchAndStoreNews() {
        return Flux.fromIterable(collectors)
                .flatMap(collector -> collector.fetchLatestNews()
                        .onErrorResume(ex -> {
                            log.error("Collector {} failed: {}", collector.getName(), ex.getMessage());
                            return Flux.empty();
                        }))
                .distinct(NewsEvent::getExternalId)
                .filterWhen(this::isNewArticle)
                .collectList()
                .flatMap(this::saveAll)
                .doOnSuccess(count -> log.info("Saved {} new news articles", count))
                .onErrorResume(ex -> {
                    log.error("News aggregation failed: {}", ex.getMessage());
                    return Mono.just(0);
                });
    }

    private Mono<Boolean> isNewArticle(NewsEvent newsEvent) {
        return Mono.fromCallable(() ->
                newsEvent.getExternalId() != null
                        && !newsEventRepository.existsByExternalId(newsEvent.getExternalId()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Integer> saveAll(List<NewsEvent> newArticles) {
        if (newArticles.isEmpty()) {
            return Mono.just(0);
        }
        return Mono.fromCallable(() -> {
            newsEventRepository.saveAll(newArticles);
            return newArticles.size();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
