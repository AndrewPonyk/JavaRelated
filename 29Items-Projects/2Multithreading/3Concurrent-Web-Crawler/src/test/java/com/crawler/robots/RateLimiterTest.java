package com.crawler.robots;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimiter.
 */
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(100); // 100ms default delay
    }

    @Test
    @DisplayName("Should create rate limiter with default delay")
    void shouldCreateWithDefaultDelay() {
        assertEquals(100, rateLimiter.getDelay("unknown-domain"));
    }

    @Test
    @DisplayName("Should set custom delay for domain")
    void shouldSetCustomDelay() {
        rateLimiter.setDelay("example.com", 500);
        assertEquals(500, rateLimiter.getDelay("example.com"));
    }

    @Test
    @DisplayName("Should acquire permit after waiting")
    void shouldAcquirePermit() throws InterruptedException {
        long start = System.currentTimeMillis();

        rateLimiter.waitForPermit("example.com");
        rateLimiter.waitForPermit("example.com");

        long elapsed = System.currentTimeMillis() - start;
        // Second request should have waited at least the delay time
        assertTrue(elapsed >= 90, "Should have waited at least ~100ms");
    }

    @Test
    @DisplayName("Should try acquire immediately when not rate limited")
    void shouldTryAcquireImmediately() throws InterruptedException {
        // First acquire
        assertTrue(rateLimiter.tryAcquire("example.com"));

        // Immediate second try should fail (rate limited)
        assertFalse(rateLimiter.tryAcquire("example.com"));

        // Wait for rate limit to expire
        Thread.sleep(150);

        // Should succeed now
        assertTrue(rateLimiter.tryAcquire("example.com"));
    }

    @Test
    @DisplayName("Should track different domains separately")
    void shouldTrackDomainsSeparately() throws InterruptedException {
        rateLimiter.setDelay("slow.com", 500);
        rateLimiter.setDelay("fast.com", 50);

        assertEquals(500, rateLimiter.getDelay("slow.com"));
        assertEquals(50, rateLimiter.getDelay("fast.com"));

        // Both first requests should succeed immediately
        assertTrue(rateLimiter.tryAcquire("slow.com"));
        assertTrue(rateLimiter.tryAcquire("fast.com"));
    }

    @Test
    @DisplayName("Should remove domain rate limiter")
    void shouldRemoveDomain() {
        rateLimiter.setDelay("example.com", 500);
        assertEquals(500, rateLimiter.getDelay("example.com"));

        rateLimiter.remove("example.com");

        // Should return default delay after removal
        assertEquals(100, rateLimiter.getDelay("example.com"));
    }

    @Test
    @DisplayName("Should clear all rate limiters")
    void shouldClearAll() {
        rateLimiter.setDelay("domain1.com", 100);
        rateLimiter.setDelay("domain2.com", 200);
        rateLimiter.setDelay("domain3.com", 300);

        rateLimiter.clear();

        // All should return default delay
        assertEquals(100, rateLimiter.getDelay("domain1.com"));
        assertEquals(100, rateLimiter.getDelay("domain2.com"));
        assertEquals(100, rateLimiter.getDelay("domain3.com"));
    }

    @Test
    @DisplayName("Should return stats")
    void shouldReturnStats() {
        rateLimiter.setDelay("example.com", 500);
        rateLimiter.setDelay("test.org", 200);

        String stats = rateLimiter.getStats();

        assertTrue(stats.contains("domains=2"));
        assertTrue(stats.contains("defaultDelay=100ms"));
    }

    @Test
    @DisplayName("Should handle concurrent access")
    void shouldHandleConcurrentAccess() throws InterruptedException {
        int numThreads = 5;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger acquireCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    rateLimiter.waitForPermit("example.com");
                    acquireCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(numThreads, acquireCount.get());

        executor.shutdown();
    }

    @Test
    @DisplayName("Should enforce rate limiting with fair ordering")
    void shouldEnforceFairOrdering() throws InterruptedException {
        RateLimiter slowLimiter = new RateLimiter(50); // 50ms delay

        long start = System.currentTimeMillis();

        // Make 3 requests
        slowLimiter.waitForPermit("example.com");
        slowLimiter.waitForPermit("example.com");
        slowLimiter.waitForPermit("example.com");

        long elapsed = System.currentTimeMillis() - start;

        // Should have waited approximately 100ms (2 * 50ms for second and third)
        assertTrue(elapsed >= 90, "Should have waited at least ~100ms for 3 requests");
    }
}
