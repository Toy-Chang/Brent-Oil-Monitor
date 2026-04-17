package com.brentmonitor.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 新闻条目数据模型（不可变对象，线程安全）
 *
 * 设计原则：
 * 1. 不可变性（Immutable）：所有字段在构造后不可修改，保证多线程共享安全
 * 2. 短路 equals/hashCode：当 contentHash 相同时直接返回 true，避免长字符串比较
 * 3. 使用 LocalDateTime 而非 Date：Java 8+ 推荐，线程安全且语义清晰
 */
public final class NewsItem {

    private final long id;                    // 全局递增 ID，用于 TableView 排序
    private final LocalDateTime timestamp;    // 发布时间
    private final String source;             // 新闻来源
    private final String content;             // 内容摘要
    private final double price;               // 关联油价（USD）
    private final String contentHash;         // 内容指纹（去重用）

    public NewsItem(long id, LocalDateTime timestamp, String source,
                    String content, double price) {
        this.id = id;
        this.timestamp = timestamp;
        this.source = source;
        this.content = content;
        this.price = price;
        // 生成内容指纹：取 content 的前 100 字符的 hashCode 作为去重依据
        this.contentHash = String.valueOf(content.hashCode());
    }

    // ==================== Getter（无 Setter，保证不可变）====================
    public long getId()                      { return id; }
    public LocalDateTime getTimestamp()      { return timestamp; }
    public String getSource()                { return source; }
    public String getContent()               { return content; }
    public double getPrice()                 { return price; }
    public String getContentHash()           { return contentHash; }

    public String getFormattedPrice() {
        // 多货币显示：USD (全球基准) + CNY (国内参考)
        double cny = price * 7.25;  // 近似汇率
        return String.format("$%.2f / ¥%.2f", price, cny);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                        // 自引用短路
        if (o == null || getClass() != o.getClass()) return false;
        NewsItem newsItem = (NewsItem) o;
        // 以 contentHash 作为主要比较依据，避免长字符串比较
        return Objects.equals(contentHash, newsItem.contentHash);
    }

    @Override
    public int hashCode() {
        // 只用 contentHash 计算 hashCode，配合 equals 实现高效去重
        return Objects.hash(contentHash);
    }

    @Override
    public String toString() {
        return String.format(
            "[%s] %s | %.2f USD | 来源: %s",
            timestamp, content.length() > 50 ? content.substring(0, 50) + "..." : content,
            price, source
        );
    }
}
