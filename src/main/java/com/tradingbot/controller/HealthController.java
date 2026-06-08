package com.tradingbot.controller;

import com.tradingbot.config.BinanceProperties;
import com.tradingbot.config.TradingProperties;
import com.tradingbot.repository.NewsEventRepository;
import com.tradingbot.repository.ExecutedTradeRepository;
import com.tradingbot.util.RedisAvailabilityTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final TradingProperties tradingProperties;
    private final BinanceProperties binanceProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisAvailabilityTracker redisTracker;
    private final NewsEventRepository newsEventRepository;
    private final ExecutedTradeRepository tradeRepository;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return Mono.fromCallable(this::buildHealthResponse)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    private Map<String, Object> buildHealthResponse() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());

        // Trading mode
        Map<String, Object> trading = new LinkedHashMap<>();
        trading.put("tradingEnabled", tradingProperties.isTradingActive());
        trading.put("paperTradingMode", tradingProperties.isPaperTradingActive());
        trading.put("trackedSymbols", binanceProperties.getSymbols());
        health.put("trading", trading);

        // Database status
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            long newsCount = newsEventRepository.count();
            long tradeCount = tradeRepository.count();
            db.put("status", "UP");
            db.put("totalNews", newsCount);
            db.put("totalTrades", tradeCount);
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("error", e.getMessage());
        }
        health.put("database", db);

        // Redis status
        Map<String, Object> redis = new LinkedHashMap<>();
        try {
            redisTemplate.opsForValue().get("health:ping");
            redisTracker.markAvailable();
            redis.put("status", "UP");
        } catch (Exception e) {
            redisTracker.markUnavailable("health-check");
            redis.put("status", "DOWN");
            redis.put("error", "Unable to connect to Redis");
        }
        health.put("redis", redis);

        return health;
    }
}
