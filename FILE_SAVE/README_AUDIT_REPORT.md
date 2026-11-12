# README 一致性审查报告 / README Audit Report

- 审查对象：`README.md`（根目录）。附带核对了 `brent-monitor/README.md` 中重复的内容。
- 审查依据：`brent-monitor/src/main/java` 下全部 15 个 Java 文件、`pom.xml`、`build.sh`、`build-macos-app.sh`、`run.sh`、`.gitignore`、`docs/demo.png`、git 历史。
- 产出：`README_REVISED.md`（新版 README）、本报告。未修改原 `README.md` 及任何源码、脚本、配置。

## 1. 结论

原 README 的整体结构和大部分设计意图与代码相符：调度器、线程池、阻塞队列、消费者、去重、JavaFX UI 都有对应实现。但存在三类问题：

1. **运行和构建说明不能直接照做**：JAR 文件名、仓库目录名、JavaFX SDK 步骤与实际不符。
2. **并发机制的描述与代码行为不一致**：背压、令牌桶限流、ObservableList 线程安全、ConcurrentHashMap 实现等。
3. **夸大或无依据的表述**："生产级""高并发"、"快约 10 倍"、"无资源泄漏"。另外有若干已实现的重要行为未提及。

逐项结果见第 2 节（标记含义见各节开头），无法验证的部分见第 5 节。

## 2. 问题清单与代码证据

标记：❌ 不准确　⚠️ 部分准确　➕ 重要遗漏　❓ 无法验证

### A. 运行与构建

| # | 标记 | 原 README 声明 | 实际情况 | 代码/配置证据 |
|---|------|----------------|----------|---------------|
| A1 | ❌ | `java ... -jar target/brent-monitor.jar`；`java -jar brent-monitor/target/brent-monitor.jar` | 产物是 `brent-monitor-shaded.jar`。已在临时副本中执行 `mvn -o package -DskipTests` 验证：`target/` 下只有 `brent-monitor-shaded.jar` | `brent-monitor/pom.xml`：maven-shade-plugin `<finalName>${project.artifactId}-shaded</finalName>`；exec-maven-plugin 删除 `brent-monitor-1.0.0.jar` |
| A2 | ❌ | `./build.sh` 后可 `java -jar .../brent-monitor.jar` | `build.sh` 检查 `target/brent-monitor.jar`，永远不存在，会打印"打包失败"并 `exit 1`；`run.sh` 同样检查该文件 | `build.sh`：`JAR_FILE="$PROJECT_DIR/target/brent-monitor.jar"`；`run.sh`：同一变量 |
| A3 | ❌ | `cd Brent-Demo-New/brent-monitor`、`cd Brent-Demo-New` | 仓库根目录是 `Brent-Oil-Monitor` | 工作区目录名；README "File Structure" 同样写 `Brent-Demo-New/` |
| A4 | ❌ | Step 1 需安装 JavaFX SDK，设置 `JAVAFX_HOME` | 脚本不使用 `JAVAFX_HOME`，JavaFX jar 取自 `~/.m2`（需先用 Maven 构建过） | `build-macos-app.sh`：`REPO=$(mvn help:evaluate ...)`、`MODULE_PATH=$REPO/org/openjfx/...`；全仓库 grep `JAVAFX_HOME` 只出现在 README |
| A5 | ❌ | 手动 jpackage 命令用 `--main-jar brent-monitor.jar`、`--module-path "$JAVAFX_HOME/lib"` | 脚本用 `brent-monitor-shaded.jar`、Maven 仓库中的 3 个 jar，并带 `--java-options "-Djpackage.app-version=1.0.0"`、不同的 `--description` | `build-macos-app.sh` 中的 `jpackage` 调用 |
| A6 | ❌ | `dist/BrentMonitor.app`（⛽ 石油图标） | `.app` 被 `.gitignore` 忽略（只提交 DMG）；脚本没传 `--icon`，`BrentMonitor.icns`、`icon/*.png` 未被引用 | `.gitignore`：`dist/*` + `!dist/BrentMonitor.dmg`；全仓库 grep `icns`/`--icon` 在脚本和 Java 中均无匹配 |
| A7 | ⚠️ | Prerequisites：Maven 3.8+ | `pom.xml` 没有限定 Maven 版本（无 enforcer 插件） | `pom.xml` |
| A8 | ➕ | （未提及）打包流程细节 | 脚本会 ad-hoc 签名、生成 DMG、并启动应用 5 秒做冒烟检查 | `build-macos-app.sh`：`codesign -s -`、`hdiutil create`、`sleep 5` |

