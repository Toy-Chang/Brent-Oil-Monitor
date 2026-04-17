package com.brentmonitor.fetcher.impl;

import com.brentmonitor.fetcher.NewsFetcher;
import com.brentmonitor.model.NewsItem;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock 新闻源：FX Empire（金融资讯站）
 *
 * 特点：
 * - 技术分析风格更强（包含更多价格预测类新闻）
 * - 20% 空返回（最低可靠性）
 * - 延迟区间最大（300~800ms）
 * - 80% 布伦特相关新闻（最高比例）
 */
public class FXEmpireNewsFetcher implements NewsFetcher {

    private static final String SOURCE = "FX Empire";

    // 布伦特原油技术分析新闻（中英双语）
    private static final String[] BRENT_TEMPLATES = {
        "Brent technical: Support $%.2f, Resistance $%.2f — 技术分析：支撑 $%.2f / 阻力 $%.2f",
        "Bull flag on Brent: $%.2f target — 布伦特\"牛旗\"形态，目标价 $%.2f",
        "Brent at $%.2f; MACD histogram turns positive — MACD柱状图转正，布伦特报 $%.2f",
        "Brent to test $%.2f if EIA beats — 若EIA数据超预期，布伦特将测试 $%.2f",
        "Brent $%.2f; 50-day MA support, RSI neutral at %d — 50日均线支撑，RSI中性 %d",
        "Brent ascending triangle near $%.2f — 布伦特在 $%.2f 附近形成上升三角形",
        "Brent weekly: $%.2f critical for trend — 周线关键位 $%.2f，趋势延续取决于此",
        "Analyst: Brent could reach $%.2f on seasonal demand — 分析师：季节性需求或推布伦特至 $%.2f",
        "Brent intraday: Buy $%.2f, Target $%.2f, Stop $%.2f — 日内建议：买入 $%.2f / 目标 $%.2f / 止损 $%.2f",
        "Brent Elliott Wave: %s correction to $%.2f — 艾略特波浪：%s调整看向 $%.2f",
        "RSI divergence signals reversal at $%.2f — RSI背离，布伦特在 $%.2f 发出转向信号",
        "Brent range: Support $%.2f / Resistance $%.2f — 布伦特区间：支撑 $%.2f / 阻力 $%.2f",
        "Brent at $%.2f above %d-day MA — 布伦特在 $%.2f，站稳 %d 日均线上方",
        "Brent Fibonacci 61.8%% retracement at $%.2f — 斐波那契 61.8% 回撤位于 $%.2f",
        "Volatility spikes; Brent $%.2f enters overbought — 波动率飙升，布伦特 $%.2f 进入超买区"
    };

    private static final String[] OTHER_TEMPLATES = {
        "EUR/USD technical analysis: Trend line support at 1.08",
        "Gold price forecast: Breakout above $%d signals $%d target",
        "GBP/JPY falls %d pips on weak UK employment data",
        "Technical analysis: Bitcoin approaches $%d resistance level",
        "Forex pairs to watch: USD/JPY, EUR/GBP, AUD/USD weekly outlook",
        "Nvidia stock forms head and shoulders; $%d price target revised"
    };

    private static final String[] CORRECTIONS = {"ABC", "flat", "zigzag", "double"};
    private static final String[] TRENDS = {"bullish", "bearish", "neutral"};

    private final Random random = ThreadLocalRandom.current();

    @Override
    public String getSourceName() {
        return SOURCE;
    }

    @Override
    public List<NewsItem> fetch() {
        simulateLatency(300, 800);

        // 20% 空返回（模拟不稳定数据源）
        if (random.nextDouble() < 0.20) {
            return Collections.emptyList();
        }

        int count = random.nextInt(1, 4);
        List<NewsItem> items = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            if (random.nextDouble() < 0.80) {  // 80% 布伦特相关
                items.add(generateBrentNews(i));
            } else {
                items.add(generateOtherNews(i));
            }
        }

        return items;
    }

    private NewsItem generateBrentNews(int index) {
        String template = BRENT_TEMPLATES[random.nextInt(BRENT_TEMPLATES.length)];
        double price = 80.0 + random.nextDouble() * 30.0;
        double support = price - random.nextDouble() * 5;
        double resistance = price + random.nextDouble() * 5;
        double target = price + (random.nextBoolean() ? 1 : -1) * random.nextDouble() * 3;
        double stop = price - random.nextDouble() * 2;
        int rsi = 30 + random.nextInt(41); // 30~70
        int maDays = random.nextBoolean() ? 50 : 200;
        String correction = CORRECTIONS[random.nextInt(CORRECTIONS.length)];

        String content;
        if (template.contains("Support $") && template.contains("Resistance $") && template.contains(" — ")) {
            content = String.format(template, support, resistance, support, resistance);
        } else if (template.contains("intraday")) {
            content = String.format(template, price, target, stop, price, target, stop);
        } else if (template.contains("Elliott Wave")) {
            content = String.format(template, correction, price);
        } else if (template.contains("RSI divergence")) {
            content = String.format(template, price, price);
        } else if (template.contains("range:")) {
            content = String.format(template, support, resistance, support, resistance);
        } else if (template.contains("above %d-day MA")) {
            content = String.format(template, price, price, maDays);
        } else if (template.contains("Fibonacci")) {
            content = String.format(template, price, price);
        } else if (template.contains("volatility spikes")) {
            content = String.format(template, price, price);
        } else if (template.contains("MACD")) {
            content = String.format(template, price, price);
        } else if (template.contains("EIA")) {
            content = String.format(template, price, price);
        } else if (template.contains("50-day MA")) {
            content = String.format(template, price, price, rsi, rsi);
        } else if (template.contains("ascending triangle")) {
            content = String.format(template, price, price);
        } else if (template.contains("weekly:")) {
            content = String.format(template, price, price);
        } else if (template.contains("seasonal demand")) {
            content = String.format(template, price, price);
        } else if (template.contains("Bull flag")) {
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
            1900 + random.nextInt(200),
            2100 + random.nextInt(400),
            50 + random.nextInt(100),
            60000 + random.nextInt(10000));

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
