package com.example.concurrency.deadlock;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Standalone lock-ordering deadlock-prevention demonstration. */
public final class OrderedBankTransferDemo {
    private OrderedBankTransferDemo() {
    }

    public static void main(String[] args) throws Exception {
        BankAccount first = new BankAccount(1, 1_000);
        BankAccount second = new BankAccount(2, 1_000);
        BankTransferService transfers = new BankTransferService();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> forward = executor.submit(
                    () -> transferRepeatedly(transfers, first, second, start));
            Future<?> backward = executor.submit(
                    () -> transferRepeatedly(transfers, second, first, start));
            start.countDown();
            for (Future<?> future : List.of(forward, backward)) {
                future.get(3, TimeUnit.SECONDS);
            }
        }

        System.out.printf(
                "Bank Transfer [lock ordering]: transfers=200, balanceA=%d, balanceB=%d, total=%d%n",
                first.balance(),
                second.balance(),
                first.balance() + second.balance());
    }

    private static Void transferRepeatedly(
            BankTransferService transfers,
            BankAccount source,
            BankAccount destination,
            CountDownLatch start) throws InterruptedException {
        start.await();
        for (int count = 0; count < 100; count++) {
            transfers.orderedTransfer(source, destination, 1);
        }
        return null;
    }
}
