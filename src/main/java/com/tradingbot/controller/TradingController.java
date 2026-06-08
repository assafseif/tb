package com.tradingbot.controller;

import com.tradingbot.config.TradingProperties;
import com.tradingbot.dto.TradingStatusDto;
import com.tradingbot.risk.RiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingProperties tradingProperties;
    private final RiskManager riskManager;

    @PostMapping("/enable")
    public Mono<ResponseEntity<TradingStatusDto>> enableTrading(
            @RequestParam(defaultValue = "false") boolean live) {

        if (live) {
            log.warn("LIVE trading enable requested — activating real order execution");
            tradingProperties.enableTrading();
            tradingProperties.getPaperTradingEnabledRuntime().set(false);
        } else {
            log.info("Paper trading enabled");
            tradingProperties.enableTrading();
            tradingProperties.getPaperTradingEnabledRuntime().set(true);
        }

        TradingStatusDto status = TradingStatusDto.builder()
                .tradingEnabled(true)
                .paperTradingMode(!live)
                .message(live ? "LIVE trading enabled — real orders will be placed!"
                        : "Paper trading enabled — simulated orders only")
                .build();

        return Mono.just(ResponseEntity.ok(status));
    }

    @PostMapping("/disable")
    public Mono<ResponseEntity<TradingStatusDto>> disableTrading() {
        tradingProperties.disableTrading();
        log.info("Trading disabled");

        TradingStatusDto status = TradingStatusDto.builder()
                .tradingEnabled(false)
                .paperTradingMode(true)
                .message("Trading disabled — no orders will be placed")
                .build();

        return Mono.just(ResponseEntity.ok(status));
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<TradingStatusDto>> getStatus() {
        TradingStatusDto status = TradingStatusDto.builder()
                .tradingEnabled(tradingProperties.isTradingActive())
                .paperTradingMode(tradingProperties.isPaperTradingActive())
                .message("Current trading status")
                .build();

        return Mono.just(ResponseEntity.ok(status));
    }
}
