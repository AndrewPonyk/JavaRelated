package com.crawler.config;

/**
 * Configuration POJO holding all crawler settings.
 * Values can be loaded from properties files, environment variables, or CLI args.
 */
public class CrawlerConfig { // |su:7 Configuration POJO - all crawler settings with sensible defaults

    // |su:8 Thread pool: threadCount workers execute crawl tasks concurrently
    private int threadCount = 10;
    private int maxConnections = 20; // |su:9 Semaphore permits - limits simultaneous HTTP connections

    // |su:10 Crawl limits - prevent infinite crawling
    private int maxPages = 10000; // Stop after N pages
    private int maxDepth = 10; // How far from seed URL to follow links
    private long defaultDelayMs = 1000; // |su:11 Politeness delay - wait between requests to same domain
    private int requestTimeoutMs = 30000;
    private int maxBodySizeBytes = 10 * 1024 * 1024; // 10MB

    // User agent
    private String userAgent = "ConcurrentCrawler/1.0 (+https://github.com/example/crawler)";

    // Database
    private String databasePath = "./data/crawler.db";

    // Robots.txt
    private boolean respectRobotsTxt = true;
    private long robotsTxtCacheTtlMs = 24 * 60 * 60 * 1000; // 24 hours

    // Retry settings
    private int maxRetries = 3;
    private long retryBaseDelayMs = 1000;

    // Domain restrictions (optional)
    private String[] allowedDomains = null;
    private String[] blockedDomains = null;

    // Phaser settings
    private int phasesCount = 3;

    // Constructors
    public CrawlerConfig() {
    }

    // Getters and Setters
    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public long getDefaultDelayMs() {
        return defaultDelayMs;
    }

    public void setDefaultDelayMs(long defaultDelayMs) {
        this.defaultDelayMs = defaultDelayMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getMaxBodySizeBytes() {
        return maxBodySizeBytes;
    }

    public void setMaxBodySizeBytes(int maxBodySizeBytes) {
        this.maxBodySizeBytes = maxBodySizeBytes;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }

    public boolean isRespectRobotsTxt() {
        return respectRobotsTxt;
    }

    public void setRespectRobotsTxt(boolean respectRobotsTxt) {
        this.respectRobotsTxt = respectRobotsTxt;
    }

    public long getRobotsTxtCacheTtlMs() {
        return robotsTxtCacheTtlMs;
    }

    public void setRobotsTxtCacheTtlMs(long robotsTxtCacheTtlMs) {
        this.robotsTxtCacheTtlMs = robotsTxtCacheTtlMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBaseDelayMs() {
        return retryBaseDelayMs;
    }

    public void setRetryBaseDelayMs(long retryBaseDelayMs) {
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    public String[] getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(String[] allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public String[] getBlockedDomains() {
        return blockedDomains;
    }

    public void setBlockedDomains(String[] blockedDomains) {
        this.blockedDomains = blockedDomains;
    }

    public int getPhasesCount() {
        return phasesCount;
    }

    public void setPhasesCount(int phasesCount) {
        this.phasesCount = phasesCount;
    }

    @Override
    public String toString() {
        return "CrawlerConfig{" +
                "threadCount=" + threadCount +
                ", maxConnections=" + maxConnections +
                ", maxPages=" + maxPages +
                ", maxDepth=" + maxDepth +
                ", userAgent='" + userAgent + '\'' +
                '}';
    }
}
