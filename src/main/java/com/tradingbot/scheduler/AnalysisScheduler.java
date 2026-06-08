package com.tradingbot.scheduler;

import com.tradingbot.service.SentimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    private final SentimentService sentimentService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    // @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void analyzeNews() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Analysis already in progress, skipping");
            return;
        }

        log.debug("Starting sentiment analysis cycle");
        sentimentService.analyzeUnprocessedNews()
                .doFinally(signal -> running.set(false))
                .subscribe(
                        count -> {
                            if (count > 0) log.info("Sentiment analysis completed: {} items", count);
                        },
                        error -> {
                            log.error("Analysis scheduler error: {}", error.getMessage());
                            running.set(false);
                        }
                );
    }
}
