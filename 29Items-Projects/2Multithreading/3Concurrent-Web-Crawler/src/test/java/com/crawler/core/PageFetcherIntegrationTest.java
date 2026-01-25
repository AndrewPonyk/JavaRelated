package com.crawler.core;

import com.crawler.config.CrawlerConfig;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PageFetcher using WireMock.
 */
@WireMockTest
class PageFetcherIntegrationTest {

    private CrawlerConfig config;
    private PageFetcher fetcher;

    @BeforeEach
    void setUp() {
        config = new CrawlerConfig();
        config.setRequestTimeoutMs(5000);
        config.setMaxRetries(2);
        config.setRetryBaseDelayMs(100);
        config.setUserAgent("TestCrawler/1.0");
        config.setMaxBodySizeBytes(1024 * 1024);
        fetcher = new PageFetcher(config);
    }

    @Test
    @DisplayName("Should fetch HTML page successfully")
    void shouldFetchHtmlPage(WireMockRuntimeInfo wmInfo) throws IOException {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/test-page")
            .willReturn(WireMock.ok()
                .withHeader("Content-Type", "text/html; charset=UTF-8")
                .withBody("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>Test Page</title></head>
                    <body>
                        <h1>Hello World</h1>
                        <p>This is a test page.</p>
                        <a href="/link1">Link 1</a>
                        <a href="/link2">Link 2</a>
                    </body>
                    </html>
                    """)));

        String url = wmInfo.getHttpBaseUrl() + "/test-page";
        PageFetcher.FetchResult result = fetcher.fetch(url);

        assertTrue(result.isSuccess());
        assertEquals(200, result.statusCode());
        assertNotNull(result.document());
        assertEquals("Test Page", result.document().title());
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @DisplayName("Should extract links from fetched page")
    void shouldExtractLinks(WireMockRuntimeInfo wmInfo) throws IOException {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/page-with-links")
            .willReturn(WireMock.ok()
                .withHeader("Content-Type", "text/html")
                .withBody("""
                    <html>
                    <body>
                        <a href="/internal">Internal Link</a>
                        <a href="https://external.com/page">External Link</a>
                    </body>
                    </html>
                    """)));

        String url = wmInfo.getHttpBaseUrl() + "/page-with-links";
        PageFetcher.FetchResult result = fetcher.fetch(url);

        assertTrue(result.isSuccess());

        LinkExtractor extractor = new LinkExtractor();
        var links = extractor.extract(result.document(), url);

        assertEquals(2, links.size());
    }

    @Test
    @DisplayName("Should return failure result for 404")
    void shouldHandleNotFound(WireMockRuntimeInfo wmInfo) throws IOException {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/not-found")
            .willReturn(WireMock.notFound()
                .withBody("Page not found")));

        String url = wmInfo.getHttpBaseUrl() + "/not-found";
        PageFetcher.FetchResult result = fetcher.fetch(url);

        assertFalse(result.isSuccess());
        assertEquals(404, result.statusCode());
    }

    @Test
    @DisplayName("Should retry on server error")
    void shouldRetryOnServerError(WireMockRuntimeInfo wmInfo) {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/server-error")
            .willReturn(WireMock.serverError()
                .withBody("Internal Server Error")));

        String url = wmInfo.getHttpBaseUrl() + "/server-error";

        // Should throw after retries exhausted
        assertThrows(IOException.class, () -> fetcher.fetch(url));

        // Verify retry attempts were made
        wireMock.verifyThat(WireMock.moreThanOrExactly(2),
            WireMock.getRequestedFor(WireMock.urlEqualTo("/server-error")));
    }

    @Test
    @DisplayName("Should follow redirects")
    void shouldFollowRedirects(WireMockRuntimeInfo wmInfo) throws IOException {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/redirect")
            .willReturn(WireMock.temporaryRedirect("/final-page")));

        wireMock.register(WireMock.get("/final-page")
            .willReturn(WireMock.ok()
                .withHeader("Content-Type", "text/html")
                .withBody("<html><head><title>Final Page</title></head></html>")));

        String url = wmInfo.getHttpBaseUrl() + "/redirect";
        PageFetcher.FetchResult result = fetcher.fetch(url);

        assertTrue(result.isSuccess());
        assertEquals("Final Page", result.document().title());
    }

    @Test
    @DisplayName("Should handle timeout")
    void shouldHandleTimeout(WireMockRuntimeInfo wmInfo) {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/slow-page")
            .willReturn(WireMock.ok()
                .withFixedDelay(10000)  // 10 second delay
                .withBody("Slow response")));

        String url = wmInfo.getHttpBaseUrl() + "/slow-page";

        // Should throw due to timeout (configured to 5s)
        assertThrows(IOException.class, () -> fetcher.fetch(url));
    }

    @Test
    @DisplayName("Should send correct User-Agent header")
    void shouldSendUserAgent(WireMockRuntimeInfo wmInfo) throws IOException {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/check-ua")
            .willReturn(WireMock.ok()
                .withHeader("Content-Type", "text/html")
                .withBody("<html></html>")));

        String url = wmInfo.getHttpBaseUrl() + "/check-ua";
        fetcher.fetch(url);

        wireMock.verifyThat(WireMock.getRequestedFor(WireMock.urlEqualTo("/check-ua"))
            .withHeader("User-Agent", WireMock.equalTo("TestCrawler/1.0")));
    }

    @Test
    @DisplayName("Should handle empty response")
    void shouldHandleEmptyResponse(WireMockRuntimeInfo wmInfo) throws IOException {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/empty")
            .willReturn(WireMock.ok()
                .withHeader("Content-Type", "text/html")
                .withBody("")));

        String url = wmInfo.getHttpBaseUrl() + "/empty";
        PageFetcher.FetchResult result = fetcher.fetch(url);

        assertTrue(result.isSuccess());
        assertNotNull(result.document());
    }

    @Test
    @DisplayName("Should handle JSON content type")
    void shouldHandleJsonContent(WireMockRuntimeInfo wmInfo) {
        WireMock wireMock = wmInfo.getWireMock();
        wireMock.register(WireMock.get("/api/data")
            .willReturn(WireMock.ok()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"key\": \"value\"}")));

        String url = wmInfo.getHttpBaseUrl() + "/api/data";

        // Jsoup doesn't handle JSON content type - it throws UnsupportedMimeTypeException
        // This is expected behavior - crawlers typically only fetch HTML
        assertThrows(org.jsoup.UnsupportedMimeTypeException.class, () -> fetcher.fetch(url));
    }
}
