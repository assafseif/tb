package com.tradingbot.news;

import com.tradingbot.config.NewsProperties;
import com.tradingbot.entity.NewsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import reactor.core.publisher.Flux;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssNewsCollector implements NewsCollector {

    @Qualifier("newsWebClient")
    private final WebClient newsWebClient;
    private final NewsProperties newsProperties;

    // RFC 2822 date format used by RSS 2.0
    private static final DateTimeFormatter RSS_DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    @Override
    public String getName() {
        return "RSS";
    }

    @Override
    public Flux<NewsEvent> fetchLatestNews() {
        return Flux.fromIterable(newsProperties.getRssFeeds())
                .flatMap(url -> fetchFeed(url).onErrorResume(ex -> {
                    log.warn("RSS feed failed [{}]: {}", url, ex.getMessage());
                    return Flux.empty();
                }), 3);
    }

    private Flux<NewsEvent> fetchFeed(String url) {
        return newsWebClient.get()
                .uri(url)
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(xml -> {
                    List<NewsEvent> items = parseRss(xml, sourceName(url));
                    log.debug("RSS [{}]: {} articles", sourceName(url), items.size());
                    return Flux.fromIterable(items);
                })
                .onErrorResume(ex -> {
                    log.warn("RSS parse failed [{}]: {}", url, ex.getMessage());
                    return Flux.empty();
                });
    }

    private List<NewsEvent> parseRss(String xml, String source) {
        List<NewsEvent> results = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity processing for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null); // suppress noisy SAX warnings
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Node node = items.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) node;

                String title = text(el, "title");
                if (title == null || title.isBlank()) continue;

                String guid = text(el, "guid");
                String link = text(el, "link");
                String externalId = "rss-" + source + "-" + (guid != null ? guid : link);
                if (externalId.length() > 250) externalId = externalId.substring(0, 250);

                String description = text(el, "description");
                if (description != null) description = description.replaceAll("<[^>]+>", "").trim();

                LocalDateTime pubDate = parsePubDate(text(el, "pubDate"));

                results.add(NewsEvent.builder()
                        .externalId(externalId)
                        .title(title.trim())
                        .content(description != null ? description : "")
                        .source(source)
                        .publishedAt(pubDate)
                        .categories(text(el, "category"))
                        .processed(false)
                        .build());
            }
        } catch (Exception e) {
            log.warn("RSS XML parse error for {}: {}", source, e.getMessage());
        }
        return results;
    }

    private String text(Element el, String tag) {
        NodeList nodes = el.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        Node node = nodes.item(0);
        return node != null ? node.getTextContent() : null;
    }

    private LocalDateTime parsePubDate(String raw) {
        if (raw == null) return LocalDateTime.now();
        try {
            return ZonedDateTime.parse(raw.trim(), RSS_DATE_FMT).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    private String sourceName(String url) {
        if (url.contains("cointelegraph")) return "CoinTelegraph";
        if (url.contains("decrypt")) return "Decrypt";
        if (url.contains("coindesk")) return "CoinDesk";
        return url.replaceAll("https?://([^/]+).*", "$1");
    }
}
