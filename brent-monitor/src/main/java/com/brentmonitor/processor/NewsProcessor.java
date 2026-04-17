package com.brentmonitor.processor;

import com.brentmonitor.cache.DeduplicationCache;
import com.brentmonitor.model.NewsItem;
import com.brentmonitor.model.MonitorStats;
import com.brentmonitor.queue.NewsItemQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据处理器（Consumer - 消费者角色）
 *
 * 职责：
 * 1. 从阻塞队列持续消费 NewsItem
 * 2. 关键词过滤（布伦特原油）
 * 3. 去重检测（ConcurrentHashMap）
 * 4. 统计计数（AtomicInteger）
 *
 * 生产者-消费者模型核心：
 * - BlockingQueue 作为缓冲区，实现速率解耦
 * - Consumer 不关心 Producer 的速度，只需从队列取数据
 * - 当队列为空时，take() 阻塞，不浪费 CPU
 *
 * 线程安全：
 * - AtomicInteger：统计计数（无锁更新）
 * - ConcurrentHashMap（DeduplicationCache）：去重（无锁查询）
 */
public final class NewsProcessor {

    // 布伦特原油关键词（大小写不敏感匹配）
    private static final String[] BRENT_KEYWORDS = {
        "Brent crude", "Brent Crude",
        "Brent oil", "Brent Oil",
        "布伦特原油", "布伦特"
    };

    private final NewsItemQueue queue;
    private final DeduplicationCache dedupCache;
    private final MonitorStats stats;
    private final ExecutorService consumerPool;

    // 处理结果回调（推送到 UI）
    private volatile java.util.function.Consumer<NewsItem> onNewsAccepted;
    private volatile java.util.function.Consumer<MonitorStats> onStatsUpdated;

    /**
     * 构造处理器
     * @param queue 共享队列
     * @param dedupCache 去重缓存
     * @param stats 统计对象（跨线程共享）
     * @param numConsumers 消费者线程数（建议：CPU 核心数）
     */
    public NewsProcessor(NewsItemQueue queue, DeduplicationCache dedupCache,
                         MonitorStats stats, int numConsumers) {
        this.queue = queue;
        this.dedupCache = dedupCache;
        this.stats = stats;

        // 消费者线程池：固定大小（不扩容，队列足够缓冲突发）
        this.consumerPool = Executors.newFixedThreadPool(numConsumers, r -> {
            Thread t = new Thread(r, "Processor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动消费者（启动后持续消费，直到 shutdown）
     */
    public void start() {
        for (int i = 0; i < ((ThreadPoolExecutor) consumerPool).getCorePoolSize(); i++) {
            // 注：需要获取实际线程数，这里直接用 numConsumers 参数
            consumerPool.submit(this::consumeLoop);
        }
    }

    /**
     * 持续消费循环（每个消费者线程执行）
     * 特点：
     * 1. take() 阻塞：队列为空时等待，不浪费 CPU
     * 2. 异常隔离：单条处理失败不影响其他
     * 3. 优雅退出：Thread.interrupted() 支持
     */
    private void consumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 从队列取数据（队列空时阻塞）
                NewsItem item = queue.take();

                // 处理数据（可能抛出异常，捕获保护）
                processItem(item);

                // 更新统计
                if (onStatsUpdated != null) {
                    onStatsUpdated.accept(stats);
                }

            } catch (InterruptedException e) {
                // 优雅退出信号
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Processor] 处理异常: " + e.getMessage());
            }
        }
    }

    /**
     * 处理单条新闻（关键词过滤 + 去重）
     */
    private void processItem(NewsItem item) {
        // 第一步：去重检查（O(1)，无锁）
        if (dedupCache.isDuplicate(item.getContentHash())) {
            // 命中已缓存：跳过，不计入新增
            return;
        }

        // 第二步：关键词过滤（大小写不敏感）
        boolean matched = containsBrentKeyword(item.getContent());

        if (matched) {
            // 命中：增加计数器 + 推送 UI
            stats.addMatched(1);
            if (onNewsAccepted != null) {
                onNewsAccepted.accept(item);
            }
        }

        // 第三步：更新统计（无论是否命中，都算新增）
        stats.addNew(1);
    }

    /**
     * 关键词检测（大小写不敏感）
     */
    private boolean containsBrentKeyword(String content) {
        if (content == null || content.isEmpty()) return false;
        String lower = content.toLowerCase();
        for (String keyword : BRENT_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 回调设置（UI 绑定）====================

    public void setOnNewsAccepted(java.util.function.Consumer<NewsItem> callback) {
        this.onNewsAccepted = callback;
    }

    public void setOnStatsUpdated(java.util.function.Consumer<MonitorStats> callback) {
        this.onStatsUpdated = callback;
    }

    /**
     * 关闭消费者线程池
     */
    public void shutdown() {
        consumerPool.shutdown();
        try {
            if (!consumerPool.awaitTermination(3, TimeUnit.SECONDS)) {
                consumerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerPool.shutdownNow();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "NewsProcessor[队列剩余: %d, 去重缓存: %s]",
            queue.size(), dedupCache
        );
    }
}
