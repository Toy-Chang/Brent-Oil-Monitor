# Brent Oil Monitor — Source Code / 源码说明

**Java 17 + JavaFX 并发监控示例 / Java 17 + JavaFX Concurrency Demo**

---

## Source Code Structure / 源码结构

```
src/main/java/com/brentmonitor/
├── Main.java                    # 应用程序入口 / Application entry point
├── model/
│   ├── NewsItem.java            # 新闻条目（不可变，线程安全）
│   └── MonitorStats.java        # 统计数据模型
├── scheduler/
│   └── MonitorScheduler.java    # ScheduledExecutorService 调度器
├── fetcher/
│   ├── NewsFetcher.java         # 新闻抓取器接口
│   ├── FetcherRunner.java       # 生产者：提交抓取任务
│   └── impl/
│       ├── ReutersNewsFetcher.java
│       ├── BloombergNewsFetcher.java
│       └── FXEmpireNewsFetcher.java
├── queue/
│   └── NewsItemQueue.java       # ArrayBlockingQueue 共享队列
├── processor/
│   └── NewsProcessor.java       # 消费者：过滤、去重、推送 UI
├── cache/
│   └── DeduplicationCache.java  # ConcurrentHashMap 去重
├── limiter/
│   └── TokenBucketRateLimiter.java  # Semaphore + CAS 限流
└── ui/
    └── MonitorUI.java           # JavaFX 界面（TableView）
```

---

## Key Implementation Details / 关键实现细节

### NewsItem — 不可变数据模型

```java
public final class NewsItem {
    private final long id;                    // 全局递增 ID
    private final LocalDateTime timestamp;    // 发布时间（GMT 格式显示）
    private final String source;             // 新闻来源
    private final String content;             // 内容摘要
    private final double price;               // 关联油价（USD）
    private final String contentHash;         // 内容指纹（去重用）
}
```

### MonitorUI — 时间排序 & GMT 显示

```java
// 时间格式化（GMT）
private static final DateTimeFormatter TIME_FMT =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                      .withZone(ZoneId.of("GMT"));

private static String formatGMT(LocalDateTime ldt) {
    if (ldt == null) return "";
    ZonedDateTime gmt = ldt.atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneId.of("GMT"));
    return TIME_FMT.format(gmt) + " GMT";
}

// 新消息添加到列表顶部（最新在前）
FXCollections.sort(newsData, (a, b) ->
    b.getTimestamp().compareTo(a.getTimestamp()));
```

### ConcurrentHashMap — 无锁去重

```java
private final ConcurrentHashMap<String, Boolean> cache =
    new ConcurrentHashMap<>();

public boolean isDuplicate(String hash) {
    // 原子操作：检查 + 插入一次完成，无竞态
    return cache.putIfAbsent(hash, Boolean.TRUE) != null;
}
```

### AtomicInteger — 无锁计数器

```java
private final AtomicInteger runCount = new AtomicInteger(0);

public void incrementRun() {
    runCount.incrementAndGet(); // CAS，无需 synchronized
}
```

---

## Build / 构建

```bash
# 依赖：Java 17+, Maven 3.8+
mvn clean package -DskipTests

# 运行
java -jar target/brent-monitor.jar
```

## App Icon / 应用图标

macOS App 图标（⛽ 石油桶 + ⛽ 加油泵主题）位于：

```
dist/BrentMonitor.app/Contents/Resources/App.icns
```

如需替换图标，重新打包时通过 jpackage 的 `--icon` 参数指定。
