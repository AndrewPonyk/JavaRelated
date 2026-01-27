package com.crawler;

import com.crawler.api.HealthServer;
import com.crawler.config.ConfigLoader;
import com.crawler.config.CrawlerConfig;
import com.crawler.core.CrawlerEngine;
import com.crawler.db.CrawlStateRepository;
import com.crawler.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for the Concurrent Web Crawler application.
 *
 * <p>Usage: java -jar crawler.jar [options] [seed-urls...]
 *
 * <p>Options:
 *   --config=path     Path to configuration file
 *   --max-pages=N     Maximum pages to crawl
 *   --threads=N       Number of worker threads
 *   --port=N          HTTP server port (default: 8080)
 *   --server          Start HTTP server for health/API
 *   --resume          Resume from last saved state
 *   --keywords=k1,k2  Target keywords for relevance scoring
 *   --help            Show help message
 */
public class CrawlerApplication { // |su:1 START HERE - Main entry point. Run: java -jar crawler.jar https://example.com

    private static final Logger logger = LoggerFactory.getLogger(CrawlerApplication.class);

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        logger.info("Starting Concurrent Web Crawler v1.0.0");

        try {
            // Parse command line arguments
            CommandLineArgs cliArgs = parseArgs(args);

            if (cliArgs.showHelp) {
                printHelp();
                return;
            }

            CrawlerConfig config = ConfigLoader.load(cliArgs.configPath);

            // Apply CLI overrides
            applyCliOverrides(config, cliArgs);

            DatabaseManager dbManager = new DatabaseManager(config.getDatabasePath());
            dbManager.initialize();

            CrawlerEngine engine = new CrawlerEngine(config, dbManager);

            // Set target keywords if provided
            if (cliArgs.keywords != null && !cliArgs.keywords.isEmpty()) {
                engine.getContentIndexer().setTargetKeywords(cliArgs.keywords);
                logger.info("Set {} target keywords for relevance scoring", cliArgs.keywords.size());
            }

            // Start health server if requested
            HealthServer healthServer = null;
            if (cliArgs.startServer) {
                int port = cliArgs.port > 0 ? cliArgs.port : getPortFromEnv();
                healthServer = new HealthServer(engine, port);
                healthServer.start();
            }

            // Shutdown hook - ensures clean termination on Ctrl+C
            final HealthServer serverRef = healthServer;
            final AtomicBoolean cleanedUp = new AtomicBoolean(false);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (cleanedUp.get()) {
                    logger.debug("Already cleaned up, skipping shutdown hook");
                    return;
                }
                logger.info("Shutdown signal received, stopping crawler...");
                engine.stop();
                if (serverRef != null) {
                    serverRef.stop();
                }
                saveState(dbManager, engine);
                dbManager.close();
            }));

            // Determine seed URLs
            List<String> seedUrls = cliArgs.seedUrls;

            // Resume from saved state if requested
            if (cliArgs.resume) {
                CrawlStateRepository stateRepo = new CrawlStateRepository(dbManager);
                Optional<CrawlStateRepository.CrawlState> savedState = stateRepo.loadLatestState();
                if (savedState.isPresent()) {
                    logger.info("Resuming from saved state (id={})", savedState.get().id());
                    // Parse frontier from saved state
                    seedUrls = parseFrontierSnapshot(savedState.get().frontierSnapshot());
                    logger.info("Loaded {} URLs from saved frontier", seedUrls.size());
                } else {
                    logger.warn("No saved state found, starting fresh");
                }
            }

            // Start crawling
            if (!seedUrls.isEmpty()) {
                engine.start(seedUrls);
                logger.info("Crawl started with {} seed URLs", seedUrls.size());
            } else if (cliArgs.startServer) {
                // Server mode without crawling - just wait
                logger.info("Running in server-only mode. Use Ctrl+C to stop.");
                Thread.currentThread().join();
            } else {
                logger.error("No seed URLs provided. Use --help for usage information.");
                System.exit(1);
            }

            // Wait for completion if not in server mode
            if (!seedUrls.isEmpty()) {
                engine.awaitCompletion();
                logger.info("Crawl completed. Statistics: {}", engine.getMetrics());

                // Keep server running if started
                if (cliArgs.startServer) {
                    logger.info("Crawl finished. Server still running. Use Ctrl+C to stop.");
                    Thread.currentThread().join();
                } else {
                    // Clean shutdown - stop engine and close DB
                    cleanedUp.set(true);
                    saveState(dbManager, engine);
                    engine.stop();
                    dbManager.close();
                    logger.info("Clean shutdown completed");
                }
            }

        } catch (Exception e) {
            logger.error("Fatal error during crawl", e);
            System.exit(1);
        }
    }

    private static void applyCliOverrides(CrawlerConfig config, CommandLineArgs cliArgs) {
        if (cliArgs.maxPages > 0) {
            config.setMaxPages(cliArgs.maxPages);
        }
        if (cliArgs.threads > 0) {
            config.setThreadCount(cliArgs.threads);
        }
        if (cliArgs.maxDepth > 0) {
            config.setMaxDepth(cliArgs.maxDepth);
        }
        if (cliArgs.delay > 0) {
            config.setDefaultDelayMs(cliArgs.delay);
        }
    }

    private static int getPortFromEnv() {
        String portEnv = System.getenv("PORT");
        if (portEnv != null) {
            try {
                return Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT environment variable: {}", portEnv);
            }
        }
        return DEFAULT_PORT;
    }

    private static void saveState(DatabaseManager dbManager, CrawlerEngine engine) {
        try {
            CrawlStateRepository stateRepo = new CrawlStateRepository(dbManager);
            String frontierSnapshot = serializeFrontier(engine);
            stateRepo.saveState(frontierSnapshot, "{}");
            logger.info("Crawl state saved");
        } catch (Exception e) {
            logger.error("Failed to save crawl state", e);
        }
    }

    private static String serializeFrontier(CrawlerEngine engine) {
        // Simple JSON array of remaining URLs
        StringBuilder json = new StringBuilder("[");
        // Note: In production, we'd iterate the frontier properly
        // For now, return empty since we can't easily serialize the queue
        json.append("]");
        return json.toString();
    }

    private static List<String> parseFrontierSnapshot(String snapshot) {
        List<String> urls = new ArrayList<>();
        if (snapshot == null || snapshot.isBlank() || snapshot.equals("[]")) {
            return urls;
        }
        // Simple JSON array parsing
        snapshot = snapshot.trim();
        if (snapshot.startsWith("[") && snapshot.endsWith("]")) {
            snapshot = snapshot.substring(1, snapshot.length() - 1);
            for (String part : snapshot.split(",")) {
                part = part.trim();
                if (part.startsWith("\"") && part.endsWith("\"")) {
                    urls.add(part.substring(1, part.length() - 1));
                }
            }
        }
        return urls;
    }

    private static CommandLineArgs parseArgs(String[] args) {
        CommandLineArgs cliArgs = new CommandLineArgs();

        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                cliArgs.showHelp = true;
            } else if (arg.startsWith("--config=")) {
                cliArgs.configPath = arg.substring("--config=".length());
            } else if (arg.startsWith("--max-pages=")) {
                cliArgs.maxPages = Integer.parseInt(arg.substring("--max-pages=".length()));
            } else if (arg.startsWith("--threads=")) {
                cliArgs.threads = Integer.parseInt(arg.substring("--threads=".length()));
            } else if (arg.startsWith("--max-depth=")) {
                cliArgs.maxDepth = Integer.parseInt(arg.substring("--max-depth=".length()));
            } else if (arg.startsWith("--delay=")) {
                cliArgs.delay = Long.parseLong(arg.substring("--delay=".length()));
            } else if (arg.startsWith("--port=")) {
                cliArgs.port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.equals("--server")) {
                cliArgs.startServer = true;
            } else if (arg.equals("--resume")) {
                cliArgs.resume = true;
            } else if (arg.startsWith("--keywords=")) {
                String keywords = arg.substring("--keywords=".length());
                cliArgs.keywords = List.of(keywords.split(","));
            } else if (!arg.startsWith("--")) {
                cliArgs.seedUrls.add(arg);
            } else {
                logger.warn("Unknown argument: {}", arg);
            }
        }

        return cliArgs;
    }

    private static void printHelp() {
        System.out.println("""
            Concurrent Web Crawler v1.0.0

            A high-performance web crawler with ML-integrated content indexing.

            USAGE:
                java -jar crawler.jar [OPTIONS] <seed-url> [additional-urls...]

            OPTIONS:
                --config=<path>       Path to configuration file (default: application.properties)
                --max-pages=<N>       Maximum number of pages to crawl (default: 10000)
                --threads=<N>         Number of worker threads (default: 10)
                --max-depth=<N>       Maximum crawl depth (default: 10)
                --delay=<ms>          Delay between requests in ms (default: 1000)
                --port=<N>            HTTP server port (default: 8080 or $PORT)
                --server              Start HTTP server for health checks and API
                --resume              Resume from last saved crawl state
                --keywords=<k1,k2>    Comma-separated keywords for relevance scoring
                --help, -h            Show this help message

            EXAMPLES:
                # Basic crawl
                java -jar crawler.jar https://example.com

                # Crawl with limits
                java -jar crawler.jar --max-pages=100 --threads=5 https://example.com

                # Start with HTTP server for monitoring
                java -jar crawler.jar --server --max-pages=1000 https://example.com

                # Resume previous crawl
                java -jar crawler.jar --resume

                # Crawl with relevance keywords
                java -jar crawler.jar --keywords=java,concurrency https://docs.oracle.com

            ENVIRONMENT VARIABLES:
                CRAWLER_THREADS           Worker thread count
                CRAWLER_MAX_CONNECTIONS   Maximum concurrent connections (semaphore permits)
                CRAWLER_DEFAULT_DELAY_MS  Default delay between requests
                CRAWLER_MAX_PAGES         Maximum pages to crawl
                CRAWLER_USER_AGENT        User-Agent header string
                DB_PATH                   SQLite database path
                PORT                      HTTP server port (Railway sets this automatically)
                LOG_LEVEL                 Logging level (TRACE, DEBUG, INFO, WARN, ERROR)

            HTTP API ENDPOINTS (when --server is used):
                GET /health             Health check (for Railway/load balancers)
                GET /status             Detailed crawler status
                GET /metrics            Raw metrics as JSON
                GET /search?q=<query>   Search indexed content

            For more information, see: https://github.com/example/concurrent-web-crawler
            """);
    }

    private static class CommandLineArgs {
        boolean showHelp = false;
        boolean startServer = false;
        boolean resume = false;
        String configPath = null;
        int maxPages = -1;
        int threads = -1;
        int maxDepth = -1;
        long delay = -1;
        int port = -1;
        List<String> keywords = null;
        List<String> seedUrls = new ArrayList<>();
    }
}
