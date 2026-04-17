package com.brentmonitor;

import com.brentmonitor.ui.MonitorUI;
import com.brentmonitor.util.Logger;
import javafx.application.Application;

/**
 * 应用入口（Main）
 *
 * 启动流程：
 * 1. 启动 JavaFX Application Thread
 * 2. MonitorUI.start() → 初始化调度器 → 创建 GUI
 * 3. 用户点击 Start → MonitorScheduler.start() → 定时任务开始
 *
 * 模块依赖关系：
 * Main → MonitorUI → MonitorScheduler → FetcherRunner + NewsProcessor
 *                                              ↓
 *                                        NewsItemQueue ← 共享队列
 *                                              ↓
 *                                        NewsProcessor
 *
 * 模块职责：
 * - MonitorScheduler：定时调度 + 协调
 * - FetcherRunner：并发抓取（生产者）
 * - NewsProcessor：数据处理（消费者）
 * - NewsItemQueue：生产者-消费者共享队列
 */
public class Main {

    public static void main(String[] args) {
        Logger.info("========================================");
        Logger.info("  Brent Oil Monitor - 高并发监控系统");
        Logger.info("  Java Version: " + System.getProperty("java.version"));
        Logger.info("  CPU Cores: " + Runtime.getRuntime().availableProcessors());
        Logger.info("========================================");

        // 启动 JavaFX 应用
        Application.launch(MonitorUI.class, args);
    }
}
