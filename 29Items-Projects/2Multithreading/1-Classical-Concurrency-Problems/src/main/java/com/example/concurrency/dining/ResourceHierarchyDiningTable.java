package com.example.concurrency.dining;

import java.util.concurrent.locks.ReentrantLock;

/** Prevents circular wait by acquiring every pair of forks in one global order. */
public final class ResourceHierarchyDiningTable implements DiningTable {
    private final ReentrantLock[] forks;

    public ResourceHierarchyDiningTable(int philosopherCount) {
        if (philosopherCount < 2) {
            throw new IllegalArgumentException("at least two philosophers are required");
        }
        forks = new ReentrantLock[philosopherCount];
        for (int index = 0; index < forks.length; index++) {
            forks[index] = new ReentrantLock(true);
        }
    }

    @Override
    public int philosopherCount() {
        return forks.length;
    }

    @Override
    public void dine(int philosopherId, Runnable eatAction) throws InterruptedException {
        validate(philosopherId, eatAction);
        int adjacentFork = (philosopherId + 1) % forks.length;
        ReentrantLock first = forks[Math.min(philosopherId, adjacentFork)];
        ReentrantLock second = forks[Math.max(philosopherId, adjacentFork)];

        first.lockInterruptibly();
        try {
            second.lockInterruptibly();
            try {
                eatAction.run();
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
    }
}
