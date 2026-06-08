package com.tradingbot.market;

import com.tradingbot.config.BinanceProperties;
import com.tradingbot.util.RedisAvailabilityTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final BinanceFuturesClient binanceFuturesClient;
    private final BinanceProperties binanceProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisAvailabilityTracker redisTracker;

    private static final String SNAPSHOT_KEY = "market:snapshot:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    public Mono<MarketSnapshot> getSnapshot(String symbol) {
        // Only attempt Redis if it was reachable last time we checked
        if (redisTracker.isAvailable()) {
            String cacheKey = SNAPSHOT_KEY + symbol;
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached instanceof MarketSnapshot snapshot) {
                    log.debug("Cache hit for market snapshot: {}", symbol);
                    return Mono.just(snapshot);
                }
                redisTracker.markAvailable();
            } catch (Exception e) {
                redisTracker.markUnavailable("read:" + symbol);
            }
        }

        return binanceFuturesClient.getMarketSnapshot(symbol)
                .doOnNext(snapshot -> {
                    if (redisTracker.isAvailable()) {
                        try {
                            redisTemplate.opsForValue().set(SNAPSHOT_KEY + symbol, snapshot, CACHE_TTL);
                            redisTracker.markAvailable();
                        } catch (Exception e) {
                            redisTracker.markUnavailable("write:" + symbol);
                        }
                    }
                });
    }

    public Flux<MarketSnapshot> getAllSnapshots() {
        List<String> symbols = binanceProperties.getSymbols();
        return Flux.fromIterable(symbols)
                .flatMap(this::getSnapshot, 3)
                .onErrorContinue((ex, obj) ->
                        log.error("Snapshot fetch failed for {}: {}", obj, ex.getMessage()));
    }
}
