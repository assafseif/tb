package com.tradingbot.service;

import com.tradingbot.config.AccountProperties;
import com.tradingbot.config.RiskProperties;
import com.tradingbot.config.TradingProperties;
import com.tradingbot.dto.DashboardDto;
import com.tradingbot.dto.DashboardDto.*;
import com.tradingbot.entity.enums.TradeStatus;
import com.tradingbot.repository.ExecutedTradeRepository;
import com.tradingbot.repository.NewsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TradingProperties tradingProperties;
    private final RiskProperties riskProperties;
    private final AccountProperties accountProperties;
    private final NewsService newsService;
    private final SignalService signalService;
    private final TradeService tradeService;
    private final NewsEventRepository newsEventRepository;
    private final ExecutedTradeRepository tradeRepository;

    public Mono<DashboardDto> getDashboard() {
        return Mono.zip(
                        buildSystemStatus(),
                        buildTradingStats(),
                        newsService.getLatestNews().take(5).collectList(),
                        signalService.getLatestSignals().take(5).collectList(),
                        tradeService.getLatestTrades().take(5).collectList()
                )
                .map(tuple -> DashboardDto.builder()
                        .systemStatus(tuple.getT1())
                        .tradingStats(tuple.getT2())
                        .recentSignals(tuple.getT4())
                        .recentTrades(tuple.getT5())
                        .build());
    }

    private Mono<SystemStatus> buildSystemStatus() {
        return Mono.zip(
                        Mono.fromCallable(newsEventRepository::countUnprocessed).subscribeOn(Schedulers.boundedElastic()),
                        signalService.countPending(),
                        tradeService.countOpenTrades()
                )
                .map(tuple -> SystemStatus.builder()
                        .tradingEnabled(tradingProperties.isTradingActive())
                        .paperTradingMode(tradingProperties.isPaperTradingActive())
                        .unprocessedNews(tuple.getT1())
                        .pendingSignals(tuple.getT2())
                        .openTrades(tuple.getT3())
                        .build());
    }

    private Mono<TradingStats> buildTradingStats() {
        return Mono.zip(
                        Mono.fromCallable(() -> tradeRepository.count()).subscribeOn(Schedulers.boundedElastic()),
                        tradeService.countOpenTrades(),
                        Mono.fromCallable(() -> tradeRepository.countByStatus(TradeStatus.CLOSED)).subscribeOn(Schedulers.boundedElastic()),
                        tradeService.getTodayPnl()
                )
                .map(tuple -> {
                    double riskPercent = riskProperties.getMaxRiskPerTrade();
                    double riskAmountUsd = accountProperties.getBalance() * riskPercent;
                    return TradingStats.builder()
                            .totalTrades(tuple.getT1())
                            .openTrades(tuple.getT2())
                            .closedTrades(tuple.getT3())
                            .todayPnl(tuple.getT4())
                            .totalPnl(BigDecimal.ZERO)
                            .winRate(0.0)
                            .riskPercent(riskPercent)
                            .riskAmountUsd(riskAmountUsd)
                            .build();
                });
    }
}
