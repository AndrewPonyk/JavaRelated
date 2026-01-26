package com.crawler.core;

import com.crawler.util.UrlNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe URL frontier for managing URLs to be crawled.
 *
 * <p>Features:
 * <ul>
 *   <li>ConcurrentHashMap for deduplication</li>
 *   <li>Bounded queue to prevent memory exhaustion</li>
 *   <li>URL normalization before storage</li>
 * </ul>
 */
public class UrlFrontier { // |su:44 Thread-safe URL queue - the "to-do list" of URLs to crawl

    private static final Logger logger = LoggerFactory.getLogger(UrlFrontier.class);

    private final BlockingQueue<CrawlUrl> queue; // |su:45 LinkedBlockingQueue: thread-safe, bounded capacity
    private final ConcurrentHashMap<String, Boolean> seen; // |su:46 ConcurrentHashMap: O(1) thread-safe deduplication
    private final int maxSize;
    private final AtomicLong totalAdded = new AtomicLong(0);
    private final AtomicLong duplicatesSkipped = new AtomicLong(0);

    /**
     * Create a new URL frontier with the given maximum size.
     *
     * @param maxSize Maximum number of URLs to hold in the queue
     */
    public UrlFrontier(int maxSize) {
        this.maxSize = maxSize;
        this.queue = new LinkedBlockingQueue<>(maxSize);
        this.seen = new ConcurrentHashMap<>(maxSize);
    }

    /**
     * Add a URL to the frontier if not already seen.
     *
     * @param url   The URL to add
     * @param depth The crawl depth of this URL
     * @return true if the URL was added, false if duplicate or queue full
     */
    public boolean add(String url, int depth) {
        // Normalize URL first
        String normalizedUrl = UrlNormalizer.normalize(url);
        if (normalizedUrl == null) {
            return false;
        }

        // Check if URL is valid for crawling
        if (!isValidUrl(normalizedUrl)) {
            return false;
        }

        // |su:47 putIfAbsent: atomic check-and-set, returns null if key was absent (thread-safe dedup)
        if (seen.putIfAbsent(normalizedUrl, Boolean.TRUE) != null) {
            duplicatesSkipped.incrementAndGet();
            return false; // Already seen - skip
        }

        // Try to add to queue
        CrawlUrl crawlUrl = new CrawlUrl(normalizedUrl, depth, System.currentTimeMillis());
        if (queue.offer(crawlUrl)) {
            totalAdded.incrementAndGet();
            logger.trace("Added URL to frontier: {} (depth={})", normalizedUrl, depth);
            return true;
        } else {
            // Queue is full, remove from seen set
            seen.remove(normalizedUrl);
            logger.debug("Frontier full (size={}), skipped: {}", maxSize, normalizedUrl);
            return false;
        }
    }

    /**
     * Get the next URL to crawl, blocking if necessary.
     *
     * @return The next CrawlUrl, or null if interrupted
     */
    public CrawlUrl poll() {
        try {
            return queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Check if the frontier is empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Get the current size of the frontier queue.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Get the total number of unique URLs seen.
     */
    public int getSeenCount() {
        return seen.size();
    }

    /**
     * Get statistics about the frontier.
     */
    public String getStats() {
        return String.format("Frontier[queued=%d, seen=%d, added=%d, duplicates=%d]",
                queue.size(), seen.size(), totalAdded.get(), duplicatesSkipped.get());
    }

    /**
     * Check if a URL has been seen before.
     */
    public boolean hasSeen(String url) {
        String normalizedUrl = UrlNormalizer.normalize(url);
        return normalizedUrl != null && seen.containsKey(normalizedUrl);
    }

    /**
     * Clear the frontier (mainly for testing).
     */
    public void clear() {
        queue.clear();
        seen.clear();
        totalAdded.set(0);
        duplicatesSkipped.set(0);
    }

    private boolean isValidUrl(String url) {
        // Only allow HTTP and HTTPS
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }

        // Block common non-HTML resources
        String lower = url.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".gif") ||
            lower.endsWith(".pdf") || lower.endsWith(".zip") ||
            lower.endsWith(".exe") || lower.endsWith(".mp3") ||
            lower.endsWith(".mp4") || lower.endsWith(".css") ||
            lower.endsWith(".js")) {
            return false;
        }

        return true;
    }

    /**
     * Record representing a URL to be crawled with metadata.
     */
    public record CrawlUrl(String url, int depth, long addedAt) {
    }
}
