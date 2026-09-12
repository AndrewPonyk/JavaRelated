package com.example.concurrency.deadlock;

import com.example.concurrency.common.Timeouts;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Unsafe and deadlock-preventing bank transfer variants. */
public final class BankTransferService {

    /** Source-first intrinsic locking can deadlock when opposite transfers run together. */
    public void unsafeTransfer(BankAccount source, BankAccount destination, long amount) {
        validate(source, destination, amount);
        if (source == destination) {
            return;
        }
        synchronized (source) {
            synchronized (destination) {
                source.transferToWithMonitors(destination, amount);
            }
        }
    }

    /**
     * Demonstration overload. Two opposite transfers sharing a latch of count two will
     * deterministically retain their source monitor before requesting the other monitor.
     */
    public void unsafeTransfer(
            BankAccount source,
            BankAccount destination,
            long amount,
            CountDownLatch sourceLocksAcquired) throws InterruptedException {
        validate(source, destination, amount);
        Objects.requireNonNull(sourceLocksAcquired, "sourceLocksAcquired");
        if (source == destination) {
            return;
        }
        synchronized (source) {
            sourceLocksAcquired.countDown();
            sourceLocksAcquired.await();
            synchronized (destination) {
                source.transferToWithMonitors(destination, amount);
            }
        }
    }

    /** Acquires both account locks by stable account ID, removing circular wait. */
    public void orderedTransfer(BankAccount source, BankAccount destination, long amount)
            throws InterruptedException {
        validate(source, destination, amount);
        if (source == destination) {
            return;
        }
        OrderedLocks ordered = order(source, destination);
        ordered.first().lock().lockInterruptibly();
        try {
            ordered.second().lock().lockInterruptibly();
            try {
                source.transferToWithExplicitLocks(destination, amount);
            } finally {
                ordered.second().lock().unlock();
            }
        } finally {
            ordered.first().lock().unlock();
        }
    }

    /** Attempts the same ordered acquisition within one shared timeout budget. */
    public boolean timedTransfer(
            BankAccount source,
            BankAccount destination,
            long amount,
            Duration timeout) throws InterruptedException {
        validate(source, destination, amount);
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (source == destination) {
            return true;
        }

        OrderedLocks ordered = order(source, destination);
        long budget = Timeouts.toNanos(timeout);
        long started = System.nanoTime();
        ReentrantLock first = ordered.first().lock();
        ReentrantLock second = ordered.second().lock();
        if (!first.tryLock(budget, TimeUnit.NANOSECONDS)) {
            return false;
        }
        try {
            long remaining = Math.max(0L, budget - (System.nanoTime() - started));
            if (!second.tryLock(remaining, TimeUnit.NANOSECONDS)) {
                return false;
            }
            try {
                source.transferToWithExplicitLocks(destination, amount);
                return true;
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
    }

    private static OrderedLocks order(BankAccount source, BankAccount destination) {
        if (source.id() == destination.id()) {
            throw new IllegalArgumentException("distinct accounts must have distinct IDs");
        }
        return source.id() < destination.id()
                ? new OrderedLocks(source, destination)
                : new OrderedLocks(destination, source);
    }

    private static void validate(BankAccount source, BankAccount destination, long amount) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private record OrderedLocks(BankAccount first, BankAccount second) {
    }
}
