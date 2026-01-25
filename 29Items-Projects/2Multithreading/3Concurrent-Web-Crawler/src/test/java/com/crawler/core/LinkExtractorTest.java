package com.crawler.core;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LinkExtractor.
 */
class LinkExtractorTest {

    private LinkExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new LinkExtractor();
    }

    @Test
    @DisplayName("Should extract links from HTML")
    void shouldExtractLinks() {
        String html = """
            <html>
            <body>
                <a href="https://example.com/page1">Link 1</a>
                <a href="https://example.com/page2">Link 2</a>
                <a href="https://other.com/page">External Link</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        assertEquals(3, links.size());
    }

    @Test
    @DisplayName("Should resolve relative links")
    void shouldResolveRelativeLinks() {
        String html = """
            <html>
            <body>
                <a href="/page1">Page 1</a>
                <a href="page2">Page 2</a>
                <a href="../parent">Parent</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/folder/");

        List<String> links = extractor.extract(doc, "https://example.com/folder/");

        assertTrue(links.stream().anyMatch(l -> l.contains("example.com")));
    }

    @Test
    @DisplayName("Should filter out mailto links")
    void shouldFilterMailtoLinks() {
        String html = """
            <html>
            <body>
                <a href="https://example.com/page">Valid</a>
                <a href="mailto:test@example.com">Email</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        assertEquals(1, links.size());
        assertFalse(links.get(0).contains("mailto"));
    }

    @Test
    @DisplayName("Should filter out javascript links")
    void shouldFilterJavascriptLinks() {
        String html = """
            <html>
            <body>
                <a href="https://example.com/page">Valid</a>
                <a href="javascript:void(0)">JS Link</a>
                <a href="javascript:alert('hi')">Alert</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        assertEquals(1, links.size());
        assertFalse(links.get(0).contains("javascript"));
    }

    @Test
    @DisplayName("Should filter out tel links")
    void shouldFilterTelLinks() {
        String html = """
            <html>
            <body>
                <a href="https://example.com/page">Valid</a>
                <a href="tel:+1234567890">Phone</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        assertEquals(1, links.size());
    }

    @Test
    @DisplayName("Should handle null document")
    void shouldHandleNullDocument() {
        List<String> links = extractor.extract(null, "https://example.com/");
        assertTrue(links.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty document")
    void shouldHandleEmptyDocument() {
        Document doc = Jsoup.parse("<html><body></body></html>", "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        assertTrue(links.isEmpty());
    }

    @Test
    @DisplayName("Should extract links for specific domain")
    void shouldExtractLinksForDomain() {
        String html = """
            <html>
            <body>
                <a href="https://example.com/page1">Same domain</a>
                <a href="https://sub.example.com/page2">Subdomain</a>
                <a href="https://other.com/page">Other domain</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extractForDomain(doc, "https://example.com/", "example.com");

        assertEquals(2, links.size());
        assertTrue(links.stream().allMatch(l -> l.contains("example.com")));
    }

    @Test
    @DisplayName("Should handle anchors without href")
    void shouldHandleAnchorsWithoutHref() {
        String html = """
            <html>
            <body>
                <a name="section1">Section</a>
                <a href="https://example.com/page">Valid Link</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        assertEquals(1, links.size());
    }

    @Test
    @DisplayName("Should handle multiple anchors to same URL")
    void shouldHandleMultipleAnchorsToSameUrl() {
        String html = """
            <html>
            <body>
                <a href="https://example.com/page">Link 1</a>
                <a href="https://example.com/page">Link 2</a>
                <a href="https://example.com/page">Link 3</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        // All links extracted (deduplication happens in UrlFrontier)
        assertEquals(3, links.size());
    }

    @Test
    @DisplayName("Should handle links with query parameters")
    void shouldHandleLinksWithQueryParams() {
        String html = """
            <html>
            <body>
                <a href="https://example.com/search?q=test&page=1">Search</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        assertEquals(1, links.size());
        assertTrue(links.get(0).contains("q=test"));
    }

    @Test
    @DisplayName("Should handle links with fragments")
    void shouldHandleLinksWithFragments() {
        String html = """
            <html>
            <body>
                <a href="https://example.com/page#section">Link with fragment</a>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html, "https://example.com/");

        List<String> links = extractor.extract(doc, "https://example.com/");

        // Fragments may or may not be filtered depending on implementation
        assertEquals(1, links.size());
    }
}
