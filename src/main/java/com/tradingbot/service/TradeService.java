package com.tradingbot.service;

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
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final ExecutedTradeRepository tradeRepository;
    private final ExecutedTradeMapper tradeMapper;

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
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        return Mono.fromCallable(() -> {
            BigDecimal pnl = tradeRepository.sumRealizedPnlAfter(startOfDay);
            return pnl != null ? pnl : BigDecimal.ZERO;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
