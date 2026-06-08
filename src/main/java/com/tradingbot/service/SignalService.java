package com.tradingbot.service;

import com.tradingbot.dto.TradeSignalDto;
import com.tradingbot.entity.enums.SignalStatus;
import com.tradingbot.mapper.TradeSignalMapper;
import com.tradingbot.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalService {

    private final TradeSignalRepository signalRepository;
    private final TradeSignalMapper signalMapper;

    public Flux<TradeSignalDto> getLatestSignals() {
        return Mono.fromCallable(() ->
                signalMapper.toDtoList(signalRepository.findTop50ByOrderByCreatedAtDesc()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Flux<TradeSignalDto> getPendingSignals() {
        return Mono.fromCallable(() ->
                signalMapper.toDtoList(signalRepository.findByStatusOrderByCreatedAtDesc(SignalStatus.PENDING)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<Integer> expireOldSignals() {
        LocalDateTime expiry = LocalDateTime.now().minusMinutes(10);
        return Mono.fromCallable(() ->
                signalRepository.expireOldSignals(LocalDateTime.now(), expiry))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(count -> {
                    if (count > 0) log.info("Expired {} stale signals", count);
                });
    }

    public Mono<Long> countPending() {
        return Mono.fromCallable(() -> signalRepository.countByStatus(SignalStatus.PENDING))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
