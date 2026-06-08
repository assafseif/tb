package com.tradingbot.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingbot.entity.NewsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoCompareNewsCollector implements NewsCollector {

    @Qualifier("newsWebClient")
    private final WebClient newsWebClient;

    // v1 endpoint returns a direct JSON array — no auth required on the free tier
    @Value("${news.cryptocompare-url}")
    private String baseUrl;

    @Value("${news.cryptocompare-api-key:}")
    private String apiKey;

    @Value("${news.categories}")
    private String categories;

    @Override
    public String getName() {
        return "CryptoCompare";
    }

    @Override
    public Flux<NewsEvent> fetchLatestNews() {
        WebClient.RequestHeadersSpec<?> req = newsWebClient.get()
                .uri(baseUrl + "?lang=EN&categories=" + categories + "&sortOrder=latest");

        if (apiKey != null && !apiKey.isBlank()) {
            req = ((WebClient.RequestHeadersUriSpec<?>) req)
                    .header("authorization", "Apikey " + apiKey);
        }

        return req.retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(root -> {
                    List<NewsEvent> articles = new ArrayList<>();

                    // v1: root is a direct array
                    // v2 with key: root is {"Data": [...]}
                    JsonNode dataNode = root.isArray() ? root : root.path("Data");

                    if (dataNode.isMissingNode()) {
                        String msg = root.path("Message").asText(root.path("message").asText("unknown"));
                        log.warn("CryptoCompare unexpected response — message: {}", msg);
                        return Flux.empty();
                    }
                    if (!dataNode.isArray()) {
                        String msg = root.path("Message").asText(root.path("message").asText("unknown"));
                        log.warn("CryptoCompare 'Data' is not an array — message: {}", msg);
                        return Flux.empty();
                    }

                    for (JsonNode item : dataNode) {
                        NewsEvent event = parseArticle(item);
                        if (event != null) articles.add(event);
                    }
                    log.debug("CryptoCompare fetched {} articles", articles.size());
                    return Flux.fromIterable(articles);
                })
                .onErrorResume(ex -> {
                    log.error("CryptoCompare fetch failed: {}", ex.getMessage());
                    return Flux.empty();
                });
    }

    private NewsEvent parseArticle(JsonNode item) {
        try {
            String id = item.path("id").asText(null);
            String title = item.path("title").asText(null);
            if (title == null || title.isBlank()) return null;

            long publishedOn = item.path("published_on").asLong(0);
            LocalDateTime publishedAt = publishedOn > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(publishedOn), ZoneId.systemDefault())
                    : LocalDateTime.now();

            String sourceName = item.path("source_info").path("name").asText(
                    item.path("source").asText("CryptoCompare"));

            return NewsEvent.builder()
                    .externalId("cryptocompare-" + id)
                    .title(title)
                    .content(item.path("body").asText(""))
                    .source(sourceName)
                    .publishedAt(publishedAt)
                    .categories(item.path("categories").asText(""))
                    .processed(false)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse CryptoCompare article: {}", e.getMessage());
            return null;
        }
    }
}
