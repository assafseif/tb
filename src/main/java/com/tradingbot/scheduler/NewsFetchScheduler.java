package com.tradingbot.scheduler;

import com.tradingbot.news.NewsAggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsFetchScheduler {

    private final NewsAggregatorService newsAggregatorService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void fetchNews() {
        if (!running.compareAndSet(false, true)) {
            log.debug("News fetch already in progress, skipping");
            return;
        }

        log.debug("Starting news fetch cycle");
        newsAggregatorService.fetchAndStoreNews()
                .doFinally(signal -> running.set(false))
                .subscribe(
                        count -> log.info("News fetch completed: {} new articles", count),
                        error -> {
                            log.error("News fetch scheduler error: {}", error.getMessage());
                            running.set(false);
                        }
                );
    }
}