### B. 技术栈与结构

| # | 标记 | 原 README 声明 | 实际情况 | 证据 |
|---|------|----------------|----------|------|
| B1 | ⚠️ | JavaFX 17 | 具体为 17.0.13 | `pom.xml`：`<javafx.version>17.0.13</javafx.version>` |
| B2 | ➕ | 技术栈表只列 Java/JavaFX/并发/Maven/jpackage | 实际还用 JDK `HttpClient`、JDK DOM 解析 RSS、自定义 Logger 写 `~/BrentMonitor.log` | `OilPriceNewsFetcher.java`（`RssNewsFetcher`）、`util/Logger.java` |
| B3 | ➕ | 包结构和文件树缺少 `util/Logger`（包结构）、`RssNewsFetcher` | `RssNewsFetcher` 是定义在 `OilPriceNewsFetcher.java` 里的包级抽象类，3 个 Fetcher 都继承它 | `OilPriceNewsFetcher.java` |
| B4 | ➕ | 文件树缺 `docs/demo.png`、`brent-monitor/README.md`、`dependency-reduced-pom.xml`、`resources/`、`.gitignore` | 这些文件均已提交 | `git ls-files` |
| B5 | ➕ | 未说明没有测试和 CI | 无 `src/test`、无测试依赖、无 `.github`；构建命令里的 `-DskipTests` 没有实际作用 | 目录列表；`pom.xml` 无 surefire/JUnit |
| B6 | ✅ | "无外部并发库" | 准确，保留。注意 `pom.xml` 把 maven-compiler-plugin、maven-shade-plugin 声明成了 `<dependency>`（见第 6 节） | `pom.xml` |

### C. 并发与业务逻辑

