package com.tradingbot.scheduler;

import com.tradingbot.trading.TradingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeExecutionScheduler {

    private final TradingEngine tradingEngine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    // @Scheduled(fixedDelay = 60000, initialDelay = 45000)
    public void executeTrades() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Trade execution already in progress, skipping");
            return;
        }

        log.debug("Starting trade execution cycle");
        tradingEngine.processPendingSignals()
                .count()
                .doFinally(signal -> running.set(false))
                .subscribe(
                        count -> {
                            if (count > 0) log.info("Processed {} trade signals", count);
                        },
                        error -> {
                            log.error("Trade execution scheduler error: {}", error.getMessage());
                            running.set(false);
                        }
                );
    }
}
