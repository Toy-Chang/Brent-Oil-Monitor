package com.brentmonitor.queue;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生产者-消费者共享队列（核心数据结构）
 *
 * 设计原理：
 * 1. ArrayBlockingQueue：基于数组的有界阻塞队列，容量固定（100）
 * 2. 线程安全：内部已处理所有同步，无需额外保护
 * 3. 阻塞语义：队列满时 put() 阻塞（生产者等），队列空时 take() 阻塞（消费者等）
 *
 * 削峰填谷机制：
 * - 峰值时（Fetcher 快，Processor 慢）：队列积压，最多 100 条，超出时 Fetcher 阻塞
 * - 低谷时（Fetcher 慢，Processor 快）：消费者阻塞等待，不浪费 CPU
 * - 平衡点：队列维持稳定水位，两侧线程自动协调速率
 *
 * 与 Semaphore 的区别：
 * - BlockingQueue 控制队列长度（流量缓冲）
 * - Semaphore 控制并发数量（线程数量上限）
 * 两者配合：先限流（Semaphore），再缓冲（BlockingQueue），实现双重保护
 */
public final class NewsItemQueue {

    private final BlockingQueue<com.brentmonitor.model.NewsItem> queue;

    // 队列监控指标
    private final AtomicLong totalProduced = new AtomicLong(0);  // 生产总量
    private final AtomicLong totalConsumed = new AtomicLong(0);   // 消费总量
    private final int capacity;

    /**
     * 构造队列
     * @param capacity 队列容量（建议：核心线程数的 3~5 倍）
     */
    public NewsItemQueue(int capacity) {
        this.capacity = capacity;
        // fair=true：公平模式，FIFO 顺序，避免线程饥饿
        this.queue = new ArrayBlockingQueue<>(capacity, true);
    }

    // ==================== 生产者接口（Fetcher 调用）====================

    /**
     * 入队（阻塞式，若队列满则等待）
     * @param item 新闻条目
     * @throws InterruptedException 中断异常
     */
    public void put(com.brentmonitor.model.NewsItem item) throws InterruptedException {
        queue.put(item);          // 队列满时阻塞，不会丢失数据
        totalProduced.incrementAndGet();
    }

    /**
     * 入队（超时式）
     * @param item 新闻条目
     * @param timeout 超时时间
     * @return true 成功，false 超时放弃
     */
    public boolean offer(com.brentmonitor.model.NewsItem item, long timeout, TimeUnit unit)
            throws InterruptedException {
        boolean success = queue.offer(item, timeout, unit);
        if (success) totalProduced.incrementAndGet();
        return success;
    }

    // ==================== 消费者接口（Processor 调用）====================

    /**
     * 出队（阻塞式，若队列空则等待）
     * @return 新闻条目
     * @throws InterruptedException 中断异常
     */
    public com.brentmonitor.model.NewsItem take() throws InterruptedException {
        com.brentmonitor.model.NewsItem item = queue.take();  // 队列空时阻塞
        totalConsumed.incrementAndGet();
        return item;
    }

    /**
     * 出队（超时式）
     * @param timeout 超时时间
     * @return NewsItem 或 null（超时）
     */
    public com.brentmonitor.model.NewsItem poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        com.brentmonitor.model.NewsItem item = queue.poll(timeout, unit);
        if (item != null) totalConsumed.incrementAndGet();
        return item;
    }

    // ==================== 监控接口 ====================

    /** 当前队列长度 */
    public int size() {
        return queue.size();
    }

    /** 剩余容量 */
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    /** 生产总量 */
    public long getTotalProduced() { return totalProduced.get(); }

    /** 消费总量 */
    public long getTotalConsumed() { return totalConsumed.get(); }

    /** 清空队列（Shutdown 时调用） */
    public void clear() {
        queue.clear();
    }

    /** 队列是否为空 */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
            "NewsItemQueue[%d/%d] | 产出: %d | 消费: %d | 积压: %d",
            size(), capacity, totalProduced.get(), totalConsumed.get(), size()
        );
    }
}
