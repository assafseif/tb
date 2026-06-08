package com.tradingbot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks Redis availability and rate-limits log warnings so the console
 * is not flooded when Redis is temporarily unavailable.
 */
@Slf4j
@Component
public class RedisAvailabilityTracker {

    private final AtomicBoolean available = new AtomicBoolean(true);
    // Only emit a WARN log at most once per 60 seconds when Redis is down
    private final AtomicLong lastWarnEpoch = new AtomicLong(0);
    private static final long WARN_INTERVAL_SECONDS = 60;

    public boolean isAvailable() {
        return available.get();
    }

    public void markUnavailable(String context) {
        available.set(false);
        long now = Instant.now().getEpochSecond();
        long last = lastWarnEpoch.get();
        if (now - last >= WARN_INTERVAL_SECONDS && lastWarnEpoch.compareAndSet(last, now)) {
            log.warn("Redis unavailable ({}). Cache disabled — app continues without caching. " +
                    "Start Redis to enable caching. Further warnings suppressed for {}s.",
                    context, WARN_INTERVAL_SECONDS);
        }
    }

    public void markAvailable() {
        if (!available.getAndSet(true)) {
            log.info("Redis connection restored — caching re-enabled.");
        }
        lastWarnEpoch.set(0);
    }
}
