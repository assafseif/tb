package com.tradingbot.service;

import com.tradingbot.binance.BinanceFuturesApiClient;
import com.tradingbot.config.AccountProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountBalanceSyncService implements ApplicationRunner {

    private final BinanceFuturesApiClient binanceApiClient;
    private final AccountProperties accountProperties;

    @Override
    public void run(ApplicationArguments args) {
        Double balance = binanceApiClient.getAvailableBalance().block();
        if (balance == null || balance <= 0) {
            throw new IllegalStateException("Failed to fetch account balance from Binance at startup — check API key and connectivity");
        }
        accountProperties.setBalance(balance);
        log.info("Account balance loaded from Binance: ${}", String.format("%.2f", balance));
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void refreshBalance() {
        binanceApiClient.getAvailableBalance()
                .subscribe(
                        balance -> {
                            accountProperties.setBalance(balance);
                            log.info("Account balance refreshed: ${}", String.format("%.2f", balance));
                        },
                        ex -> log.warn("Balance refresh failed, keeping last known value ${}: {}",
                                String.format("%.2f", accountProperties.getBalance()), ex.getMessage())
                );
    }
}
