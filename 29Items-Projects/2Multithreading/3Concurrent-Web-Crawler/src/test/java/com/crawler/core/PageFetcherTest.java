package com.crawler.core;

import com.crawler.config.CrawlerConfig;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageFetcher.
 * Integration tests with WireMock can be added for HTTP mocking.
 */
class PageFetcherTest {

    private CrawlerConfig config;
    private PageFetcher fetcher;

    @BeforeEach
    void setUp() {
        config = new CrawlerConfig();
        config.setRequestTimeoutMs(5000);
        config.setMaxRetries(2);
        config.setRetryBaseDelayMs(100);
        config.setUserAgent("TestCrawler/1.0");

        fetcher = new PageFetcher(config);
    }

    @Test
    @DisplayName("Should create fetcher with config")
    void shouldCreateFetcherWithConfig() {
        assertNotNull(fetcher);
    }

    @Test
    @DisplayName("Should throw exception for invalid URL")
    void shouldThrowExceptionForInvalidUrl() {
        assertThrows(Exception.class, () -> fetcher.fetch("not-a-valid-url"));
    }

    @Test
    @DisplayName("Should throw exception for non-existent domain")
    void shouldThrowExceptionForNonExistentDomain() {
        assertThrows(Exception.class, () ->
            fetcher.fetch("https://this-domain-definitely-does-not-exist-xyz123.com/page")
        );
    }

    @Test
    @Disabled("Requires network access")
    @DisplayName("Should fetch real page successfully")
    void shouldFetchRealPage() throws Exception {
        PageFetcher.FetchResult result = fetcher.fetch("https://httpbin.org/html");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(200, result.statusCode());
        assertNotNull(result.document());
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @Disabled("Requires network access")
    @DisplayName("Should handle 404 response")
    void shouldHandle404Response() throws Exception {
        PageFetcher.FetchResult result = fetcher.fetch("https://httpbin.org/status/404");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(404, result.statusCode());
    }
}
