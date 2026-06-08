package com.tradingbot.service;

import com.tradingbot.dto.NewsEventDto;
import com.tradingbot.mapper.NewsMapper;
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
public class NewsService {

    private final NewsEventRepository newsEventRepository;
    private final NewsMapper newsMapper;

    public Flux<NewsEventDto> getLatestNews() {
        return Mono.fromCallable(() ->
                newsMapper.toDtoList(newsEventRepository.findTop50ByOrderByPublishedAtDesc()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<Long> countUnprocessed() {
        return Mono.fromCallable(newsEventRepository::countUnprocessed)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