| # | 标记 | 原 README 声明 | 实际情况 | 证据 |
|---|------|----------------|----------|------|
| C1 | ❌ | 队列满时 `put()` 阻塞 Fetcher，自动背压 | 生产者调用 `offer(item, 1, SECONDS)`，最多等 1 秒，超时则丢弃，返回值未检查、无日志。`put()` 已实现但无调用方 | `FetcherRunner.fetchWithLimiter()`：`queue.offer(item, 1, TimeUnit.SECONDS)`；`NewsItemQueue.put()` 无引用 |
| C2 | ❌ | 面试要点 #2："队列满时生产者自动阻塞产生背压"（归因于 SynchronousQueue） | SynchronousQueue 是线程池的任务队列，与新闻队列无关 | `FetcherRunner` 构造函数；`NewsItemQueue` |
| C3 | ❌ | 令牌桶："无可用 token 时线程阻塞"；"token 补充机制平滑限流" | `acquire()` 只在 `semaphore.acquire()` 上阻塞，不检查 `tokens`。`tokensPerSecond=10` 实际不限制请求速率；实际生效的只有 3 个并发许可。名称 TokenBucket 与行为有差距 | `TokenBucketRateLimiter.acquire()`、`refill()` |
| C4 | ⚠️ | "Semaphore + CAS" | 补充令牌用 `synchronized(refillLock)` + `compareAndSet`，不是纯 CAS | `TokenBucketRateLimiter.refill()` |
| C5 | ❌ | 示例 `new TokenBucketRateLimiter(maxConcurrent: 3, tokensPerSecond: 10)` | 非合法 Java（命名参数）；实际在 `FetcherRunner` 内部创建，入口是 `new FetcherRunner(queue, 3, 10)` | `MonitorScheduler` 构造函数；`FetcherRunner` 构造函数 |
| C6 | ❌ | ThreadPoolExecutor 示例中 `new ThreadFactory("Fetcher-%d")`、`new RejectedExecutionHandler()` | 非合法代码：实际是匿名内部类。线程名 `Fetcher-N`（daemon）；拒绝策略仅 `System.err.println`，不抛出 | `FetcherRunner` 构造函数 |
| C7 | ❌ | 线程命名示例 `Processor-2` | 消费者线程统一叫 `Processor`，无序号 | `NewsProcessor` 构造函数：`new Thread(r, "Processor")` |
| C8 | ❌ | "ConcurrentHashMap：分段锁机制，读操作几乎无锁"；"无锁去重" | JDK 8+ 的 ConcurrentHashMap 已不使用分段锁（Segment）。`putIfAbsent` 在空桶时用 CAS，桶冲突时对桶头节点加 `synchronized`，不是严格"无锁" | 项目使用 Java 17（`pom.xml`）；JDK 实现为公开事实 |
| C9 | ❌ | "`AtomicInteger` … 简单计数器快约 10 倍" | 仓库无任何基准测试，属无依据的性能数据，已删除 | 无测试/基准代码 |
| C10 | ❌ | 去重：`String.valueOf(content.hashCode())`，"短字符串比较短路机制避免长字符串比较开销" | 指纹对**完整** `content` 取 32 位 hashCode（注释写"前 100 字符"，代码并非如此）。缓存键本身是 hash 字符串，所谓"短路"与缓存无关；存在碰撞可能 | `NewsItem` 构造函数 |
| C11 | ➕ | 未说明缓存行为 | 缓存只增不减，`clear()` 从未被调用；去重发生在关键词过滤**之前**，所以未命中的新闻也会入缓存 | `NewsProcessor.processItem()`；全仓库 `clear()` 仅定义无调用 |
| C12 | ⚠️ | 架构图把 "Keyword Filter" 放在 "Deduplication" 之前；数据流步骤 5–7 | 实际顺序：去重 → 关键词过滤 → 统计。步骤 6/7 已调整 | `NewsProcessor.processItem()` |
| C13 | ➕ | 未说明关键词规则 | 子串匹配（忽略大小写）：`Brent crude`、`Brent oil`、`布伦特原油`、`布伦特`；单独的 "Brent" 不命中 | `NewsProcessor.BRENT_KEYWORDS`、`containsBrentKeyword()` |
| C14 | ❌ | 面试要点 #5："TableView 的 ObservableList 在内部处理自己的同步" | `ObservableList` 不是线程安全的。代码的安全性来自"所有修改都在 `Platform.runLater` 里"（`MonitorUI` 的类注释也有同样的错误说法） | `MonitorUI.setupCallbacks()` |
| C15 | ❌ | 面试要点 #6："关闭时无资源泄漏" | 无依据，已删除。并且消费者线程阻塞在 `take()`，`shutdown()` 不会中断它们，`NewsProcessor.shutdown()` 通常等满 3 秒后由 `shutdownNow()` 中断（静态推断，未运行验证） | `NewsProcessor.consumeLoop()`、`shutdown()` |
| C16 | ❌ | 配置：`new MonitorScheduler(intervalMinutes: 0.5, initialDelaySeconds: 3)`，"编辑 `MonitorScheduler.java` 构造函数" | 构造函数签名为 `(int intervalSeconds, int initialDelaySeconds)`，调用在 `MonitorUI.start()`：`new MonitorScheduler(30, 3)`。参数是秒，不是分钟 | `MonitorScheduler` 构造函数；`MonitorUI.start()` |
| C17 | ➕ | 数据流未提及 | 调度线程用 `CompletableFuture.supplyAsync` + `allOf().join()` 阻塞等待；每轮后 `sleep(2000)`；启动后自动 `start()`；HTTP 超时 8 秒/12 秒；XXE 防护 | `FetcherRunner.fetchAll()`、`MonitorScheduler.executeOnce()`、`MonitorUI.start()`、`RssNewsFetcher` |
| C18 | ➕ | 未提及 | 其他硬编码参数：队列 100、消费者线程 = CPU 核数、界面最多 200 行 | `MonitorScheduler`、`MonitorUI` |

### D. UI 与展示

