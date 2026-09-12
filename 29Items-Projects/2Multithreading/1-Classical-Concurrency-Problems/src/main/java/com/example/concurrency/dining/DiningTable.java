package com.example.concurrency.dining;

import java.util.Objects;

/** Strategy contract for one philosopher's attempt to eat once. */
public interface DiningTable {
    int philosopherCount();

    void dine(int philosopherId, Runnable eatAction) throws InterruptedException;

    default void validate(int philosopherId, Runnable eatAction) {
        Objects.requireNonNull(eatAction, "eatAction");
        if (philosopherId < 0 || philosopherId >= philosopherCount()) {
            throw new IllegalArgumentException("invalid philosopher id: " + philosopherId);
        }
    }
}
