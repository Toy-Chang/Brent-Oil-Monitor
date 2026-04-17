package com.brentmonitor.fetcher.impl;

import com.brentmonitor.fetcher.NewsFetcher;
import com.brentmonitor.model.NewsItem;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock 新闻源：Bloomberg（彭博社）
 *
 * 与 ReutersNewsFetcher 的区别：
 * - 不同数据模板（彭博社新闻风格）
 * - 不同油价区间（彭博更乐观，预测油价略高）
 * - 15% 空返回（比 Reuters 更频繁）
 * - 延迟区间不同（200~600ms）
 */
public class BloombergNewsFetcher implements NewsFetcher {

    private static final String SOURCE = "Bloomberg";

    // 布伦特原油相关新闻（中英双语）
    private static final String[] BRENT_TEMPLATES = {
        "Brent benchmarks at $%.2f; strategic reserves talks stall — 战略储备讨论停滞，布伦特报价 $%.2f",
        "Brent Oil surges %+.1f%% to $%.2f on supply disruption — 供应中断忧虑，布伦特大涨 %+.1f%% 至 $%.2f",
        "Brent Crude at $%.2f amid tight supply — 供应紧张，布伦特原油期货报 $%.2f",
        "Brent advances to $%.2f as OPEC+ policy under review — 欧佩克+政策审查中，布伦特升至 $%.2f",
        "Brent settles %+.1f%% higher at $%.2f/barrel — 欧佩克+会议前夕，布伦特上涨 %+.1f%% 至 $%.2f",
        "Middle East supply risks push Brent above $%.2f — 中东供应风险，布伦特突破 $%.2f 关口",
        "Brent futures $%.2f as China imports hit quarterly high — 中国进口创新高，布伦特报 $%.2f",
        "Analysts raise Brent 12-month target to $%.2f — 机构上调布伦特12个月目标价至 $%.2f",
        "Brent fluctuates near $%.2f ahead of IEA report — IEA报告前，布伦特在 $%.2f 附近波动",
        "Brent at $%.2f; implied volatility at %.0f%% — 布伦特报 $%.2f，隐含波动率 %.0f%%",
        "Brent $%.2f/barrel; WTI spreads widen on pipeline outage — 管道故障，WTI价差扩大，布伦特 $%.2f",
        "Brent climbs %+.1f%% to $%.2f on falling shale output — 美国页岩油产量下降，布伦特涨 %+.1f%% 至 $%.2f",
        "Brent at $%.2f amid geopolitical premium — 地缘风险溢价，布伦特报 $%.2f",
        "Brent open interest rises %d%% QoQ — 布伦特未平仓合约上升 %d%%，期货市场活跃",
        "Brent hits $%.2f; Goldman maintains $%.2f year-end forecast — 高盛维持年底 $%.2f 预测，布伦特触及 $%.2f"
    };

    private static final String[] OTHER_TEMPLATES = {
        "Fed minutes show divided views on inflation trajectory",
        "Microsoft Azure reports %+.1f%% revenue growth in cloud segment",
        "S&P 500 hits new all-time high amid AI optimism",
        "European Central Bank holds rates, signals data-dependent approach",
        "NVIDIA announces next-generation Blackwell Ultra GPU architecture",
        "Global shipping rates drop %d%% as供应链压力缓解",
        "Champions League: Real Madrid advances with 3-1 victory",
        "IMF upgrades global growth forecast to %+.1f%% for 2025"
    };

    private final Random random = ThreadLocalRandom.current();

    @Override
    public String getSourceName() {
        return SOURCE;
    }

    @Override
    public List<NewsItem> fetch() {
        simulateLatency(200, 600);

        // 15% 空返回
        if (random.nextDouble() < 0.15) {
            return Collections.emptyList();
        }

        int count = random.nextInt(1, 4);
        List<NewsItem> items = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            if (random.nextDouble() < 0.70) {  // 70% 布伦特相关
                items.add(generateBrentNews(i));
            } else {
                items.add(generateOtherNews(i));
            }
        }

        return items;
    }

    private NewsItem generateBrentNews(int index) {
        String template = BRENT_TEMPLATES[random.nextInt(BRENT_TEMPLATES.length)];
        double price = 82.0 + random.nextDouble() * 28.0; // 82~110 USD
        double change = (random.nextDouble() - 0.5) * 7.0;
        double vol = 15.0 + random.nextDouble() * 20.0;
        int qoqRise = 5 + random.nextInt(20);

        String content;
        if (template.contains("surges %+.1f%% to")) {
            content = String.format(template, price, change, price, change, price);
        } else if (template.contains("open interest rises")) {
            content = String.format(template, price, qoqRise, price, qoqRise, price);
        } else if (template.contains("Goldman maintains")) {
            content = String.format(template, price, price, price, price);
        } else if (template.contains("implied volatility")) {
            content = String.format(template, price, vol, price, vol);
        } else if (template.contains("WTI spreads")) {
            content = String.format(template, price, price, price);
        } else if (template.contains("climbs %+.1f%%")) {
            content = String.format(template, change, price, change, price);
        } else if (template.contains("strategic reserves")) {
            content = String.format(template, price, price);
        } else if (template.contains("fluctuates near")) {
            content = String.format(template, price, price);
        } else {
            content = String.format(template, price, price);
        }

        LocalDateTime timestamp = LocalDateTime.now()
            .minusMinutes(random.nextInt(30))
            .minusSeconds(random.nextInt(60));

        return new NewsItem(0, timestamp, SOURCE, content, price);
    }

    private NewsItem generateOtherNews(int index) {
        String template = OTHER_TEMPLATES[random.nextInt(OTHER_TEMPLATES.length)];
        String content = String.format(template,
            20.0 + random.nextDouble() * 10.0,
            3 + random.nextInt(20),
            -5 + random.nextInt(15));

        return new NewsItem(0, LocalDateTime.now(), SOURCE, content, 0.0);
    }

    private void simulateLatency(int minMs, int maxMs) {
        try {
            int delay = minMs + random.nextInt(maxMs - minMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
