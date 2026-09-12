package com.example.concurrency.dining;

import java.util.concurrent.CountDownLatch;

/**
 * Intentionally deadlocking, one-shot implementation for thread-dump demonstrations.
 * Never invoke all philosophers on non-daemon threads inside a normal test process.
 */
public final class DeadlockDiningTable implements DiningTable {
    private final Object[] forks;
    private final CountDownLatch leftForksAcquired;

    public DeadlockDiningTable(int philosopherCount) {
        if (philosopherCount < 2) {
            throw new IllegalArgumentException("at least two philosophers are required");
        }
        forks = new Object[philosopherCount];
        for (int index = 0; index < forks.length; index++) {
            forks[index] = new Object();
        }
        leftForksAcquired = new CountDownLatch(philosopherCount);
    }

    @Override
    public int philosopherCount() {
        return forks.length;
    }

    @Override
    public void dine(int philosopherId, Runnable eatAction) throws InterruptedException {
        validate(philosopherId, eatAction);
        Object left = forks[philosopherId];
        Object right = forks[(philosopherId + 1) % forks.length];

        synchronized (left) {
            leftForksAcquired.countDown();
            leftForksAcquired.await();
            synchronized (right) {
                eatAction.run();
            }
        }
    }
}
