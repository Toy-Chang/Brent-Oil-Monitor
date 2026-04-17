package com.brentmonitor.scheduler;

import com.brentmonitor.cache.DeduplicationCache;
import com.brentmonitor.fetcher.FetcherRunner;
import com.brentmonitor.fetcher.NewsFetcher;
import com.brentmonitor.fetcher.impl.BloombergNewsFetcher;
import com.brentmonitor.fetcher.impl.FXEmpireNewsFetcher;
import com.brentmonitor.fetcher.impl.ReutersNewsFetcher;
import com.brentmonitor.model.MonitorStats;
import com.brentmonitor.model.NewsItem;
import com.brentmonitor.processor.NewsProcessor;
import com.brentmonitor.queue.NewsItemQueue;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务调度器（Scheduler）
 *
 * 核心职责：
 * 1. 使用 ScheduledExecutorService 管理定时任务
 * 2. 协调 FetcherRunner（生产者）和 NewsProcessor（消费者）
 * 3. 控制启动/停止状态
 *
 * ScheduledExecutorService vs Timer：
 * - ScheduledExecutorService：多线程，支持并发执行任务
 * - Timer：单线程，前一个任务延迟会影响后续任务
 * - 本场景需要并发，所以选 ScheduledExecutorService
 *
 * 设计优势：
 * - 任务失败不影响后续调度（隔离）
 * - 支持 fixedDelay（固定间隔）和 fixedRate（固定频率）
 */
public final class MonitorScheduler {

    private final ScheduledExecutorService scheduler;
    private final FetcherRunner fetcherRunner;
    private final NewsProcessor processor;
    private final NewsItemQueue queue;
    private final DeduplicationCache dedupCache;
    private final MonitorStats stats;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledTask;
    private final long intervalMs;
    private final int initialDelayMs;

    /**
     * 构造调度器
     * @param intervalSeconds 调度间隔（分钟）
     * @param initialDelaySeconds 首次执行延迟（秒）
     */
    public MonitorScheduler(int intervalSeconds, int initialDelaySeconds) {
        this.intervalMs = TimeUnit.SECONDS.toMillis(intervalSeconds);
        this.initialDelayMs = initialDelaySeconds * 1000;

        // 初始化核心组件
        this.queue = new NewsItemQueue(100);      // 队列容量 100
        this.dedupCache = new DeduplicationCache();
        this.stats = new MonitorStats();

        // 线程池参数：
        // - 3 个 Fetcher（3 个新闻源）
        // - 3 个 Processor（与 Fetcher 数量匹配）
        this.fetcherRunner = new FetcherRunner(queue, 3, 10); // 最大 3 并发，QPS=10
        this.processor = new NewsProcessor(queue, dedupCache, stats,
            Runtime.getRuntime().availableProcessors());

        // 注册 3 个新闻源
        fetcherRunner.registerFetcher(new ReutersNewsFetcher());
        fetcherRunner.registerFetcher(new BloombergNewsFetcher());
        fetcherRunner.registerFetcher(new FXEmpireNewsFetcher());

        // 启动消费者
        processor.start();

        // 创建调度器（2 个线程，避免任务相互阻塞）
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Scheduler-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动定时监控
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            System.out.println("[Scheduler] 启动定时监控，间隔 " +
                intervalMs / 1000 + " 秒");

            // 首次执行延迟 initialDelayMs，后续每 intervalMs 执行一次
            scheduledTask = scheduler.scheduleAtFixedRate(
                this::executeOnce,
                initialDelayMs,
                intervalMs,
                TimeUnit.MILLISECONDS
            );
        } else {
            System.out.println("[Scheduler] 已经在运行中");
        }
    }

    /**
     * 停止定时监控
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            System.out.println("[Scheduler] 已停止定时监控");
        } else {
            System.out.println("[Scheduler] 未在运行");
        }
    }

    /**
     * 执行一次完整的抓取-处理流程
     *
     * 流程：
     * 1. 重置统计（本次执行的增量）
     * 2. 重置去重（可选，或使用滑动窗口）
     * 3. 并发抓取（3 个 Fetcher 同时执行）
     * 4. 收集结果（自动放入队列，Processor 消费）
     */
    private void executeOnce() {
        if (!running.get()) return;

        System.out.println("[Scheduler] ===== 执行第 " + (stats.getRunCount() + 1) + " 次抓取 =====");
        stats.incrementRun();
        stats.reset();

        try {
            // 并发抓取（主线程等待所有 Fetcher 完成）
            List<NewsItem> fetched = fetcherRunner.fetchAll();

            // 更新抓取数量
            stats.addFetched(fetched.size());

            System.out.println("[Scheduler] 本次抓取: " + fetched.size() + " 条");

            // 短暂等待 Processor 处理完成（最多 2 秒）
            Thread.sleep(2000);

            // 输出统计
            System.out.println("[Scheduler] 统计: " + stats);
            System.out.println("[Scheduler] 队列状态: " + queue);
            System.out.println("[Scheduler] 去重缓存: " + dedupCache);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[Scheduler] 执行被中断");
        } catch (Exception e) {
            System.err.println("[Scheduler] 执行异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 立即执行一次（不等待定时器）
     */
    public void executeNow() {
        if (!running.get()) {
            System.out.println("[Scheduler] 请先启动调度器");
            return;
        }
        scheduler.submit(this::executeOnce);
    }

    /**
     * 完全关闭（释放所有资源）
     */
    public void shutdown() {
        stop();
        processor.shutdown();
        fetcherRunner.shutdown();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        System.out.println("[Scheduler] 资源已全部释放");
    }

    // ==================== UI 绑定 ====================

    public void setOnNewsAccepted(java.util.function.Consumer<NewsItem> callback) {
        processor.setOnNewsAccepted(callback);
    }

    public void setOnStatsUpdated(java.util.function.Consumer<MonitorStats> callback) {
        processor.setOnStatsUpdated(callback);
    }

    public MonitorStats getStats() {
        return stats;
    }

    public boolean isRunning() {
        return running.get();
    }

    public NewsItemQueue getQueue() {
        return queue;
    }

    public DeduplicationCache getDedupCache() {
        return dedupCache;
    }
}
