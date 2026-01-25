package com.crawler.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigLoader.
 */
class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should load default configuration")
    void shouldLoadDefaultConfiguration() {
        CrawlerConfig config = ConfigLoader.load();

        assertNotNull(config);
        assertTrue(config.getThreadCount() > 0);
        assertTrue(config.getMaxConnections() > 0);
        assertNotNull(config.getUserAgent());
    }

    @Test
    @DisplayName("Should load configuration from classpath")
    void shouldLoadFromClasspath() {
        CrawlerConfig config = ConfigLoader.load();

        // Values from application.properties on classpath
        assertNotNull(config.getDatabasePath());
        assertTrue(config.isRespectRobotsTxt());
    }

    @Test
    @DisplayName("Should load configuration from external file")
    void shouldLoadFromExternalFile() throws IOException {
        // Create a test config file
        Path configFile = tempDir.resolve("test-config.properties");
        Files.writeString(configFile, """
            crawler.threads=42
            crawler.maxPages=999
            crawler.userAgent=TestBot/1.0
            db.path=/tmp/test.db
            """);

        CrawlerConfig config = ConfigLoader.load(configFile.toString());

        assertEquals(42, config.getThreadCount());
        assertEquals(999, config.getMaxPages());
        assertEquals("TestBot/1.0", config.getUserAgent());
        assertEquals("/tmp/test.db", config.getDatabasePath());
    }

    @Test
    @DisplayName("Should handle non-existent config file gracefully")
    void shouldHandleNonExistentFile() {
        // Should not throw, just use defaults
        CrawlerConfig config = ConfigLoader.load("/non/existent/path.properties");

        assertNotNull(config);
        assertTrue(config.getThreadCount() > 0);
    }

    @Test
    @DisplayName("Should parse all config properties")
    void shouldParseAllProperties() throws IOException {
        Path configFile = tempDir.resolve("full-config.properties");
        Files.writeString(configFile, """
            crawler.threads=8
            crawler.maxConnections=16
            crawler.maxPages=5000
            crawler.maxDepth=5
            crawler.defaultDelayMs=500
            crawler.requestTimeoutMs=10000
            crawler.userAgent=CustomAgent/2.0
            crawler.respectRobotsTxt=false
            crawler.maxRetries=5
            crawler.allowedDomains=example.com,test.org
            crawler.blockedDomains=spam.com,ads.net
            db.path=/custom/db.sqlite
            """);

        CrawlerConfig config = ConfigLoader.load(configFile.toString());

        assertEquals(8, config.getThreadCount());
        assertEquals(16, config.getMaxConnections());
        assertEquals(5000, config.getMaxPages());
        assertEquals(5, config.getMaxDepth());
        assertEquals(500, config.getDefaultDelayMs());
        assertEquals(10000, config.getRequestTimeoutMs());
        assertEquals("CustomAgent/2.0", config.getUserAgent());
        assertFalse(config.isRespectRobotsTxt());
        assertEquals(5, config.getMaxRetries());
        assertNotNull(config.getAllowedDomains());
        assertEquals(2, config.getAllowedDomains().length);
        assertNotNull(config.getBlockedDomains());
        assertEquals(2, config.getBlockedDomains().length);
        assertEquals("/custom/db.sqlite", config.getDatabasePath());
    }

    @Test
    @DisplayName("Should handle empty allowed domains")
    void shouldHandleEmptyAllowedDomains() throws IOException {
        Path configFile = tempDir.resolve("empty-domains.properties");
        Files.writeString(configFile, """
            crawler.allowedDomains=
            crawler.blockedDomains=
            """);

        CrawlerConfig config = ConfigLoader.load(configFile.toString());

        // Empty strings should not create arrays
        assertNull(config.getAllowedDomains());
        assertNull(config.getBlockedDomains());
    }

    @Test
    @DisplayName("Should handle partial configuration")
    void shouldHandlePartialConfiguration() throws IOException {
        Path configFile = tempDir.resolve("partial-config.properties");
        Files.writeString(configFile, """
            crawler.threads=20
            """);

        CrawlerConfig config = ConfigLoader.load(configFile.toString());

        // Specified value
        assertEquals(20, config.getThreadCount());
        // Defaults for unspecified values
        assertTrue(config.getMaxPages() > 0);
        assertNotNull(config.getUserAgent());
    }
}
