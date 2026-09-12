package com.example.concurrency.dining;

import com.example.concurrency.deadlock.DeadlockDetector;
import com.example.concurrency.deadlock.DeadlockDetector.DeadlockedThread;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Detects a deliberate philosopher deadlock on daemon threads, then exits normally. */
public final class DeadlockDiningTableDemo {
    private DeadlockDiningTableDemo() {
    }

    public static void main(String[] args) throws Exception {
        int philosopherCount = 5;
        DeadlockDiningTable table = new DeadlockDiningTable(philosopherCount);
        for (int philosopher = 0; philosopher < philosopherCount; philosopher++) {
            int philosopherId = philosopher;
            Thread thread = new Thread(
                    () -> dineForever(table, philosopherId),
                    "deadlocked-philosopher-" + philosopherId);
            thread.setDaemon(true);
            thread.start();
        }

        List<DeadlockedThread> deadlocked = awaitDeadlock(Duration.ofSeconds(2)).stream()
                .filter(info -> info.name().startsWith("deadlocked-philosopher-"))
                .toList();
        if (deadlocked.size() != philosopherCount) {
            throw new IllegalStateException("expected five deadlocked philosopher threads");
        }
        System.out.printf(
                "Dining Philosophers [deliberate deadlock]: detectedThreads=%d, processCanExit=true%n",
                deadlocked.size());
    }

    private static void dineForever(DeadlockDiningTable table, int philosopherId) {
        try {
            table.dine(philosopherId, () -> {
                throw new AssertionError("deadlocked philosopher must never eat");
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<DeadlockedThread> awaitDeadlock(Duration timeout)
            throws InterruptedException {
        DeadlockDetector detector = new DeadlockDetector();
        long deadline = System.nanoTime() + timeout.toNanos();
        List<DeadlockedThread> result = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            result = detector.detect();
            if (!result.isEmpty()) {
                return result;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return result;
    }
}
