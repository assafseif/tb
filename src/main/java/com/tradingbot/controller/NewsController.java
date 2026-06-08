package com.tradingbot.controller;

import com.tradingbot.dto.NewsEventDto;
import com.tradingbot.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public Flux<NewsEventDto> getNews() {
        return newsService.getLatestNews();
    }

    @GetMapping("/count/unprocessed")
    public Mono<ResponseEntity<Long>> countUnprocessed() {
        return newsService.countUnprocessed()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(0L));
    }
}
