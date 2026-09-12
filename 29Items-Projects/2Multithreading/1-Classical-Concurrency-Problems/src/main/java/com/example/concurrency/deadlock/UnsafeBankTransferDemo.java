package com.example.concurrency.deadlock;

import com.example.concurrency.deadlock.DeadlockDetector.DeadlockedThread;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Detects a deliberate opposite-transfer deadlock on daemon threads, then exits. */
public final class UnsafeBankTransferDemo {
    private UnsafeBankTransferDemo() {
    }

    public static void main(String[] args) throws Exception {
        BankAccount first = new BankAccount(1, 1_000);
        BankAccount second = new BankAccount(2, 1_000);
        BankTransferService transfers = new BankTransferService();
        CountDownLatch sourceLocksAcquired = new CountDownLatch(2);

        startDeadlockingTransfer(
                "unsafe-transfer-A-to-B",
                transfers,
                first,
                second,
                sourceLocksAcquired);
        startDeadlockingTransfer(
                "unsafe-transfer-B-to-A",
                transfers,
                second,
                first,
                sourceLocksAcquired);

        List<DeadlockedThread> deadlocked = awaitDeadlock(Duration.ofSeconds(2)).stream()
                .filter(info -> info.name().startsWith("unsafe-transfer-"))
                .toList();
        if (deadlocked.size() != 2) {
            throw new IllegalStateException("expected two deadlocked transfer threads");
        }
        System.out.printf(
                "Bank Transfer [unsafe source-first locking]: detectedThreads=%d, balancesUnchanged=%s%n",
                deadlocked.size(),
                first.balance() + second.balance() == 2_000);
    }

    private static void startDeadlockingTransfer(
            String threadName,
            BankTransferService transfers,
            BankAccount source,
            BankAccount destination,
            CountDownLatch sourceLocksAcquired) {
        Thread thread = new Thread(() -> {
            try {
                transfers.unsafeTransfer(source, destination, 10, sourceLocksAcquired);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
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
