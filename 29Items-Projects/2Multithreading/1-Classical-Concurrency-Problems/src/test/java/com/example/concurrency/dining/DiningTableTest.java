package com.example.concurrency.dining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class DiningTableTest {
    private static final int PHILOSOPHERS = 5;
    private static final int MEALS_PER_PHILOSOPHER = 50;

    @Test
    void validatesTableSizePhilosopherAndAction() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceHierarchyDiningTable(1));
        assertThrows(IllegalArgumentException.class, () -> new ArbitratorDiningTable(1));
        assertThrows(IllegalArgumentException.class, () -> new DeadlockDiningTable(1));

        DiningTable table = new ResourceHierarchyDiningTable(5);
        assertThrows(IllegalArgumentException.class, () -> table.dine(-1, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> table.dine(5, () -> { }));
        assertThrows(NullPointerException.class, () -> table.dine(0, null));
    }

    @Test
    @Timeout(10)
    void preventionStrategiesCompleteWithoutAdjacentDiners() throws Exception {
        List<DiningTable> strategies = List.of(
                new ResourceHierarchyDiningTable(PHILOSOPHERS),
                new ArbitratorDiningTable(PHILOSOPHERS));

        for (DiningTable table : strategies) {
            verifyStrategy(table);
        }
    }

    private static void verifyStrategy(DiningTable table) throws Exception {
        AtomicIntegerArray eating = new AtomicIntegerArray(PHILOSOPHERS);
        AtomicInteger meals = new AtomicInteger();
        AtomicBoolean adjacentOverlap = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(PHILOSOPHERS)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int philosopher = 0; philosopher < PHILOSOPHERS; philosopher++) {
                int id = philosopher;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int meal = 0; meal < MEALS_PER_PHILOSOPHER; meal++) {
                        table.dine(id, () -> recordMeal(id, eating, meals, adjacentOverlap));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        assertFalse(adjacentOverlap.get());
        assertEquals(PHILOSOPHERS * MEALS_PER_PHILOSOPHER, meals.get());
    }

    private static void recordMeal(
            int id,
            AtomicIntegerArray eating,
            AtomicInteger meals,
            AtomicBoolean adjacentOverlap) {
        int previous = (id + PHILOSOPHERS - 1) % PHILOSOPHERS;
        int next = (id + 1) % PHILOSOPHERS;
        eating.set(id, 1);
        if (eating.get(previous) != 0 || eating.get(next) != 0) {
            adjacentOverlap.set(true);
        }
        Thread.yield();
        meals.incrementAndGet();
        eating.set(id, 0);
    }
}
