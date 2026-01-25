package com.crawler.api;

import com.crawler.config.CrawlerConfig;
import com.crawler.core.CrawlerEngine;
import com.crawler.db.DatabaseManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HealthServer API endpoints.
 */
class HealthServerTest {

    @TempDir
    Path tempDir;

    private static final int TEST_PORT = 18080;

    private DatabaseManager dbManager;
    private CrawlerEngine engine;
    private HealthServer server;

    @BeforeEach
    void setUp() throws Exception {
        CrawlerConfig config = new CrawlerConfig();
        config.setDatabasePath(tempDir.resolve("test.db").toString());
        config.setThreadCount(2);
        config.setMaxConnections(5);
        config.setMaxPages(10);

        dbManager = new DatabaseManager(config.getDatabasePath());
        dbManager.initialize();

        engine = new CrawlerEngine(config, dbManager);
        server = new HealthServer(engine, TEST_PORT);
        server.start();

        // Give server time to start
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (engine != null) {
            engine.stop();
        }
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("GET /health should return healthy status")
    void healthEndpointShouldReturnHealthy() throws IOException {
        String response = httpGet("/health");

        assertTrue(response.contains("\"status\": \"healthy\""));
        assertTrue(response.contains("\"service\": \"concurrent-web-crawler\""));
        assertTrue(response.contains("\"version\": \"1.0.0\""));
    }

    @Test
    @DisplayName("GET /status should return crawler status")
    void statusEndpointShouldReturnStatus() throws IOException {
        String response = httpGet("/status");

        assertTrue(response.contains("\"status\":"));
        assertTrue(response.contains("\"pagesProcessed\":"));
        assertTrue(response.contains("\"errors\":"));
        assertTrue(response.contains("\"frontierSize\":"));
    }

    @Test
    @DisplayName("GET /metrics should return JSON metrics")
    void metricsEndpointShouldReturnMetrics() throws IOException {
        String response = httpGet("/metrics");

        assertTrue(response.contains("\"pagesProcessed\":"));
        assertTrue(response.contains("\"bytesDownloaded\":"));
        assertTrue(response.contains("\"errors\":"));
        assertTrue(response.contains("\"elapsedTimeMs\":"));
    }

    @Test
    @DisplayName("GET /search without query should return error")
    void searchWithoutQueryShouldReturnError() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + TEST_PORT + "/search"
        ).openConnection();
        conn.setRequestMethod("GET");

        assertEquals(400, conn.getResponseCode());
    }

    @Test
    @DisplayName("GET /search with query should return results")
    void searchWithQueryShouldReturnResults() throws IOException {
        String response = httpGet("/search?q=test&limit=5");

        assertTrue(response.contains("\"query\": \"test\""));
        assertTrue(response.contains("\"count\":"));
        assertTrue(response.contains("\"results\":"));
    }

    @Test
    @DisplayName("POST to /health should return 405")
    void postToHealthShouldReturn405() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + TEST_PORT + "/health"
        ).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        assertEquals(405, conn.getResponseCode());
    }

    @Test
    @DisplayName("Response should have correct Content-Type")
    void responseShouldHaveJsonContentType() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + TEST_PORT + "/health"
        ).openConnection();
        conn.setRequestMethod("GET");

        String contentType = conn.getContentType();
        assertTrue(contentType.contains("application/json"));
    }

    @Test
    @DisplayName("Response should have CORS header")
    void responseShouldHaveCorsHeader() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + TEST_PORT + "/health"
        ).openConnection();
        conn.setRequestMethod("GET");
        conn.getInputStream();

        String corsHeader = conn.getHeaderField("Access-Control-Allow-Origin");
        assertEquals("*", corsHeader);
    }

    private String httpGet(String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + TEST_PORT + path
        ).openConnection();
        conn.setRequestMethod("GET");

        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
