package com.example.trading.infrastructure.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class AtomicOrderIdGeneratorTest {

    @Test
    void generatesUniqueIdsAcrossTenThousandVirtualThreads() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            AtomicOrderIdGenerator generator = new AtomicOrderIdGenerator();
            Set<Long> ids = ConcurrentHashMap.newKeySet();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Long>> futures = new ArrayList<>(10_000);
                for (int index = 0; index < 10_000; index++) {
                    futures.add(executor.submit(generator::nextId));
                }
                for (Future<Long> future : futures) {
                    assertTrue(ids.add(future.get()), "duplicate ID generated");
                }
            }

            assertEquals(10_000, ids.size());
        });
    }
}
