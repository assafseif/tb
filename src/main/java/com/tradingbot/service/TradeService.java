package com.tradingbot.service;

import com.tradingbot.binance.BinanceFuturesApiClient;
import com.tradingbot.dto.ExecutedTradeDto;
import com.tradingbot.entity.enums.TradeStatus;
import com.tradingbot.mapper.ExecutedTradeMapper;
import com.tradingbot.repository.ExecutedTradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final ExecutedTradeRepository tradeRepository;
    private final ExecutedTradeMapper tradeMapper;
    private final BinanceFuturesApiClient binanceApiClient;

    public Flux<ExecutedTradeDto> getLatestTrades() {
        return Mono.fromCallable(() ->
                tradeMapper.toDtoList(tradeRepository.findTop50ByOrderByCreatedAtDesc()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Flux<ExecutedTradeDto> getOpenTrades() {
        return Mono.fromCallable(() ->
                tradeMapper.toDtoList(tradeRepository.findByStatusOrderByCreatedAtDesc(TradeStatus.OPEN)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<Long> countOpenTrades() {
        return Mono.fromCallable(tradeRepository::countOpenTrades)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<BigDecimal> getTodayPnl() {
        return binanceApiClient.getTodayRealizedPnl();
    }
}
