package com.crawler.robots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-domain rate limiter to respect crawl delays.
 *
 * <p>Features:
 * <ul>
 *   <li>Per-domain tracking</li>
 *   <li>Thread-safe implementation</li>
 *   <li>Dynamic delay adjustment based on robots.txt</li>
 * </ul>
 */
public class RateLimiter { // |su:59 Per-domain rate limiter - prevents hammering any single server

    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    private final ConcurrentHashMap<String, DomainLimiter> limiters; // |su:60 One limiter per domain
    private final long defaultDelayMs;

    public RateLimiter(long defaultDelayMs) {
        this.limiters = new ConcurrentHashMap<>();
        this.defaultDelayMs = defaultDelayMs;
    }

    /**
     * Wait for permission to access a domain.
     * Blocks until the rate limit allows another request.
     *
     * @param domain Domain to access
     * @throws InterruptedException if interrupted while waiting
     */
    public void waitForPermit(String domain) throws InterruptedException {
        DomainLimiter limiter = limiters.computeIfAbsent(domain,
                d -> new DomainLimiter(defaultDelayMs));
        limiter.acquire();
    }

    /**
     * Try to get permission without blocking.
     *
     * @param domain Domain to access
     * @return true if permit acquired, false if would need to wait
     */
    public boolean tryAcquire(String domain) {
        DomainLimiter limiter = limiters.computeIfAbsent(domain,
                d -> new DomainLimiter(defaultDelayMs));
        return limiter.tryAcquire();
    }

    /**
     * Set custom delay for a domain.
     *
     * @param domain  Domain name
     * @param delayMs Delay in milliseconds
     */
    public void setDelay(String domain, long delayMs) {
        DomainLimiter limiter = limiters.computeIfAbsent(domain,
                d -> new DomainLimiter(delayMs));
        limiter.setDelayMs(delayMs);
        logger.debug("Set delay for {}: {}ms", domain, delayMs);
    }

    /**
     * Get the current delay for a domain.
     *
     * @param domain Domain name
     * @return Current delay in milliseconds
     */
    public long getDelay(String domain) {
        DomainLimiter limiter = limiters.get(domain);
        return limiter != null ? limiter.getDelayMs() : defaultDelayMs;
    }

    /**
     * Remove rate limiting for a domain.
     *
     * @param domain Domain name
     */
    public void remove(String domain) {
        limiters.remove(domain);
    }

    /**
     * Clear all rate limiters.
     */
    public void clear() {
        limiters.clear();
    }

    /**
     * Get statistics.
     */
    public String getStats() {
        return String.format("RateLimiter[domains=%d, defaultDelay=%dms]",
                limiters.size(), defaultDelayMs);
    }

    /**
     * Per-domain rate limiter implementation.
     */
    private static class DomainLimiter { // |su:61 Inner class: handles rate limiting for single domain
        private final ReentrantLock lock = new ReentrantLock(true); // |su:62 Fair lock: FIFO ordering, no starvation
        private volatile long delayMs; // |su:63 volatile: visibility across threads without locking
        private volatile long lastRequestTime = 0;

        DomainLimiter(long delayMs) {
            this.delayMs = delayMs;
        }

        void acquire() throws InterruptedException { // |su:64 Block until enough time has passed since last request
            lock.lock(); // |su:65 lock() blocks until acquired - ensures serialized access
            try {
                long now = System.currentTimeMillis();
                long timeSinceLastRequest = now - lastRequestTime;
                long waitTime = delayMs - timeSinceLastRequest;

                if (waitTime > 0) {
                    logger.trace("Rate limiting: waiting {}ms", waitTime);
                    TimeUnit.MILLISECONDS.sleep(waitTime); // |su:66 Sleep to enforce delay between requests
                }

                lastRequestTime = System.currentTimeMillis();
            } finally {
                lock.unlock(); // |su:67 ALWAYS unlock in finally - prevents deadlock on exception
            }
        }

        boolean tryAcquire() {
            if (!lock.tryLock()) {
                return false;
            }
            try {
                long now = System.currentTimeMillis();
                long timeSinceLastRequest = now - lastRequestTime;

                if (timeSinceLastRequest >= delayMs) {
                    lastRequestTime = now;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        long getDelayMs() {
            return delayMs;
        }
    }
}
