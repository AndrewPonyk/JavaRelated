package com.example.concurrency.deadlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BankTransferServiceTest {

    @Test
    void validatesAccountsAmountsFundsAndOrderingKeys() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new BankAccount(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BankAccount(1, -1));

        BankTransferService transfers = new BankTransferService();
        BankAccount first = new BankAccount(1, 100);
        BankAccount second = new BankAccount(2, 100);
        assertThrows(NullPointerException.class, () -> transfers.orderedTransfer(
                null, second, 1));
        assertThrows(NullPointerException.class, () -> transfers.orderedTransfer(
                first, null, 1));
        assertThrows(IllegalArgumentException.class, () -> transfers.orderedTransfer(
                first, second, 0));
        assertThrows(IllegalArgumentException.class, () -> transfers.orderedTransfer(
                first, new BankAccount(1, 100), 1));
        assertThrows(IllegalStateException.class, () -> transfers.orderedTransfer(
                first, second, 101));
        assertThrows(IllegalArgumentException.class, () -> transfers.timedTransfer(
                first, second, 1, Duration.ofNanos(-1)));

        transfers.orderedTransfer(first, first, 10);
        assertTrue(transfers.timedTransfer(first, first, 10, Duration.ZERO));
        assertEquals(100, first.balance());
    }

    @Test
    void preventsPartialTransferWhenDestinationWouldOverflow() {
        BankTransferService transfers = new BankTransferService();
        BankAccount source = new BankAccount(1, 10);
        BankAccount destination = new BankAccount(2, Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, () -> transfers.unsafeTransfer(
                source, destination, 1));
        assertEquals(10, source.balance());
        assertEquals(Long.MAX_VALUE, destination.balance());
        assertTrue(new DeadlockDetector().detect().isEmpty());
    }

    @Test
    @Timeout(10)
    void orderedOppositeTransfersConserveBalanceWithoutDeadlock() throws Exception {
        BankAccount first = new BankAccount(1, 10_000);
        BankAccount second = new BankAccount(2, 10_000);
        BankTransferService transfers = new BankTransferService();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> forward = executor.submit(
                    () -> transferRepeatedly(transfers, first, second, start));
            Future<?> backward = executor.submit(
                    () -> transferRepeatedly(transfers, second, first, start));
            start.countDown();
            for (Future<?> future : List.of(forward, backward)) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        assertEquals(10_000, first.balance());
        assertEquals(10_000, second.balance());
        assertEquals(20_000, first.balance() + second.balance());
    }

    @Test
    @Timeout(5)
    void timedTransferRollsBackPartialLockAcquisition() throws Exception {
        BankAccount first = new BankAccount(1, 100);
        BankAccount second = new BankAccount(2, 100);
        BankTransferService transfers = new BankTransferService();
        CountDownLatch secondLocked = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> holder = executor.submit(() -> {
                second.lock().lock();
                try {
                    secondLocked.countDown();
                    releaseSecond.await();
                } finally {
                    second.lock().unlock();
                }
                return null;
            });
            secondLocked.await();
            assertFalse(transfers.timedTransfer(
                    first, second, 10, Duration.ofMillis(50)));
            releaseSecond.countDown();
            holder.get(2, TimeUnit.SECONDS);
        } finally {
            releaseSecond.countDown();
        }

        assertEquals(100, first.balance());
        assertEquals(100, second.balance());
    }

    private static Void transferRepeatedly(
            BankTransferService transfers,
            BankAccount source,
            BankAccount destination,
            CountDownLatch start) throws InterruptedException {
        start.await();
        for (int count = 0; count < 1_000; count++) {
            transfers.orderedTransfer(source, destination, 1);
        }
        return null;
    }
}
