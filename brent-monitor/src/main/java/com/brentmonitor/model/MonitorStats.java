package com.brentmonitor.model;

import java.time.LocalDateTime;

/**
 * 应用级统计数据（线程安全包装）
 *
 * 使用 AtomicInteger/AtomicLong 保证多线程无锁更新，
 * 避免 synchronized 的性能开销。
 */
public final class MonitorStats {

    private final java.util.concurrent.atomic.AtomicInteger runCount
        = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger fetchedCount
        = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger newCount
        = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger matchedCount
        = new java.util.concurrent.atomic.AtomicInteger(0);
    private volatile LocalDateTime lastRunTime = null;  // volatile 保证可见性

    // ==================== 计数器原子递增 ====================
    public void incrementRun()     { runCount.incrementAndGet(); lastRunTime = LocalDateTime.now(); }
    public void addFetched(int n)  { fetchedCount.addAndGet(n); }
    public void addNew(int n)      { newCount.addAndGet(n); }
    public void addMatched(int n)  { matchedCount.addAndGet(n); }

    // ==================== Getter ====================
    public int         getRunCount()     { return runCount.get(); }
    public int         getFetchedCount() { return fetchedCount.get(); }
    public int         getNewCount()     { return newCount.get(); }
    public int         getMatchedCount() { return matchedCount.get(); }
    public LocalDateTime getLastRunTime(){ return lastRunTime; }

    /** 重置计数器（每次调度开始前调用） */
    public void reset() {
        fetchedCount.set(0);
        newCount.set(0);
        matchedCount.set(0);
    }

    @Override
    public String toString() {
        return String.format(
            "执行: %d次 | 抓取: %d条 | 新增: %d条 | 命中: %d条",
            getRunCount(), getFetchedCount(), getNewCount(), getMatchedCount()
        );
    }
}
