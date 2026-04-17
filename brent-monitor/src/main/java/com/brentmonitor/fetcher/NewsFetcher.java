package com.brentmonitor.fetcher;

import com.brentmonitor.model.NewsItem;
import java.util.List;

/**
 * 新闻抓取器接口（Producer 角色）
 *
 * 设计原则：
 * 1. 模拟多个新闻源并发抓取
 * 2. 每个实现类代表一个数据源
 * 3. 线程安全：Fetcher 本身无状态，抓取逻辑由线程池调度执行
 *
 * 生产者职责：
 * - 从外部获取原始数据
 * - 解析为 NewsItem
 * - 放入阻塞队列（NewsItemQueue）
 */
public interface NewsFetcher {

    /**
     * 获取新闻源名称
     */
    String getSourceName();

    /**
     * 执行抓取（可能抛出异常，调用方负责处理）
     * @return 抓取到的新闻列表（不含布伦特关键词的也会返回，由 Processor 过滤）
     */
    List<NewsItem> fetch();
}