| # | 标记 | 原 README 声明 | 实际情况 | 证据 |
|---|------|----------------|----------|------|
| D1 | ⚠️ | 统计面板"实时显示 Runs / Fetched / New / Matched" | `Fetched/New/Matched` 每轮开始被 `reset()` 清零，是单轮值而不是累计值；面板只在处理完一条新闻后刷新，`Fetched` 在抓取完成后才累加，因此面板上的 `Fetched` 可能滞后（截图中 Fetched=0 而 New=5 与此一致，属推断） | `MonitorScheduler.executeOnce()`、`MonitorStats.reset()`、`NewsProcessor.consumeLoop()` |
| D2 | ➕ | 未提及价格列 | RSS 无价格，`NewsItem.price` 恒为 `0.0`，界面显示 `$0.00 / ¥0.00`；汇率 7.25 硬编码 | `RssNewsFetcher.parseFeed()`（传入 `0.0`）；`NewsItem.getFormattedPrice()` |
| D3 | ❌ | 截图 `docs/demo.png` 作为当前界面 | 截图显示 Reuters 来源、中英双语摘要、真实感价格，日期 2026-04-17，来自接入真实 RSS 之前的模拟数据版本。Reuters Fetcher 在提交 80738b3 中删除 | 提交 80738b3 删除 `ReutersNewsFetcher.java`；截图内容 |
| D4 | ⚠️ | 提示音 | 实现为 `java.awt.Toolkit.beep()`，每条命中新闻响一次；未验证在 macOS JavaFX 环境下是否可用 | `MonitorUI.playAlertSound()` |
| D5 | ✅ | 最新消息在上、GMT 时间、Start/Stop、统计面板 | 准确 | `MonitorUI`：排序比较器、`formatGMT()`、`btnStart/btnStop` |

### E. 夸大或无依据的表述

| # | 原文 | 处理 |
|---|------|------|
| E1 | 副标题 "High-Concurrency … System"、"完整展示生产级并发编程架构"、"Production-grade concurrency architecture" | 改为"Java Concurrency Demo"。仓库没有压测数据、并发指标或生产部署证据；每轮只有 3 个抓取任务 |
| E2 | "Lock-free programming with CAS" | 改为"原子类与 ConcurrentHashMap"。限流器的补充用到 `synchronized` |
| E3 | "Backpressure handling & peak shaving" | 改为"有界队列"，并写明 1 秒超时丢弃 |
| E4 | "Custom ThreadPool tuning … 调优" | 改为"自定义线程池配置"。没有调优或测量依据 |
| E5 | "Rate limiting with Token Bucket algorithm" | 改为"基于 Semaphore 的并发数限制器"，并说明 QPS 未生效 |

## 3. 修改前后对照（摘要）

| 位置 | 修改前 | 修改后 |
|------|--------|--------|
| 副标题 | High-Concurrency Brent Crude News Monitoring System | Brent Crude News Monitoring Desktop App — a Java Concurrency Demo |
| 概述 | "展示生产级并发编程能力" | "个人学习/面试演示项目"，并声明无压测/生产证据 |
| 运行命令 | `java -jar target/brent-monitor.jar` | `java -jar target/brent-monitor-shaded.jar` |
| 路径 | `Brent-Demo-New/` | `Brent-Oil-Monitor/` |
| macOS 构建 Step 1 | 安装 JavaFX SDK + `JAVAFX_HOME` | 先 `mvn clean package -DskipTests`，JavaFX 来自 `~/.m2` |
| 手动 jpackage | `brent-monitor.jar` + `$JAVAFX_HOME/lib` | 与 `build-macos-app.sh` 一致的参数 |
| 队列背压 | `put()` 阻塞 | `offer(…, 1s)` 超时丢弃 |
| 令牌桶 | 阻塞限流 QPS | 仅 Semaphore 限制并发，令牌计数不阻塞 |
| 配置 | `MonitorScheduler(intervalMinutes: 0.5, …)` | `MonitorUI.start()` 中 `new MonitorScheduler(30, 3)`，附参数表 |
| 面试要点 | 6 条，含错误说法 | 6 条，按代码行为重写 |

## 4. 新增与删除内容及原因

**新增**
- "Known Issues"：记录 `build.sh`/`run.sh` 的 JAR 名称问题（A2），避免读者误以为构建失败。
- RSS 源表、关键词规则、配置参数表：均由代码直接得出。
- 测试/CI 缺失说明、LICENSE 文件缺失说明、日志位置、价格列恒为 0 的说明、截图过期提示。
- 技术栈补充 `HttpClient`、DOM、Logger。

