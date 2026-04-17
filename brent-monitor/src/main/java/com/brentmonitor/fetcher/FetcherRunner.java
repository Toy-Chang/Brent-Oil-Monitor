package com.brentmonitor.fetcher;

import com.brentmonitor.limiter.TokenBucketRateLimiter;
import com.brentmonitor.model.NewsItem;
import com.brentmonitor.queue.NewsItemQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发抓取协调器（Fetcher Runner）
 *
 * 核心职责：
 * 1. 管理多个 NewsFetcher（生产者）
 * 2. 使用 ThreadPoolExecutor 并发执行抓取
 * 3. 通过 TokenBucketRateLimiter 控制 QPS
 * 4. 将结果放入阻塞队列（交给 Processor 消费）
 *
 * 线程池设计：
 * - 核心线程数 = 3（3 个新闻源，每个对应一个线程）
 * - 最大线程数 = 6（允许突发情况扩展）
 * - 队列容量 = 0（SynchronousQueue，直接提交给线程执行）
 *
 * 设计原因：
 * - 固定数量的 Fetcher，不需要很大线程池
 * - SynchronousQueue 保证任务不会被积压（队列满时线程池扩容或拒绝）
 * - 限流由 TokenBucketRateLimiter 处理，不是队列
 */
public final class FetcherRunner {

    private final ThreadPoolExecutor executor;
    private final NewsItemQueue queue;
    private final TokenBucketRateLimiter rateLimiter;
    private final List<NewsFetcher> fetchers;

    // 统计
    private final AtomicInteger totalFetched = new AtomicInteger(0);

    /**
     * 构造抓取协调器
     * @param queue 共享队列（生产者和消费者的交接点）
     * @param maxConcurrent 最大并发抓取数（限流信号量容量）
     * @param qps 每秒最多抓取请求数（令牌补充速率）
     */
    public FetcherRunner(NewsItemQueue queue, int maxConcurrent, long qps) {
        this.queue = queue;
        this.rateLimiter = new TokenBucketRateLimiter(maxConcurrent, qps);
        this.fetchers = new ArrayList<>();

        // 创建线程池：核心=3, 最大=6, 队列=SynchronousQueue（不缓冲任务）
        this.executor = new ThreadPoolExecutor(
            3,                          // corePoolSize: 固定 3 个线程（每个源一个）
            6,                          // maximumPoolSize: 允许临时扩展到 6（应对突发）
            60L, TimeUnit.SECONDS,     // keepAliveTime: 60s 后回收多余线程
            new SynchronousQueue<>(),  // 无队列缓冲，直接提交给线程执行
            // 自定义线程工厂：命名清晰，便于调试
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Fetcher-" + counter.getAndIncrement());
                    t.setDaemon(true);  // daemon 线程：主程序退出时自动终止
                    return t;
                }
            },
            // 拒绝策略：若无法提交，说明系统过载，记录日志
            new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    System.err.println("[FetcherRunner] 任务被拒绝，线程池已饱和");
                }
            }
        );
    }

    /**
     * 注册新闻源
     */
    public void registerFetcher(NewsFetcher fetcher) {
        fetchers.add(fetcher);
    }

    /**
     * 并发执行所有已注册的新闻源抓取
     * 所有 Fetcher 同时启动（CompletableFuture.allOf）
     * 主线程阻塞等待全部完成
     */
    public List<NewsItem> fetchAll() throws InterruptedException {
        if (fetchers.isEmpty()) {
            return List.of();
        }

        // 为每个 Fetcher 创建一个异步任务
        List<CompletableFuture<List<NewsItem>>> futures = fetchers.stream()
            .map(fetcher -> CompletableFuture.supplyAsync(() -> {
                return fetchWithLimiter(fetcher);
            }, executor))
            .toList();

        // 等待所有任务完成（allOf 阻塞）
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 收集结果
        List<NewsItem> allItems = new CopyOnWriteArrayList<>();
        for (CompletableFuture<List<NewsItem>> future : futures) {
            try {
                List<NewsItem> items = future.get();  // 已完成，直接 get
                if (items != null) {
                    allItems.addAll(items);
                    totalFetched.addAndGet(items.size());
                }
            } catch (ExecutionException e) {
                System.err.println("[FetcherRunner] 抓取失败: " + e.getCause().getMessage());
            }
        }

        return allItems;
    }

    /**
     * 单个 Fetcher 的抓取逻辑（限流保护）
     */
    private List<NewsItem> fetchWithLimiter(NewsFetcher fetcher) {
        try {
            // 尝试获取令牌（阻塞直到可用，限流的核心）
            rateLimiter.acquire();

            try {
                // 实际执行抓取
                List<NewsItem> items = fetcher.fetch();

                // 将抓取结果放入阻塞队列（Producer → Queue）
                if (items != null) {
                    for (NewsItem item : items) {
                        // offer 超时 1 秒，若队列满则丢弃（避免无限等待）
                        queue.offer(item, 1, TimeUnit.SECONDS);
                    }
                }

                return items != null ? items : List.of();
            } finally {
                // ⚠️ 关键修复：归还 permit，否则 semaphore 耗尽后永久阻塞
                rateLimiter.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[FetcherRunner] " + fetcher.getSourceName() + " 被中断");
            return List.of();
        }
    }

    /**
     * 关闭线程池（Shutdown 时调用）
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public int getTotalFetched() {
        return totalFetched.get();
    }

    @Override
    public String toString() {
        return String.format(
            "FetcherRunner[线程数: %d/%d, 限流: %s, 总抓取: %d]",
            executor.getActiveCount(), executor.getPoolSize(), rateLimiter, getTotalFetched()
        );
    }
}
