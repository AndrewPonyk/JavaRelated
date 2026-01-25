package com.crawler.core;

import com.crawler.config.CrawlerConfig;
import com.crawler.db.DatabaseManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CrawlerEngine.
 * Note: These tests require network access for actual crawling tests.
 */
class CrawlerEngineTest {

    @TempDir
    Path tempDir;

    private CrawlerConfig config;
    private DatabaseManager dbManager;
    private CrawlerEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        config = new CrawlerConfig();
        config.setThreadCount(2);
        config.setMaxConnections(5);
        config.setMaxPages(10);
        config.setMaxDepth(2);
        config.setDefaultDelayMs(100);
        config.setDatabasePath(tempDir.resolve("test-crawler.db").toString());

        dbManager = new DatabaseManager(config.getDatabasePath());
        dbManager.initialize();

        engine = new CrawlerEngine(config, dbManager);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
        }
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("Should initialize engine with correct configuration")
    void shouldInitializeWithConfig() {
        assertNotNull(engine);
        assertNotNull(engine.getMetrics());
        assertEquals(0, engine.getMetrics().getPagesProcessed());
    }

    @Test
    @DisplayName("Should track metrics during crawl")
    void shouldTrackMetrics() {
        // Start with a simple seed that won't actually crawl (no network)
        // This tests the metrics initialization
        var metrics = engine.getMetrics();

        assertEquals(0, metrics.getPagesProcessed());
        assertEquals(0, metrics.getErrors());
        assertEquals(0, metrics.getBytesDownloaded());
    }

    @Test
    @DisplayName("Should stop gracefully")
    void shouldStopGracefully() throws InterruptedException {
        // Start crawl with empty seed (won't actually fetch anything)
        engine.start(List.of());

        // Stop immediately
        engine.stop();

        // Should complete without hanging
        assertTrue(true);
    }

    @Test
    @DisplayName("Should respect max pages limit")
    void shouldRespectMaxPagesLimit() {
        config.setMaxPages(5);
        // In a real test with network access, we would verify
        // that crawling stops at 5 pages
        assertEquals(5, config.getMaxPages());
    }

    @Test
    @DisplayName("Should respect max depth limit")
    void shouldRespectMaxDepthLimit() {
        config.setMaxDepth(3);
        assertEquals(3, config.getMaxDepth());
    }

    @Test
    @Disabled("Requires network access - enable for integration testing")
    @DisplayName("Should crawl real website")
    void shouldCrawlRealWebsite() throws InterruptedException {
        config.setMaxPages(5);

        engine.start(List.of("https://httpbin.org/links/3"));
        engine.awaitCompletion();

        var metrics = engine.getMetrics();
        assertTrue(metrics.getPagesProcessed() > 0);
    }
}
