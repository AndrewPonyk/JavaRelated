package com.crawler.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UrlFrontier.
 */
class UrlFrontierTest {

    private UrlFrontier frontier;

    @BeforeEach
    void setUp() {
        frontier = new UrlFrontier(1000);
    }

    @Test
    @DisplayName("Should add valid URLs to frontier")
    void shouldAddValidUrls() {
        assertTrue(frontier.add("https://example.com/page1", 0));
        assertTrue(frontier.add("https://example.com/page2", 0));
        assertEquals(2, frontier.size());
    }

    @Test
    @DisplayName("Should reject duplicate URLs")
    void shouldRejectDuplicates() {
        assertTrue(frontier.add("https://example.com/page", 0));
        assertFalse(frontier.add("https://example.com/page", 0));
        assertEquals(1, frontier.size());
    }

    @Test
    @DisplayName("Should normalize URLs for deduplication")
    void shouldNormalizeUrlsForDeduplication() {
        assertTrue(frontier.add("https://example.com/page", 0));
        // Same URL with trailing slash should be treated as duplicate
        // (depends on normalization implementation)
        assertFalse(frontier.add("https://example.com/page/", 0));
    }

    @Test
    @DisplayName("Should reject invalid URLs")
    void shouldRejectInvalidUrls() {
        assertFalse(frontier.add("not-a-url", 0));
        assertFalse(frontier.add("", 0));
        assertFalse(frontier.add(null, 0));
        assertEquals(0, frontier.size());
    }

    @Test
    @DisplayName("Should reject non-HTTP URLs")
    void shouldRejectNonHttpUrls() {
        assertFalse(frontier.add("ftp://example.com/file", 0));
        assertFalse(frontier.add("mailto:test@example.com", 0));
        assertFalse(frontier.add("javascript:void(0)", 0));
    }

    @Test
    @DisplayName("Should filter out non-HTML resources")
    void shouldFilterNonHtmlResources() {
        assertFalse(frontier.add("https://example.com/image.jpg", 0));
        assertFalse(frontier.add("https://example.com/style.css", 0));
        assertFalse(frontier.add("https://example.com/script.js", 0));
        assertFalse(frontier.add("https://example.com/document.pdf", 0));
    }

    @Test
    @DisplayName("Should poll URLs in FIFO order")
    void shouldPollInFifoOrder() {
        frontier.add("https://example.com/first", 0);
        frontier.add("https://example.com/second", 0);
        frontier.add("https://example.com/third", 0);

        UrlFrontier.CrawlUrl first = frontier.poll();
        assertNotNull(first);
        assertTrue(first.url().contains("first"));
    }

    @Test
    @DisplayName("Should track seen URLs")
    void shouldTrackSeenUrls() {
        frontier.add("https://example.com/page", 0);
        assertTrue(frontier.hasSeen("https://example.com/page"));
        assertFalse(frontier.hasSeen("https://example.com/other"));
    }

    @Test
    @DisplayName("Should report correct statistics")
    void shouldReportCorrectStats() {
        frontier.add("https://example.com/page1", 0);
        frontier.add("https://example.com/page2", 0);
        frontier.add("https://example.com/page1", 0); // duplicate

        assertEquals(2, frontier.size());
        assertEquals(2, frontier.getSeenCount());

        String stats = frontier.getStats();
        assertTrue(stats.contains("queued=2"));
        assertTrue(stats.contains("seen=2"));
    }

    @Test
    @DisplayName("Should clear frontier correctly")
    void shouldClearCorrectly() {
        frontier.add("https://example.com/page1", 0);
        frontier.add("https://example.com/page2", 0);

        frontier.clear();

        assertTrue(frontier.isEmpty());
        assertEquals(0, frontier.getSeenCount());
        assertFalse(frontier.hasSeen("https://example.com/page1"));
    }

    @Test
    @DisplayName("Should handle concurrent access")
    void shouldHandleConcurrentAccess() throws InterruptedException {
        int numThreads = 10;
        int urlsPerThread = 100;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < urlsPerThread; j++) {
                    frontier.add("https://example.com/" + threadId + "/" + j, 0);
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(numThreads * urlsPerThread, frontier.getSeenCount());
    }
}
