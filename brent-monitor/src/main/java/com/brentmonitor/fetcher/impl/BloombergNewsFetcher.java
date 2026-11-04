package com.brentmonitor.fetcher.impl;

public final class BloombergNewsFetcher extends RssNewsFetcher {

    public BloombergNewsFetcher() {
        super("Bloomberg", "https://feeds.bloomberg.com/markets/news.rss");
    }
}
