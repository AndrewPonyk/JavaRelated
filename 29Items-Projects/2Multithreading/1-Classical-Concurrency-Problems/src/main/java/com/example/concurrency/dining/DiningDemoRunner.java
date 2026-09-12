package com.example.concurrency.dining;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded workload for the safe Dining Philosophers demos. */
final class DiningDemoRunner {
    private static final int MEALS_PER_PHILOSOPHER = 3;

    private DiningDemoRunner() {
    }

    static void run(String strategy, DiningTable table) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger completedMeals = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(table.philosopherCount())) {
            List<Future<?>> futures = new ArrayList<>();
            for (int philosopher = 0; philosopher < table.philosopherCount(); philosopher++) {
                int philosopherId = philosopher;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int meal = 0; meal < MEALS_PER_PHILOSOPHER; meal++) {
                        table.dine(philosopherId, completedMeals::incrementAndGet);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(3, TimeUnit.SECONDS);
            }
        }

        int expectedMeals = table.philosopherCount() * MEALS_PER_PHILOSOPHER;
        System.out.printf(
                "Dining Philosophers [%s]: philosophers=%d, meals=%d/%d, completed=%s%n",
                strategy,
                table.philosopherCount(),
                completedMeals.get(),
                expectedMeals,
                completedMeals.get() == expectedMeals);
    }
}
