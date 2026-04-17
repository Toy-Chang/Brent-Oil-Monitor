package com.brentmonitor.fetcher.impl;

import com.brentmonitor.fetcher.NewsFetcher;
import com.brentmonitor.model.NewsItem;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock 新闻源：Reuters（路透社）
 *
 * 设计说明：
 * 1. 模拟真实新闻 API：返回包含布伦特原油关键词的新闻
 * 2. 模拟网络延迟：100~500ms（真实 API 会有 RTT 延迟）
 * 3. 模拟部分失败：10% 概率返回空列表
 * 4. 模拟数据多样性：多个预设模板 + 随机油价/时间
 *
 * 线程安全：
 * - 使用 ThreadLocalRandom 确保多线程并发安全
 * - 预设数据（PRESET_TEMPLATES）只读，无需同步
 */
public class ReutersNewsFetcher implements NewsFetcher {

    private static final String SOURCE = "Reuters";

    // 布伦特原油相关新闻模板（中英双语）
    // 内容 = "英文原文（中文翻译）"
    private static final String[] BRENT_TEMPLATES = {
        "Brent crude surged %+.1f%% to $%.2f/barrel — 中东局势紧张，布伦特原油大涨",
        "Brent Crude climbs to $%.2f as OPEC+ extends cuts — 欧佩克+延长减产，油价攀升至 $%.2f",
        "Brent Oil hits $%.2f after %+.1f%% surprise inventory draw — 美国库存意外下降，油价触及 $%.2f",
        "Brent crude settles at $%.2f, up %+.1f%% on supply concerns — 供应忧虑支撑，布伦特收于 $%.2f",
        "Brent crude edges up to $%.2f on strong China PMI — 中国PMI超预期，布伦特涨至 $%.2f",
        "Brent Crude drops %+.1f%% to $%.2f amid dollar strength — 美元走强，布伦特跌至 $%.2f",
        "Brent at $%.2f; EIA reports %+.1f%% inventory build — EIA库存增加，布伦特报价 $%.2f",
        "OPEC+ compliance at %d%%; Brent holds above $%.2f — 欧佩克+减产执行率 %d%%，布伦特守住 $%.2f",
        "Brent crude stabilizes at $%.2f after volatile session — 剧烈波动后，布伦特企稳于 $%.2f",
        "Global demand forecast raised; Brent advances to $%.2f — 全球需求预期上调，布伦特升至 $%.2f",
        "Geopolitical risk widens; Brent at $%.2f/barrel — 地缘风险溢价扩大，布伦特报价 $%.2f",
        "Brent Crude dips to $%.2f after %+.1f%% rally — 连续上涨 %+.1f%% 后回调，布伦特报 $%.2f",
        "Brent Oil at $%.2f amid seasonal demand shift — 季节性需求转换，布伦特交易于 $%.2f",
        "Record volume as Brent swings to $%.2f — 价格剧烈波动，布伦特触及 $%.2f 成交创新高",
        "IEA raises demand estimate; Brent at $%.2f — IEA上调需求预期，布伦特报 $%.2f",
        "Brent settles $%.2f as hurricane threatens output — 飓风威胁产能，布伦特收于 $%.2f"
    };

    // 非布伦特新闻（用于测试过滤逻辑）
    private static final String[] OTHER_TEMPLATES = {
        "Federal Reserve signals potential rate cut in upcoming meeting",
        "Apple announces new MacBook Pro with M4 chip",
        "Global gold prices hit record high amid inflation concerns",
        "Tesla delivers %d vehicles in Q%d, beats analyst expectations",
        "Champions League final set for June 3rd at Wembley Stadium",
        "Bitcoin falls below $%d amid regulatory crackdown",
        "New study reveals coffee consumption linked to longevity",
        "Amazon Web Services announces new AI-powered cloud services"
    };

    private final Random random = ThreadLocalRandom.current();

    @Override
    public String getSourceName() {
        return SOURCE;
    }

    @Override
    public List<NewsItem> fetch() {
        // 模拟网络延迟（100~500ms）
        simulateLatency(100, 500);

        // 10% 概率返回空（模拟 API 临时不可用）
        if (random.nextDouble() < 0.10) {
            return Collections.emptyList();
        }

        // 生成 1~3 条新闻（模拟真实 API 返回条数不固定）
        int count = random.nextInt(1, 4);
        List<NewsItem> items = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            // 75% 概率生成布伦特相关新闻
            if (random.nextDouble() < 0.75) {
                items.add(generateBrentNews(i));
            } else {
                items.add(generateOtherNews(i));
            }
        }

        return items;
    }

    private NewsItem generateBrentNews(int index) {
        String template = BRENT_TEMPLATES[random.nextInt(BRENT_TEMPLATES.length)];
        double price = 80.0 + random.nextDouble() * 25.0; // 80~105 USD
        double change = (random.nextDouble() - 0.5) * 6.0; // -3% ~ +3%
        int compliance = 90 + random.nextInt(11); // 90~100%

        String content;
        if (template.contains("OPEC+ extends")) {
            content = String.format(template, price, price);
        } else if (template.contains("surprise inventory draw")) {
            content = String.format(template, price, change, price);
        } else if (template.contains("supply concerns") && template.contains("settles")) {
            content = String.format(template, price, change, price);
        } else if (template.contains("China PMI")) {
            content = String.format(template, price, price);
        } else if (template.contains("dollar strength")) {
            content = String.format(template, change, price, price);
        } else if (template.contains("EIA reports")) {
            content = String.format(template, price, change, price);
        } else if (template.contains("OPEC+ compliance")) {
            content = String.format(template, change, price, compliance, price, compliance, price);
        } else if (template.contains("stabilizes") && template.contains("volatile")) {
            content = String.format(template, price, price);
        } else if (template.contains("demand forecast raised")) {
            content = String.format(template, price, price);
        } else if (template.contains("geopolitical")) {
            content = String.format(template, price, price);
        } else if (template.contains("profit-taking")) {
            content = String.format(template, price, change, change, price);
        } else if (template.contains("seasonal demand")) {
            content = String.format(template, price, price);
        } else if (template.contains("record volume")) {
            content = String.format(template, price, price);
        } else if (template.contains("IEA raises")) {
            content = String.format(template, price, price);
        } else if (template.contains("hurricane threatens")) {
            content = String.format(template, price, price);
        } else if (template.contains("surged")) {
            content = String.format(template, change, price, price);
        } else {
            content = String.format(template, change, price);
        }

        // 模拟发布时间（最近 30 分钟内）
        LocalDateTime timestamp = LocalDateTime.now()
            .minusMinutes(random.nextInt(30))
            .minusSeconds(random.nextInt(60));

        return new NewsItem(0, timestamp, SOURCE, content, price);
    }

    private NewsItem generateOtherNews(int index) {
        String template = OTHER_TEMPLATES[random.nextInt(OTHER_TEMPLATES.length)];
        String content = String.format(template,
            random.nextInt(1000, 2000), random.nextInt(1, 5));

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
