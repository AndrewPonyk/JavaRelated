package com.crawler.robots;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RobotsTxtParser.
 */
class RobotsTxtParserTest {

    private RobotsTxtParser parser;
    private static final String USER_AGENT = "ConcurrentCrawler/1.0";

    @BeforeEach
    void setUp() {
        parser = new RobotsTxtParser();
    }

    @Test
    @DisplayName("Should allow all paths when robots.txt is empty")
    void shouldAllowAllWhenEmpty() {
        RobotsTxtParser.RobotsTxt robots = parser.parse("", USER_AGENT);

        assertTrue(robots.isAllowed("/"));
        assertTrue(robots.isAllowed("/any/path"));
        assertEquals(0, robots.crawlDelay());
    }

    @Test
    @DisplayName("Should allow all paths when robots.txt is null")
    void shouldAllowAllWhenNull() {
        RobotsTxtParser.RobotsTxt robots = parser.parse(null, USER_AGENT);

        assertTrue(robots.isAllowed("/"));
        assertTrue(robots.isAllowed("/any/path"));
    }

    @Test
    @DisplayName("Should parse simple Disallow rules")
    void shouldParseSimpleDisallow() {
        String content = """
            User-agent: *
            Disallow: /private/
            Disallow: /admin/
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertTrue(robots.isAllowed("/"));
        assertTrue(robots.isAllowed("/public/"));
        assertFalse(robots.isAllowed("/private/"));
        assertFalse(robots.isAllowed("/private/secret"));
        assertFalse(robots.isAllowed("/admin/"));
    }

    @Test
    @DisplayName("Should parse Allow rules")
    void shouldParseAllowRules() {
        String content = """
            User-agent: *
            Disallow: /private/
            Allow: /private/public-file.html
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertFalse(robots.isAllowed("/private/"));
        assertFalse(robots.isAllowed("/private/secret.html"));
        assertTrue(robots.isAllowed("/private/public-file.html"));
    }

    @Test
    @DisplayName("Should handle Crawl-delay directive")
    void shouldHandleCrawlDelay() {
        String content = """
            User-agent: *
            Crawl-delay: 2.5
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertEquals(2.5, robots.crawlDelay(), 0.001);
        assertEquals(2500, robots.getCrawlDelayMs());
    }

    @Test
    @DisplayName("Should handle wildcard patterns")
    void shouldHandleWildcardPatterns() {
        String content = """
            User-agent: *
            Disallow: /*.pdf
            Disallow: /search?*
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertFalse(robots.isAllowed("/document.pdf"));
        assertFalse(robots.isAllowed("/path/to/file.pdf"));
        assertFalse(robots.isAllowed("/search?q=test"));
        assertTrue(robots.isAllowed("/search"));
        assertTrue(robots.isAllowed("/document.html"));
    }

    @Test
    @DisplayName("Should handle end-of-URL pattern ($)")
    void shouldHandleEndOfUrlPattern() {
        String content = """
            User-agent: *
            Disallow: /*.pdf$
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertFalse(robots.isAllowed("/document.pdf"));
        assertTrue(robots.isAllowed("/document.pdf.html")); // Doesn't end with .pdf
    }

    @Test
    @DisplayName("Should respect specific user-agent over wildcard")
    void shouldRespectSpecificUserAgent() {
        String content = """
            User-agent: *
            Disallow: /public/

            User-agent: ConcurrentCrawler
            Disallow: /private/
            Allow: /public/
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        // Should use ConcurrentCrawler rules, not * rules
        assertTrue(robots.isAllowed("/public/"));
        assertFalse(robots.isAllowed("/private/"));
    }

    @Test
    @DisplayName("Should handle empty Disallow (allow all)")
    void shouldHandleEmptyDisallow() {
        String content = """
            User-agent: *
            Disallow:
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertTrue(robots.isAllowed("/"));
        assertTrue(robots.isAllowed("/any/path/at/all"));
    }

    @Test
    @DisplayName("Should ignore comments")
    void shouldIgnoreComments() {
        String content = """
            # This is a comment
            User-agent: *
            # Another comment
            Disallow: /private/ # Inline comments might not work
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertFalse(robots.isAllowed("/private/"));
        assertTrue(robots.isAllowed("/public/"));
    }

    @Test
    @DisplayName("Should handle case-insensitive directives")
    void shouldHandleCaseInsensitiveDirectives() {
        String content = """
            USER-AGENT: *
            DISALLOW: /private/
            ALLOW: /private/allowed/
            CRAWL-DELAY: 1
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertFalse(robots.isAllowed("/private/"));
        assertTrue(robots.isAllowed("/private/allowed/"));
        assertEquals(1.0, robots.crawlDelay(), 0.001);
    }

    @Test
    @DisplayName("Should prefer longer matching paths")
    void shouldPreferLongerMatchingPaths() {
        String content = """
            User-agent: *
            Disallow: /path/
            Allow: /path/allowed/
            Disallow: /path/allowed/secret/
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertFalse(robots.isAllowed("/path/other/"));
        assertTrue(robots.isAllowed("/path/allowed/file.html"));
        assertFalse(robots.isAllowed("/path/allowed/secret/file.html"));
    }

    @Test
    @DisplayName("Should handle real-world robots.txt example")
    void shouldHandleRealWorldExample() {
        String content = """
            User-agent: *
            Disallow: /search
            Disallow: /account/
            Disallow: /api/
            Allow: /api/public/
            Crawl-delay: 1

            User-agent: Googlebot
            Disallow:

            Sitemap: https://example.com/sitemap.xml
            """;

        RobotsTxtParser.RobotsTxt robots = parser.parse(content, USER_AGENT);

        assertFalse(robots.isAllowed("/search"));
        assertFalse(robots.isAllowed("/account/settings"));
        assertFalse(robots.isAllowed("/api/private"));
        assertTrue(robots.isAllowed("/api/public/data"));
        assertTrue(robots.isAllowed("/about"));
        assertEquals(1.0, robots.crawlDelay(), 0.001);
    }
}
