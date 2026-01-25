package com.crawler.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics collection for crawl statistics.
 */
public class CrawlMetrics {

    // Basic counters
    private final AtomicLong pagesProcessed = new AtomicLong(0);
    private final AtomicLong bytesDownloaded = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final AtomicLong robotsBlocked = new AtomicLong(0);
    private final AtomicLong duplicatesSkipped = new AtomicLong(0);

    // Status code distribution
    private final ConcurrentHashMap<Integer, AtomicLong> statusCodes = new ConcurrentHashMap<>();

    // Per-domain statistics
    private final ConcurrentHashMap<String, AtomicLong> domainCounts = new ConcurrentHashMap<>();

    // Timing
    private final long startTime = System.currentTimeMillis();

    /**
     * Record a successfully processed page.
     *
     * @param domain      Domain of the page
     * @param statusCode  HTTP status code
     * @param bytes       Size of the page in bytes
     */
    public void recordPage(String domain, int statusCode, long bytes) {
        pagesProcessed.incrementAndGet();
        bytesDownloaded.addAndGet(bytes);
        statusCodes.computeIfAbsent(statusCode, k -> new AtomicLong()).incrementAndGet();
        domainCounts.computeIfAbsent(domain, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Record an error during crawling.
     */
    public void recordError() {
        errors.incrementAndGet();
    }

    /**
     * Record a URL blocked by robots.txt.
     */
    public void recordRobotsBlocked() {
        robotsBlocked.incrementAndGet();
    }

    /**
     * Record a duplicate URL that was skipped.
     */
    public void recordDuplicateSkipped() {
        duplicatesSkipped.incrementAndGet();
    }

    // Getters

    public long getPagesProcessed() {
        return pagesProcessed.get();
    }

    public long getBytesDownloaded() {
        return bytesDownloaded.get();
    }

    public long getErrors() {
        return errors.get();
    }

    public long getRobotsBlocked() {
        return robotsBlocked.get();
    }

    public long getDuplicatesSkipped() {
        return duplicatesSkipped.get();
    }

    public Map<Integer, AtomicLong> getStatusCodeDistribution() {
        return statusCodes;
    }

    public Map<String, AtomicLong> getDomainCounts() {
        return domainCounts;
    }

    /**
     * Get elapsed time in milliseconds.
     */
    public long getElapsedTimeMs() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get pages per minute rate.
     */
    public double getPagesPerMinute() {
        long elapsed = getElapsedTimeMs();
        if (elapsed == 0) {
            return 0.0;
        }
        return (pagesProcessed.get() * 60000.0) / elapsed;
    }

    /**
     * Get bytes per second rate.
     */
    public double getBytesPerSecond() {
        long elapsed = getElapsedTimeMs();
        if (elapsed == 0) {
            return 0.0;
        }
        return (bytesDownloaded.get() * 1000.0) / elapsed;
    }

    /**
     * Get error rate as percentage.
     */
    public double getErrorRate() {
        long total = pagesProcessed.get() + errors.get();
        if (total == 0) {
            return 0.0;
        }
        return (errors.get() * 100.0) / total;
    }

    /**
     * Get number of unique domains crawled.
     */
    public int getUniqueDomains() {
        return domainCounts.size();
    }

    /**
     * Get formatted bytes string (KB, MB, etc.).
     */
    public String getFormattedBytes() {
        long bytes = bytesDownloaded.get();
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Get formatted elapsed time.
     */
    public String getFormattedElapsedTime() {
        long elapsed = getElapsedTimeMs();
        long seconds = elapsed / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Get summary as JSON-like string.
     */
    public String toJson() {
        return String.format("""
            {
              "pagesProcessed": %d,
              "bytesDownloaded": %d,
              "errors": %d,
              "robotsBlocked": %d,
              "duplicatesSkipped": %d,
              "uniqueDomains": %d,
              "elapsedTimeMs": %d,
              "pagesPerMinute": %.2f,
              "errorRate": %.2f
            }""",
            pagesProcessed.get(),
            bytesDownloaded.get(),
            errors.get(),
            robotsBlocked.get(),
            duplicatesSkipped.get(),
            getUniqueDomains(),
            getElapsedTimeMs(),
            getPagesPerMinute(),
            getErrorRate()
        );
    }

    @Override
    public String toString() {
        return String.format(
            "CrawlMetrics[pages=%d, bytes=%s, errors=%d (%.1f%%), domains=%d, time=%s, rate=%.1f pages/min]",
            pagesProcessed.get(),
            getFormattedBytes(),
            errors.get(),
            getErrorRate(),
            getUniqueDomains(),
            getFormattedElapsedTime(),
            getPagesPerMinute()
        );
    }
}
