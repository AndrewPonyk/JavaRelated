package com.crawler.core;

import com.crawler.config.CrawlerConfig;
import com.crawler.db.CrawlStateRepository;
import com.crawler.db.DatabaseManager;
import com.crawler.db.PageRepository;
import com.crawler.ml.ContentIndexer;
import com.crawler.robots.RateLimiter;
import com.crawler.robots.RobotsTxtCache;
import com.crawler.util.CrawlMetrics;
import com.crawler.util.DomainExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main orchestrator for the web crawling process.
 *
 * <p>Coordinates:
 * <ul>
 *   <li>ExecutorService thread pool for parallel crawling</li>
 *   <li>Semaphore for connection limiting</li>
 *   <li>Phaser for multi-phase crawl coordination</li>
 *   <li>URL frontier management</li>
 * </ul>
 */
public class CrawlerEngine { // |su:12 CORE: Main orchestrator coordinating all crawling operations

    private static final Logger logger = LoggerFactory.getLogger(CrawlerEngine.class);

    private final CrawlerConfig config;
    private final DatabaseManager dbManager;

    // |su:13 CONCURRENCY PRIMITIVES - the heart of multi-threaded crawling
    private final ExecutorService executorService; // |su:14 Thread pool - fixed N workers process URLs in parallel
    private final Semaphore connectionSemaphore; // |su:15 Semaphore - limits concurrent HTTP connections (prevents server overload)
    private final Phaser phaser; // |su:16 Phaser - synchronizes crawl phases (all workers finish before next phase)

    // Core components
    private final UrlFrontier frontier;
    private final PageFetcher pageFetcher;
    private final LinkExtractor linkExtractor;
    private final ContentProcessor contentProcessor;
    private final RobotsTxtCache robotsCache;
    private final RateLimiter rateLimiter;
    private final PageRepository pageRepository;
    private final ContentIndexer contentIndexer;
    private final CrawlMetrics metrics;

    // State
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private CountDownLatch completionLatch;

