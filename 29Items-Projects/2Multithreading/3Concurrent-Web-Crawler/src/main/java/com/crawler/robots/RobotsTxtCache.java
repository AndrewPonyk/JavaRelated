package com.crawler.robots;

import com.crawler.config.CrawlerConfig;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cache for robots.txt rules with automatic fetching and expiration.
 */
public class RobotsTxtCache {

    private static final Logger logger = LoggerFactory.getLogger(RobotsTxtCache.class);

    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final RobotsTxtParser parser;
    private final CrawlerConfig config;

    public RobotsTxtCache(CrawlerConfig config) {
        this.cache = new ConcurrentHashMap<>();
        this.parser = new RobotsTxtParser();
        this.config = config;
    }

    /**
     * Check if a URL is allowed by robots.txt.
     *
     * @param url Full URL to check
     * @return true if allowed, false if disallowed
     */
    public boolean isAllowed(String url) {
        try {
            URL parsedUrl = new URL(url);
            String domain = parsedUrl.getHost();
            String path = parsedUrl.getPath();

            if (path.isEmpty()) {
                path = "/";
            }

            RobotsTxtParser.RobotsTxt robotsTxt = getRobotsTxt(domain);
            return robotsTxt.isAllowed(path);

        } catch (Exception e) {
            logger.warn("Error checking robots.txt for URL: {}", url, e);
            // Default to allow on error
            return true;
        }
    }

    /**
     * Get the crawl delay for a domain.
     *
     * @param domain Domain name
     * @return Crawl delay in milliseconds
     */
    public long getCrawlDelay(String domain) {
        try {
            RobotsTxtParser.RobotsTxt robotsTxt = getRobotsTxt(domain);
            long robotsDelay = robotsTxt.getCrawlDelayMs();

            // Use the larger of robots.txt delay or configured default
            return Math.max(robotsDelay, config.getDefaultDelayMs());

        } catch (Exception e) {
            return config.getDefaultDelayMs();
        }
    }

    /**
     * Get robots.txt rules for a domain, fetching if necessary.
     *
     * @param domain Domain name
     * @return Parsed RobotsTxt
     */
    private RobotsTxtParser.RobotsTxt getRobotsTxt(String domain) {
        CacheEntry entry = cache.get(domain);

        // Check if we have a valid cached entry
        if (entry != null && !entry.isExpired()) {
            return entry.robotsTxt();
        }

        // Fetch and parse robots.txt
        RobotsTxtParser.RobotsTxt robotsTxt = fetchRobotsTxt(domain);

        // Cache the result
        long expiresAt = System.currentTimeMillis() + config.getRobotsTxtCacheTtlMs();
        cache.put(domain, new CacheEntry(robotsTxt, expiresAt));

        return robotsTxt;
    }

    /**
     * Fetch robots.txt from a domain.
     *
     * @param domain Domain name
     * @return Parsed RobotsTxt (empty rules if not found)
     */
    private RobotsTxtParser.RobotsTxt fetchRobotsTxt(String domain) {
        String robotsUrl = "https://" + domain + "/robots.txt";

        try {
            logger.debug("Fetching robots.txt from: {}", robotsUrl);

            String content = Jsoup.connect(robotsUrl)
                    .userAgent(config.getUserAgent())
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            return parser.parse(content, config.getUserAgent());

        } catch (Exception e) {
            logger.debug("Could not fetch robots.txt for {}: {}", domain, e.getMessage());

            // Try HTTP if HTTPS failed
            try {
                robotsUrl = "http://" + domain + "/robots.txt";
                String content = Jsoup.connect(robotsUrl)
                        .userAgent(config.getUserAgent())
                        .timeout(10000)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .execute()
                        .body();

                return parser.parse(content, config.getUserAgent());

            } catch (Exception e2) {
                logger.debug("Could not fetch robots.txt via HTTP for {}", domain);
            }

            // Return empty rules (allow all) if we can't fetch
            return new RobotsTxtParser.RobotsTxt(java.util.List.of(), 0.0);
        }
    }

    /**
     * Invalidate cached entry for a domain.
     *
     * @param domain Domain name
     */
    public void invalidate(String domain) {
        cache.remove(domain);
        logger.debug("Invalidated robots.txt cache for: {}", domain);
    }

    /**
     * Clear all cached entries.
     */
    public void clear() {
        cache.clear();
        logger.info("Cleared robots.txt cache");
    }

    /**
     * Get cache statistics.
     */
    public String getStats() {
        long validEntries = cache.values().stream()
                .filter(e -> !e.isExpired())
                .count();
        return String.format("RobotsTxtCache[total=%d, valid=%d]", cache.size(), validEntries);
    }

    /**
     * Cache entry with expiration.
     */
    private record CacheEntry(RobotsTxtParser.RobotsTxt robotsTxt, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
