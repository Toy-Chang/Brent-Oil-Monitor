package com.brentmonitor.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发安全的去重缓存（Bloom Filter 的简化实现）
 *
 * 设计原理：
 * 1. ConcurrentHashMap 作为底层存储，所有操作无锁（CAS 保证）
 * 2. 以 contentHash 为 key，去重判断 O(1)
 * 3. 使用 AtomicInteger 统计命中次数
 *
 * 线程安全：
 * - ConcurrentHashMap 本身是线程安全的
 * - 不需要额外的 synchronized 保护
 *
 * 性能优化：
 * - 弱一致性：允许短暂重复（可接受），换取高吞吐
 * - 无锁读取：contains() 完全无锁
 */
public final class DeduplicationCache {

    // 底层存储：key = contentHash, value = 标记值（可重复插入同一 key）
    private final ConcurrentHashMap<String, Boolean> cache;

    // 统计指标
    private final AtomicInteger totalChecks = new AtomicInteger(0);
    private final AtomicInteger duplicateHits = new AtomicInteger(0);

    public DeduplicationCache() {
        this.cache = new ConcurrentHashMap<>(1024);
    }

    /**
     * 检查是否已存在（重复返回 true）
     * 线程安全：ConcurrentHashMap 的 get 是无锁的
     */
    public boolean isDuplicate(String contentHash) {
        totalChecks.incrementAndGet();
        // putIfAbsent 返回旧值，若为 null 表示是新内容
        Boolean existing = cache.putIfAbsent(contentHash, Boolean.TRUE);
        if (existing != null) {
            duplicateHits.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * 添加到缓存（幂等操作）
     */
    public void put(String contentHash) {
        cache.putIfAbsent(contentHash, Boolean.TRUE);
    }

    /** 缓存大小（用于监控） */
    public int size() {
        return cache.size();
    }

    /** 重复命中率 */
    public double getDuplicateRate() {
        int total = totalChecks.get();
        return total == 0 ? 0.0 : (double) duplicateHits.get() / total;
    }

    /** 清空缓存（定时任务可调用） */
    public void clear() {
        cache.clear();
    }

    @Override
    public String toString() {
        return String.format(
            "DeduplicationCache[缓存数: %d, 检查次数: %d, 重复命中: %d, 去重率: %.1f%%]",
            size(), totalChecks.get(), duplicateHits.get(),
            getDuplicateRate() * 100
        );
    }
}
