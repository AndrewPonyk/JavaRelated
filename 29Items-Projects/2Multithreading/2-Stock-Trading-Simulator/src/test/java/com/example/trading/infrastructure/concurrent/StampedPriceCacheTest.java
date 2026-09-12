package com.example.trading.infrastructure.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class StampedPriceCacheTest {

    @Test
    void supportsOptimisticReadsDuringConcurrentWrites() throws Exception {
        StampedPriceCache cache = new StampedPriceCache();
        cache.put("aapl", new BigDecimal("100.00"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(2_000);
            for (int index = 0; index < 2_000; index++) {
                int value = index;
                futures.add(executor.submit(() -> {
                    if (value % 10 == 0) {
                        cache.put("AAPL", BigDecimal.valueOf(100L + value));
                    } else {
                        assertFalse(cache.get("aapl").isEmpty());
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertEquals(1, cache.get("AAPL").orElseThrow().signum());
        assertTrue(cache.optimisticReadCount() > 0);
        assertTrue(cache.fallbackReadCount() >= 0);
    }
}
