package com.brentmonitor.fetcher.impl;

import com.brentmonitor.fetcher.NewsFetcher;
import com.brentmonitor.model.NewsItem;
import com.brentmonitor.util.Logger;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class OilPriceNewsFetcher extends RssNewsFetcher {

    public OilPriceNewsFetcher() {
        super("OilPrice.com", "https://oilprice.com/rss/main");
    }
}

abstract class RssNewsFetcher implements NewsFetcher {

    private static final AtomicLong IDS = new AtomicLong();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final String source;
    private final URI feedUri;

    protected RssNewsFetcher(String source, String feedUrl) {
        this.source = source;
        this.feedUri = URI.create(feedUrl);
    }

    @Override
    public final String getSourceName() {
        return source;
    }

    @Override
    public final List<NewsItem> fetch() {
        HttpRequest request = HttpRequest.newBuilder(feedUri)
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .header("User-Agent", "BrentOilMonitor/1.0 (+personal-project)")
            .GET()
            .build();

        try {
            HttpResponse<InputStream> response = HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    Logger.error(source + " RSS 请求失败，HTTP " + response.statusCode());
                    return Collections.emptyList();
                }
                return parseFeed(body);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.error(source + " RSS 请求被中断", e);
        } catch (Exception e) {
            Logger.error(source + " RSS 抓取或解析失败", e);
        }
        return Collections.emptyList();
    }

    private List<NewsItem> parseFeed(InputStream body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        NodeList nodes = factory.newDocumentBuilder().parse(body).getElementsByTagName("item");
        List<NewsItem> items = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Element item = (Element) nodes.item(i);
            String title = childText(item, "title");
            String published = childText(item, "pubDate");
            if (title.isBlank() || published.isBlank()) {
                continue;
            }

            try {
                LocalDateTime timestamp = ZonedDateTime
                    .parse(published, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
                items.add(new NewsItem(
                    IDS.incrementAndGet(), timestamp, source,
                    buildContent(title, childText(item, "description")), 0.0));
            } catch (DateTimeParseException e) {
                Logger.error(source + " RSS 发布时间无法解析: " + published, e);
            }
        }
        return items;
    }

    private String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return child.getTextContent().strip();
            }
        }
        return "";
    }

    private String buildContent(String title, String description) {
        String summary = description.replaceAll("<[^>]+>", " ")
            .replaceAll("\\s+", " ").strip();
        if (summary.isEmpty()) {
            return title;
        }
        if (summary.length() > 500) {
            summary = summary.substring(0, 500).strip() + "…";
        }
        return title + " — " + summary;
    }
}
