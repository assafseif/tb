package com.tradingbot.scheduler;

import com.tradingbot.service.SignalService;
import com.tradingbot.trading.SignalGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalGenerationScheduler {

    private final SignalGenerator signalGenerator;
    private final SignalService signalService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    // @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void generateSignals() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Signal generation already in progress, skipping");
            return;
        }

        log.debug("Starting signal generation cycle");

        // Expire stale signals first
        signalService.expireOldSignals()
                .then()
                .then(signalGenerator.generateSignals().count())
                .doFinally(signal -> running.set(false))
                .subscribe(
                        count -> {
                            if (count > 0) log.info("Generated {} trade signals", count);
                        },
                        error -> {
                            log.error("Signal generation scheduler error: {}", error.getMessage());
                            running.set(false);
                        }
                );
    }
}
