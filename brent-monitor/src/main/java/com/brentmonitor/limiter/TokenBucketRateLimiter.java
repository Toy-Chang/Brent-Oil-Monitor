package com.brentmonitor.limiter;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 令牌桶限流器（Token Bucket Rate Limiter）
 *
 * 设计原理：
 * - Semaphore 控制最大并发数（削峰）
 * - 令牌补充机制保证流量平滑（填谷）
 *
 * 线程安全：
 * - 使用 AtomicLong 保证令牌计数原子更新
 * - 使用 Semaphore 实现阻塞式限流
 *
 * 使用场景：
 * - 限制并发抓取线程数（maxConcurrent）
 * - 限制 QPS（tokensPerSecond）
 */
public class TokenBucketRateLimiter {

    private final Semaphore semaphore;           // 并发上限信号量
    private final AtomicLong tokens;             // 当前令牌数
    private final long maxTokens;                // 桶容量
    private final long refillPerSecond;          // 每秒补充令牌数
    private final AtomicLong lastRefillTime;     // 上次补充时间
    private final Object refillLock = new Object();  // 补充操作锁

    /**
     * 构造限流器
     * @param maxConcurrent 最大并发数（桶容量）
     * @param tokensPerSecond 每秒补充令牌数（QPS 上限）
     */
    public TokenBucketRateLimiter(int maxConcurrent, long tokensPerSecond) {
        this.maxTokens = maxConcurrent;
        this.refillPerSecond = tokensPerSecond;
        this.semaphore = new Semaphore(maxConcurrent, true); // 公平模式
        this.tokens = new AtomicLong(maxTokens);
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * 获取一个令牌（阻塞直到获取成功）
     * 阻塞时自动释放 permit，限流线程不消耗 CPU 空转
     */
    public void acquire() throws InterruptedException {
        // 先补充令牌（懒补充，避免每次调用都加锁）
        refill();

        // 阻塞获取 permit（可中断）
        semaphore.acquire();

        // 获取成功后消耗一个令牌
        tokens.decrementAndGet();
    }

    /**
     * 非阻塞尝试获取令牌
     * @return true 获取成功，false 被限流
     */
    public boolean tryAcquire() {
        refill();
        if (semaphore.tryAcquire()) {
            tokens.decrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * 释放令牌（归还 permit，增加令牌计数）
     */
    public void release() {
        semaphore.release();
        tokens.incrementAndGet();
        // 不超过桶容量上限
        if (tokens.get() > maxTokens) {
            tokens.set(maxTokens);
        }
    }

    /**
     * 令牌补充逻辑（同步保证线程安全）
     * 按时间流逝比例补充令牌，实现平滑限流
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long last = lastRefillTime.get();

        // 计算时间差（秒），只补充整数秒
        long elapsedMs = now - last;
        if (elapsedMs < 1000) return;  // 不到 1 秒不补充

        long secondsElapsed = elapsedMs / 1000;

        synchronized (refillLock) {
            // 再次检查（double-check，避免重复补充）
            long current = tokens.get();
            long newTokens = Math.min(maxTokens, current + secondsElapsed * refillPerSecond);
            if (tokens.compareAndSet(current, newTokens)) {
                lastRefillTime.set(now);
            }
        }
    }

    /** 获取当前可用令牌数（用于监控） */
    public long getAvailableTokens() {
        return tokens.get();
    }

    /** 获取当前等待队列长度 */
    public int getQueueLength() {
        return semaphore.getQueueLength();
    }

    @Override
    public String toString() {
        return String.format(
            "TokenBucket[可用令牌: %d/%d, 等待队列: %d, QPS上限: %d]",
            tokens.get(), maxTokens, getQueueLength(), refillPerSecond
        );
    }
}
