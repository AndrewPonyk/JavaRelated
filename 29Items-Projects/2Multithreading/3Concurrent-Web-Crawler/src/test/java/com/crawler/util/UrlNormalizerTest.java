package com.crawler.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UrlNormalizer.
 */
class UrlNormalizerTest {

    @Test
    @DisplayName("Should normalize basic URL")
    void shouldNormalizeBasicUrl() {
        String result = UrlNormalizer.normalize("HTTPS://EXAMPLE.COM/Page");
        assertEquals("https://example.com/Page", result);
    }

    @Test
    @DisplayName("Should remove default HTTP port")
    void shouldRemoveDefaultHttpPort() {
        String result = UrlNormalizer.normalize("http://example.com:80/page");
        assertEquals("http://example.com/page", result);
    }

    @Test
    @DisplayName("Should remove default HTTPS port")
    void shouldRemoveDefaultHttpsPort() {
        String result = UrlNormalizer.normalize("https://example.com:443/page");
        assertEquals("https://example.com/page", result);
    }

    @Test
    @DisplayName("Should keep non-default port")
    void shouldKeepNonDefaultPort() {
        String result = UrlNormalizer.normalize("https://example.com:8080/page");
        assertEquals("https://example.com:8080/page", result);
    }

    @Test
    @DisplayName("Should add trailing slash for root")
    void shouldAddTrailingSlashForRoot() {
        String result = UrlNormalizer.normalize("https://example.com");
        assertEquals("https://example.com/", result);
    }

    @Test
    @DisplayName("Should remove fragment")
    void shouldRemoveFragment() {
        String result = UrlNormalizer.normalize("https://example.com/page#section");
        assertEquals("https://example.com/page", result);
    }

    @Test
    @DisplayName("Should sort query parameters")
    void shouldSortQueryParameters() {
        String result = UrlNormalizer.normalize("https://example.com/page?z=1&a=2&m=3");
        assertEquals("https://example.com/page?a=2&m=3&z=1", result);
    }

    @Test
    @DisplayName("Should remove tracking parameters")
    void shouldRemoveTrackingParameters() {
        String result = UrlNormalizer.normalize(
            "https://example.com/page?id=1&utm_source=google&utm_medium=cpc"
        );
        assertEquals("https://example.com/page?id=1", result);
    }

    @Test
    @DisplayName("Should resolve path segments")
    void shouldResolvePathSegments() {
        String result = UrlNormalizer.normalize("https://example.com/a/b/../c/./d");
        assertEquals("https://example.com/a/c/d", result);
    }

    @Test
    @DisplayName("Should remove multiple slashes")
    void shouldRemoveMultipleSlashes() {
        String result = UrlNormalizer.normalize("https://example.com//a///b//c");
        assertEquals("https://example.com/a/b/c", result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://example.com/file",
        "mailto:test@example.com",
        "javascript:void(0)",
        "data:text/html,Hello"
    })
    @DisplayName("Should reject non-HTTP URLs")
    void shouldRejectNonHttpUrls(String url) {
        assertNull(UrlNormalizer.normalize(url));
    }

    @Test
    @DisplayName("Should return null for invalid URL")
    void shouldReturnNullForInvalidUrl() {
        assertNull(UrlNormalizer.normalize("not-a-url"));
        assertNull(UrlNormalizer.normalize(""));
        assertNull(UrlNormalizer.normalize(null));
    }

    @Test
    @DisplayName("Should extract domain from URL")
    void shouldExtractDomain() {
        assertEquals("example.com", UrlNormalizer.extractDomain("https://example.com/page"));
        assertEquals("sub.example.com", UrlNormalizer.extractDomain("https://sub.example.com/page"));
    }

    @ParameterizedTest
    @CsvSource({
        "https://example.com/page, https://example.com/page, true",
        "http://example.com/page, HTTP://EXAMPLE.COM/page, true",
        "https://example.com/page?a=1&b=2, https://example.com/page?b=2&a=1, true",
        "https://example.com/page#section, https://example.com/page, true",
        "https://example.com/page, https://other.com/page, false"
    })
    @DisplayName("Should check URL equivalence")
    void shouldCheckUrlEquivalence(String url1, String url2, boolean expected) {
        assertEquals(expected, UrlNormalizer.areEquivalent(url1, url2));
    }

    @Test
    @DisplayName("Should handle URL encoded characters")
    void shouldHandleUrlEncodedCharacters() {
        String result = UrlNormalizer.normalize("https://example.com/path%20with%20spaces");
        assertNotNull(result);
        assertTrue(result.contains("example.com"));
    }

    @Test
    @DisplayName("Should preserve case in path")
    void shouldPreserveCaseInPath() {
        String result = UrlNormalizer.normalize("https://example.com/CamelCase/Path");
        assertTrue(result.contains("CamelCase"));
    }

    @Test
    @DisplayName("Should handle empty path")
    void shouldHandleEmptyPath() {
        String result = UrlNormalizer.normalize("https://example.com?query=value");
        assertEquals("https://example.com/?query=value", result);
    }
}
