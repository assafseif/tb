package com.tradingbot.controller;

import com.tradingbot.dto.TradeSignalDto;
import com.tradingbot.service.SignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;

    @GetMapping
    public Flux<TradeSignalDto> getSignals() {
        return signalService.getLatestSignals();
    }

    @GetMapping("/pending")
    public Flux<TradeSignalDto> getPendingSignals() {
        return signalService.getPendingSignals();
    }
}
