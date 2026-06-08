package com.tradingbot.news;

import com.tradingbot.entity.NewsEvent;
import reactor.core.publisher.Flux;

public interface NewsCollector {

    String getName();

    Flux<NewsEvent> fetchLatestNews();
}
