package com.example.trading.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.trading.TestFixture;
import com.example.trading.infrastructure.concurrent.ConcurrentAuditLog;
import com.example.trading.infrastructure.concurrent.StampedPriceCache;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PriceServiceTest {

    @Test
    void fetchesOnceThenUsesTheCacheAndSupportsCrud() {
        AtomicInteger providerCalls = new AtomicInteger();
        PriceService service = service(
                symbol -> {
                    providerCalls.incrementAndGet();
                    return new BigDecimal("225.00");
                },
                Runnable::run,
                Duration.ofSeconds(1));

        assertEquals(new BigDecimal("225.00"), service.getPrice("aapl").join());
        assertEquals(new BigDecimal("225.00"), service.getPrice("AAPL").join());
        assertEquals(1, providerCalls.get());
        assertEquals(new BigDecimal("99"), service.putPrice("test", new BigDecimal("99")));
        assertEquals(new BigDecimal("99"), service.deletePrice("TEST").orElseThrow());
        assertTrue(service.deletePrice("TEST").isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.putPrice("TEST", BigDecimal.ZERO));
    }

    @Test
    void coalescesConcurrentCacheMisses() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PriceService service = service(
                    symbol -> {
                        calls.incrementAndGet();
                        providerStarted.countDown();
                        await(releaseProvider);
                        return BigDecimal.TEN;
                    },
                    executor,
                    Duration.ofSeconds(2));

            CompletableFuture<BigDecimal> first = service.getPrice("TEST");
            assertTrue(providerStarted.await(1, TimeUnit.SECONDS));
            CompletableFuture<BigDecimal> second = service.getPrice("test");
            assertSame(first, second);
            releaseProvider.countDown();
            assertEquals(BigDecimal.TEN, first.join());
            assertEquals(1, calls.get());
            assertEquals(0, service.inFlightRequestCount());
        }
    }

    @Test
    void appliesTimeoutAndPreservesProviderFailures() {
        CountDownLatch releaseProvider = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PriceService timeoutService = service(
                    symbol -> {
                        await(releaseProvider);
                        return BigDecimal.ONE;
                    },
                    executor,
                    Duration.ofMillis(20));
            assertThrows(CompletionException.class, () -> timeoutService.getPrice("TEST").join());
            releaseProvider.countDown();

            PriceService failingService = service(
                    symbol -> {
                        throw new IllegalStateException("feed down");
                    },
                    executor,
                    Duration.ofSeconds(1));
            CompletionException error =
                    assertThrows(CompletionException.class, () -> failingService.getPrice("FAIL").join());
            assertTrue(error.getCause().getMessage().contains("could not fetch price"));
        }
    }

    @Test
    void explicitUpdateWinsAgainstAnOlderInFlightFetch() throws Exception {
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        CompletableFuture<BigDecimal> staleRequest;
        PriceService service;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            service = service(
                    symbol -> {
                        providerStarted.countDown();
                        await(releaseProvider);
                        return new BigDecimal("10");
                    },
                    executor,
                    Duration.ofSeconds(1));
            staleRequest = service.getPrice("TEST");
            assertTrue(providerStarted.await(1, TimeUnit.SECONDS));

            service.putPrice("TEST", new BigDecimal("20"));
            assertThrows(CancellationException.class, staleRequest::join);
            releaseProvider.countDown();
        }

        assertEquals(new BigDecimal("20"), service.getPrice("TEST").join());
    }

    private static PriceService service(
            com.example.trading.application.port.PriceProvider provider,
            java.util.concurrent.Executor executor,
            Duration timeout) {
        return new PriceService(
                new StampedPriceCache(),
                provider,
                executor,
                timeout,
                new ConcurrentAuditLog(),
                TestFixture.CLOCK);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", exception);
        }
    }
}
