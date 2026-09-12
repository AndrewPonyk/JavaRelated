package com.example.concurrency.dining;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/** Prevents deadlock by allowing at most N-1 philosophers to compete for forks. */
public final class ArbitratorDiningTable implements DiningTable {
    private final ReentrantLock[] forks;
    private final Semaphore arbitrator;

    public ArbitratorDiningTable(int philosopherCount) {
        if (philosopherCount < 2) {
            throw new IllegalArgumentException("at least two philosophers are required");
        }
        forks = new ReentrantLock[philosopherCount];
        for (int index = 0; index < forks.length; index++) {
            forks[index] = new ReentrantLock();
        }
        arbitrator = new Semaphore(philosopherCount - 1, true);
    }

    @Override
    public int philosopherCount() {
        return forks.length;
    }

    @Override
    public void dine(int philosopherId, Runnable eatAction) throws InterruptedException {
        validate(philosopherId, eatAction);
        ReentrantLock left = forks[philosopherId];
        ReentrantLock right = forks[(philosopherId + 1) % forks.length];

        arbitrator.acquire();
        try {
            left.lockInterruptibly();
            try {
                right.lockInterruptibly();
                try {
                    eatAction.run();
                } finally {
                    right.unlock();
                }
            } finally {
                left.unlock();
            }
        } finally {
            arbitrator.release();
        }
    }
}
