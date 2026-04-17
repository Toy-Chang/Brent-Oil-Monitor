package com.brentmonitor.util;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;

/**
 * 文件日志工具（替代 System.out）
 * macOS GUI 应用的标准输出被系统吃掉了，
 * 所以需要写入用户主目录的日志文件
 */
public final class Logger {
    private static final String LOG_PATH =
        System.getProperty("user.home") + "/BrentMonitor.log";
    private static final Object lock = new Object();

    private Logger() {}

    public static void info(String msg) {
        log("[INFO] " + msg);
    }

    public static void error(String msg) {
        log("[ERROR] " + msg);
    }

    public static void error(String msg, Throwable t) {
        log("[ERROR] " + msg + ": " + t.getMessage());
    }

    private static void log(String line) {
        String ts = "[" + LocalDateTime.now() + "] " + line;
        System.out.println(ts);  // 控制台（开发时可见）
        synchronized (lock) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_PATH, true))) {
                pw.println(ts);
            } catch (Exception ignored) {}
        }
    }
}
