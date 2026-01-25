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

    // |su:17 CORE COMPONENTS - each handles a specific crawling responsibility
    private final UrlFrontier frontier; // |su:18 URL queue with deduplication (ConcurrentHashMap)
    private final PageFetcher pageFetcher; // |su:19 HTTP client with retry logic
    private final LinkExtractor linkExtractor; // |su:20 Discovers new URLs from HTML
    private final ContentProcessor contentProcessor; // |su:21 Extracts text, computes hashes, indexes
    private final RobotsTxtCache robotsCache; // |su:22 Caches robots.txt rules per domain
    private final RateLimiter rateLimiter; // |su:23 Per-domain throttling with ReentrantLock
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

        // |su:24 ThreadPoolExecutor: fixed thread pool with bounded queue prevents memory exhaustion
        this.executorService = new ThreadPoolExecutor(
                config.getThreadCount(), // core pool size
                config.getThreadCount(), // max pool size (same = fixed)
                60L, TimeUnit.SECONDS, // idle thread timeout
                new LinkedBlockingQueue<>(config.getMaxPages()), // bounded work queue
                new ThreadPoolExecutor.CallerRunsPolicy() // |su:25 CallerRunsPolicy: backpressure - if queue full, caller thread runs task
        );

        // |su:26 Semaphore(permits, fair): fair=true ensures FIFO ordering of waiting threads
        this.connectionSemaphore = new Semaphore(config.getMaxConnections(), true);

        // |su:27 Phaser: advanced sync barrier - threads register, arrive, wait for others
        this.phaser = new Phaser(1); // Main thread is initial party (participant)

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

    private void crawlLoop() { // |su:28 Main loop: runs in separate thread, processes URLs in phases
        try {
            int phase = 0;
            while (!stopped.get() && metrics.getPagesProcessed() < config.getMaxPages()) {
                logger.info("Starting phase {}", phase);

                // |su:29 Inner loop: drain frontier, submit tasks to thread pool
                while (!stopped.get() && !frontier.isEmpty() &&
                        metrics.getPagesProcessed() < config.getMaxPages()) {

                    UrlFrontier.CrawlUrl crawlUrl = frontier.poll(); // |su:30 poll() with timeout - non-blocking dequeue
                    if (crawlUrl == null) {
                        break;
                    }

                    // Check depth limit
                    if (crawlUrl.depth() > config.getMaxDepth()) {
                        continue;
                    }

                    // |su:31 Phaser register: each task becomes a party that must arrive before phase ends
                    phaser.register();
                    executorService.submit(new CrawlTask( // |su:32 Submit task to thread pool for async execution
                            crawlUrl.url(),
                            crawlUrl.depth(),
                            this
                    ));
                }

                // |su:33 arriveAndAwaitAdvance(): block until ALL registered parties arrive (phase barrier)
                phaser.arriveAndAwaitAdvance();
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
            running.set(false);
            completionLatch.countDown();
            logger.info("Crawl finished. {}", metrics);
        }
    }

    /**
     * Process a single URL. Called by CrawlTask.
     */
    void processUrl(String url, int depth) { // |su:34 Single URL processing - called by CrawlTask on worker thread
        String domain = extractDomain(url);

        try {
            // |su:35 STEP 1: Check robots.txt rules BEFORE fetching
            if (config.isRespectRobotsTxt() && !robotsCache.isAllowed(url)) {
                logger.debug("URL blocked by robots.txt: {}", url);
                metrics.recordRobotsBlocked();
                return;
            }

            // |su:36 STEP 2: Acquire semaphore permit - blocks if at max connections
            connectionSemaphore.acquire(); // blocks until permit available
            try {
                // |su:37 STEP 3: Rate limit - wait if too soon since last request to this domain
                rateLimiter.waitForPermit(domain);

                // |su:38 STEP 4: HTTP fetch with auto-retry on 5xx/timeout
                PageFetcher.FetchResult result = pageFetcher.fetch(url);
                metrics.recordPage(domain, result.statusCode(), result.contentLength());

                if (result.isSuccess()) {
                    // |su:39 STEP 5: Extract links from HTML, add to frontier for future crawling
                    List<String> links = linkExtractor.extract(result.document(), url);
                    for (String link : links) {
                        frontier.add(link, depth + 1); // depth+1 tracks distance from seed
                    }

                    // |su:40 STEP 6: ML processing - text extraction, TF-IDF, relevance scoring
                    contentProcessor.process(result.document(), url);

                    // |su:41 STEP 7: Persist to SQLite database
                    pageRepository.save(url, result.document(), result.statusCode());
                }
            } finally {
                connectionSemaphore.release(); // |su:42 ALWAYS release permit in finally block
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Crawl task interrupted for URL: {}", url);
        } catch (Exception e) {
            logger.error("Error processing URL: {}", url, e);
            metrics.recordError();
        } finally {
            phaser.arriveAndDeregister(); // |su:43 Phaser: signal task done + unregister from phase
        }
    }

    /**
     * Stop the crawler gracefully.
     */
    public void stop() {
        logger.info("Stopping crawler...");
        stopped.set(true);
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
