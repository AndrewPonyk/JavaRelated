package com.example.concurrency.deadlock;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Standalone timed-tryLock rollback and retry demonstration. */
public final class TimedBankTransferDemo {
    private TimedBankTransferDemo() {
    }

    public static void main(String[] args) throws Exception {
        BankAccount first = new BankAccount(1, 100);
        BankAccount second = new BankAccount(2, 100);
        BankTransferService transfers = new BankTransferService();
        CountDownLatch secondLockHeld = new CountDownLatch(1);
        CountDownLatch releaseSecondLock = new CountDownLatch(1);

        boolean firstAttempt;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> holder = executor.submit(() -> holdLock(
                    second, secondLockHeld, releaseSecondLock));
            if (!secondLockHeld.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("account lock was not acquired");
            }
            firstAttempt = transfers.timedTransfer(
                    first, second, 10, Duration.ofMillis(50));
            releaseSecondLock.countDown();
            holder.get(2, TimeUnit.SECONDS);
        } finally {
            releaseSecondLock.countDown();
        }

        boolean retry = transfers.timedTransfer(
                first, second, 10, Duration.ofSeconds(1));
        System.out.printf(
                "Bank Transfer [timed tryLock]: contendedAttempt=%s, retry=%s, balances=%d/%d%n",
                firstAttempt,
                retry,
                first.balance(),
                second.balance());
    }

    private static Void holdLock(
            BankAccount account,
            CountDownLatch lockHeld,
            CountDownLatch releaseLock) throws InterruptedException {
        account.lock().lockInterruptibly();
        try {
            lockHeld.countDown();
            releaseLock.await();
        } finally {
            account.lock().unlock();
        }
        return null;
    }
}
