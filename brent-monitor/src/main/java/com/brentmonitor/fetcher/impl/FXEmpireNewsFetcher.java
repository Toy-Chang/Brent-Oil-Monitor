package com.brentmonitor.fetcher.impl;

public final class FXEmpireNewsFetcher extends RssNewsFetcher {

    public FXEmpireNewsFetcher() {
        super("FX Empire", "https://www.fxempire.com/api/v1/en/articles/rss/news");
    }
}
