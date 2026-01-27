package com.crawler.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a single crawl task for processing one URL.
 * Executed by the thread pool managed by CrawlerEngine.
 */
public class CrawlTask implements Runnable { // |su:134 Work unit: represents one URL to crawl, submitted to thread pool

    private static final Logger logger = LoggerFactory.getLogger(CrawlTask.class);

    private final String url;
    private final int depth;
    private final CrawlerEngine engine;

    /**
     * Create a new crawl task.
     *
     * @param url    The URL to crawl
     * @param depth  Current crawl depth from seed
     * @param engine Reference to the crawler engine for processing
     */
    public CrawlTask(String url, int depth, CrawlerEngine engine) {
        this.url = url;
        this.depth = depth;
        this.engine = engine;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("crawler-" + Thread.currentThread().getId());
        logger.trace("Processing URL at depth {}: {}", depth, url);

        try {
            engine.processUrl(url, depth);
        } catch (Exception e) {
            logger.error("Unexpected error in crawl task for URL: {}", url, e);
        }
    }

    public String getUrl() {
        return url;
    }

    public int getDepth() {
        return depth;
    }
}
