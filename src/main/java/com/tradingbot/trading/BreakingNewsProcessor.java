package com.tradingbot.trading;

import com.tradingbot.entity.enums.NewsUrgency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BreakingNewsProcessor {

    private final SignalGenerator signalGenerator;
    private final TradingEngine tradingEngine;

    public void processImmediately(String symbol, NewsUrgency urgency) {
        log.info("[BREAKING] {} urgency triggered for {} — bypassing scheduler", urgency, symbol);
        signalGenerator.generateSignalForSymbol(symbol, urgency)
                .flatMap(signal -> tradingEngine.processPendingSignals().next())
                .subscribe(
                        trade -> log.info("[BREAKING] Immediate trade executed: {} {}", trade.getSymbol(), trade.getSide()),
                        error -> log.error("[BREAKING] Immediate execution failed for {}: {}", symbol, error.getMessage())
                );
    }
}
