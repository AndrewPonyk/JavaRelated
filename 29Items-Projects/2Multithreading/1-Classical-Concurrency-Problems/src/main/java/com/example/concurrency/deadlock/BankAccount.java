package com.example.concurrency.deadlock;

import java.util.concurrent.locks.ReentrantLock;

/** Mutable account used only to demonstrate compound locking across two aggregates. */
public final class BankAccount {
    private final long id;
    private final ReentrantLock lock = new ReentrantLock(true);
    private volatile long balance;

    public BankAccount(long id, long openingBalance) {
        if (id < 0 || openingBalance < 0) {
            throw new IllegalArgumentException("id and opening balance must be non-negative");
        }
        this.id = id;
        balance = openingBalance;
    }

    public long id() {
        return id;
    }

    public long balance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    ReentrantLock lock() {
        return lock;
    }

    void transferToWithExplicitLocks(BankAccount destination, long amount) {
        if (!lock.isHeldByCurrentThread() || !destination.lock.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("both account locks must be held");
        }
        moveFunds(destination, amount);
    }

    void transferToWithMonitors(BankAccount destination, long amount) {
        if (!Thread.holdsLock(this) || !Thread.holdsLock(destination)) {
            throw new IllegalMonitorStateException("both account monitors must be held");
        }
        moveFunds(destination, amount);
    }

    private void moveFunds(BankAccount destination, long amount) {
        long nextSourceBalance = Math.subtractExact(balance, amount);
        if (nextSourceBalance < 0) {
            throw new IllegalStateException("insufficient funds in account " + id);
        }
        long nextDestinationBalance = Math.addExact(destination.balance, amount);
        balance = nextSourceBalance;
        destination.balance = nextDestinationBalance;
    }
}