**删除**
- "快约 10 倍"（C9）、"分段锁"（C8）、"无资源泄漏"（C15）、"ObservableList 内部同步"（C14）：不准确或无依据。
- `JAVAFX_HOME` 步骤（A4）。
- 原 "Interview Highlights" 中错误的 SynchronousQueue 背压说法（C2）。
- 原 README 中的不合法示例代码（C5、C6）。改为文字描述与真实代码片段。

**保留**：章节标题和顺序、中英双语表述、架构图（已修正）、徽章（原文无）、License 小节（加了注释）、`docs/demo.png` 引用。

## 5. 未验证的声明与验证范围

已做的验证：
- 静态阅读全部源码、脚本和 `pom.xml`。
- 在 `$KIROCREW_SCRATCH` 的临时副本中执行 `mvn -o -q package -DskipTests`：成功，只生成 `brent-monitor-shaded.jar`，Manifest `Main-Class: com.brentmonitor.Main`。临时副本已删除，仓库 `git status` 无变化。
- `curl` 访问 3 个 RSS 地址：OilPrice.com 200、FX Empire 200、Bloomberg 301 → 200（`https://www.bloomberg.com/feeds/markets/news.rss`）。仅检查 HTTP 状态，未检查内容格式。
- `mvn help:evaluate -Dexpression=settings.localRepository` 可正常返回 `~/.m2/repository`。

**未验证（README_REVISED.md 中已按保守方式表述）：**
1. `java -jar target/brent-monitor-shaded.jar` 能否正常启动 GUI（未启动 GUI）。
2. `build-macos-app.sh` 的 jpackage、codesign、hdiutil 流程，以及仓库中已提交的 `dist/BrentMonitor.dmg` 内容。
3. 提示音在 macOS + JavaFX 下是否可用。
4. 关闭时 `NewsProcessor` 等满 3 秒的推断（C15）和面板 `Fetched` 滞后（D1），均来自代码逻辑推断，未运行验证。
5. 各 RSS 源的内容格式和长期可用性。
6. Windows/Linux 上是否可运行：脚本是 macOS 取向（`jpackage` 参数、`hdiutil`），未验证。
7. "Maven 3.8+" 是否真的是最低版本：`pom.xml` 未限定，审查环境为 Maven 3.10.0。

## 6. 剩余风险与代码层面的观察（未修改代码）

这些是审查中发现的代码问题，按要求没有改动，只在 README 中如实描述：

1. `build.sh`/`run.sh` 的 JAR 名称与 `pom.xml` 不一致（A1、A2）。修复方式是把脚本中的文件名改成 `brent-monitor-shaded.jar`，或把 shade 的 `finalName` 去掉。
2. `TokenBucketRateLimiter` 的令牌计数不参与阻塞（C3）。
3. 队列满时静默丢弃新闻，且无日志（C1）。
4. 去重缓存无界、无淘汰，指纹为 32 位 hash（C10、C11）。
5. `ThreadPoolExecutor` 的拒绝策略只打印日志，被拒绝的 `CompletableFuture` 不会完成，`allOf().join()` 会一直阻塞。当前每轮只提交 3 个任务，通常不会触发，但增加数据源或出现线程抖动时有风险（静态推断）。
6. `pom.xml` 把 `maven-compiler-plugin`、`maven-shade-plugin` 写在 `<dependencies>` 中，会被打进 shaded JAR，属于配置异常。
7. `exec-maven-plugin` 使用 `rm` 命令，在 Windows 上不可用。
8. `NewsItem.contentHash` 的注释写"前 100 字符"，代码用整个 content；`DeduplicationCache` 的类注释称其为"Bloom Filter 的简化实现"，实际是 `ConcurrentHashMap`；`MonitorScheduler` 的注释称参数单位是"分钟"，实际是秒；`MonitorUI` 注释"10分钟间隔，5秒初始延迟"与代码 `(30, 3)` 不符。这些源码注释都已过时。
9. 没有 `LICENSE` 文件，却在 README 中声明 MIT。
10. `src/main/resources/application.css`、`BrentMonitor.icns`、`icon/*.png` 未被引用。
11. 无自动化测试，无法证明任何并发行为的正确性。