    public CrawlerEngine(CrawlerConfig config, DatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;

        this.executorService = new ThreadPoolExecutor(
                config.getThreadCount(),
                config.getThreadCount(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getMaxPages()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.connectionSemaphore = new Semaphore(config.getMaxConnections(), true);

        this.phaser = new Phaser(1);

        // Initialize components
        this.frontier = new UrlFrontier(config.getMaxPages());
        this.pageFetcher = new PageFetcher(config);
        this.linkExtractor = new LinkExtractor();
        this.robotsCache = new RobotsTxtCache(config);
        this.rateLimiter = new RateLimiter(config.getDefaultDelayMs());
        this.pageRepository = new PageRepository(dbManager);
        this.contentIndexer = new ContentIndexer(dbManager);
        this.contentProcessor = new ContentProcessor(contentIndexer);
        this.metrics = new CrawlMetrics();
    }

    /**
     * Start the crawl with the given seed URLs.
     *
     * @param seedUrls Initial URLs to begin crawling
     */
    public void start(List<String> seedUrls) {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Crawler is already running");
            return;
        }

        logger.info("Starting crawl with {} seed URLs", seedUrls.size());
        completionLatch = new CountDownLatch(1);

        // Add seed URLs to frontier
        for (String url : seedUrls) {
            frontier.add(url, 0);
        }

        // Start crawl loop in separate thread
        Thread crawlThread = new Thread(this::crawlLoop, "crawler-main");
        crawlThread.start();
    }

    private void crawlLoop() {
        try {
            int phase = 0;
            int batchSize = 100; // Submit tasks in smaller batches for better control

            while (!stopped.get() && metrics.getPagesProcessed() < config.getMaxPages()) {
                logger.info("Starting phase {} (processed: {}/{})",
                        phase, metrics.getPagesProcessed(), config.getMaxPages());

                int submittedInPhase = 0;
                while (!stopped.get() && !frontier.isEmpty() &&
                        metrics.getPagesProcessed() < config.getMaxPages()) {

                    UrlFrontier.CrawlUrl crawlUrl = frontier.poll();
                    if (crawlUrl == null) {
                        break;
                    }

                    // Check depth limit
                    if (crawlUrl.depth() > config.getMaxDepth()) {
                        continue;
                    }

                    phaser.register();
                    executorService.submit(new CrawlTask(
                            crawlUrl.url(),
                            crawlUrl.depth(),
                            this
                    ));
                    submittedInPhase++;

                    // Process in batches - wait for batch to complete, then check stop condition
                    if (submittedInPhase >= batchSize) {
                        break;
                    }
                }

                if (submittedInPhase == 0) {
                    logger.info("No tasks submitted, frontier may be empty");
                    break;
                }

                try {
                    int currentPhase = phaser.getPhase();
                    while (!phaser.isTerminated()) {
                        // Wait max 5 seconds, then check stop condition
                        phaser.awaitAdvanceInterruptibly(currentPhase, 5, TimeUnit.SECONDS);
                        break; // Phase completed
                    }
                } catch (TimeoutException e) {
                    // Check if we should stop (max pages reached)
                    if (metrics.getPagesProcessed() >= config.getMaxPages()) {
                        logger.info("Max pages reached during phase wait, stopping...");
                        break;
                    }
                    // Otherwise continue waiting
                    phaser.arriveAndAwaitAdvance();
                }

                phase++;

                // Check if more URLs were discovered
                if (frontier.isEmpty()) {
                    logger.info("Frontier empty, crawl complete");
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error in crawl loop", e);
        } finally {
            // Wait for all worker threads to complete before scoring
            logger.info("Waiting for worker threads to complete...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("Some workers didn't complete in time");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("All workers completed. Pages in DB: {}", metrics.getPagesProcessed());

            // Pass 2: Recalculate all scores with full corpus (all pages now saved)
            if (!stopped.get() && metrics.getPagesProcessed() > 0) {
                contentIndexer.recalculateAllScores();
            }
            running.set(false);
            completionLatch.countDown();
            logger.info("Crawl finished. {}", metrics);
        }
    }

    /**
     * Process a single URL. Called by CrawlTask.
     */
    void processUrl(String url, int depth) { // |su:17 Single URL processing - called by worker threads
        String domain = extractDomain(url);

        try {
            if (config.isRespectRobotsTxt() && !robotsCache.isAllowed(url)) {
                logger.debug("URL blocked by robots.txt: {}", url);
                metrics.recordRobotsBlocked();
                return;
            }

            connectionSemaphore.acquire(); // |su:18 Acquire permit - blocks if at max connections
            try {
                rateLimiter.waitForPermit(domain);

                PageFetcher.FetchResult result = pageFetcher.fetch(url);
                metrics.recordPage(domain, result.statusCode(), result.contentLength());

                if (result.isSuccess()) {
                    List<String> links = linkExtractor.extract(result.document(), url);
                    int newLinks = 0;
                    for (String link : links) {
                        if (frontier.add(link, depth + 1)) {
                            newLinks++;
                        }
                    }
                    if (newLinks > 0) {
                        logger.debug("Added {} new links (of {} found) from {}", newLinks, links.size(), url);
                    }

                    pageRepository.save(url, result.document(), result.statusCode());

                    contentProcessor.process(result.document(), url);
                }
            } finally {
                connectionSemaphore.release(); // |su:19 ALWAYS release in finally - prevents deadlock
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Crawl task interrupted for URL: {}", url);
        } catch (Exception e) {
            logger.error("Error processing URL: {}", url, e);
            metrics.recordError();
        } finally {
            phaser.arriveAndDeregister(); // |su:20 Signal task done + unregister from phase
        }
    }

    /**
     * Stop the crawler gracefully.
     */
    public void stop() {
        logger.info("Stopping crawler...");
        stopped.set(true);

        // Executor may already be shut down from crawlLoop finally block
        if (!executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Wait for crawl to complete.
     */
    public void awaitCompletion() throws InterruptedException {
        if (completionLatch != null) {
            completionLatch.await();
        }
    }

    /**
     * Get current crawl metrics.
     */
    public CrawlMetrics getMetrics() {
        return metrics;
    }

    private String extractDomain(String url) {
        String domain = DomainExtractor.extractDomain(url);
        return domain != null ? domain : "unknown";
    }

    /**
     * Check if the crawler is currently running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Check if the crawler has been stopped.
     */
    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * Get the URL frontier for status reporting.
     */
    public UrlFrontier getFrontier() {
        return frontier;
    }

    /**
     * Get the robots.txt cache for status reporting.
     */
    public RobotsTxtCache getRobotsCache() {
        return robotsCache;
    }

    /**
     * Get the content indexer for search operations.
     */
    public ContentIndexer getContentIndexer() {
        return contentIndexer;
    }

    /**
     * Get the database manager.
     */
    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    /**
     * Get the configuration.
     */
    public CrawlerConfig getConfig() {
        return config;
    }
}
