package com.tradingbot.controller;

import com.tradingbot.dto.ExecutedTradeDto;
import com.tradingbot.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @GetMapping
    public Flux<ExecutedTradeDto> getTrades() {
        return tradeService.getLatestTrades();
    }

    @GetMapping("/open")
    public Flux<ExecutedTradeDto> getOpenTrades() {
        return tradeService.getOpenTrades();
    }
}
