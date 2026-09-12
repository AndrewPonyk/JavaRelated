package com.example.concurrency.smokers;

import com.example.concurrency.common.Timeouts;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Agent/smoker rendezvous in which the agent identifies the smoker's missing ingredient. */
public final class CigaretteSmokers implements AutoCloseable {
    public enum Ingredient {
        TOBACCO,
        PAPER,
        MATCHES
    }

    private final Map<Ingredient, Semaphore> smokerSignals = new EnumMap<>(Ingredient.class);
    private final Semaphore agentMaySupply = new Semaphore(1, true);
    private final ExecutorService smokers;
    private final Consumer<Ingredient> smokeAction;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicLong completedRounds = new AtomicLong();
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    private final Object completionMonitor = new Object();

    public CigaretteSmokers(Consumer<Ingredient> smokeAction) {
        this.smokeAction = Objects.requireNonNull(smokeAction, "smokeAction");
        for (Ingredient ingredient : Ingredient.values()) {
            smokerSignals.put(ingredient, new Semaphore(0, true));
        }
        smokers = Executors.newFixedThreadPool(Ingredient.values().length);
        for (Ingredient ingredient : Ingredient.values()) {
            smokers.submit(() -> smokeWhenSelected(ingredient));
        }
    }

    /**
     * Supplies the two ingredients owned by the agent and wakes the smoker who owns
     * {@code missingIngredient}. Returns false on timeout or after shutdown.
     */
    public boolean supplyFor(Ingredient missingIngredient, Duration timeout)
            throws InterruptedException {
        Objects.requireNonNull(missingIngredient, "missingIngredient");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        throwIfFailed();
        if (!open.get()
                || !agentMaySupply.tryAcquire(Timeouts.toNanos(timeout), TimeUnit.NANOSECONDS)) {
            return false;
        }
        boolean assigned = false;
        try {
            throwIfFailed();
            if (!open.get()) {
                return false;
            }
            smokerSignals.get(missingIngredient).release();
            assigned = true;
            return true;
        } finally {
            if (!assigned) {
                agentMaySupply.release();
            }
        }
    }

    /** Waits without polling until at least the requested number of rounds completes. */
    public boolean awaitRounds(long expectedRounds, Duration timeout) throws InterruptedException {
        if (expectedRounds < 0) {
            throw new IllegalArgumentException("expected rounds must be non-negative");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        long budget = Timeouts.toNanos(timeout);
        long remaining = budget;
        long started = System.nanoTime();
        synchronized (completionMonitor) {
            while (completedRounds.get() < expectedRounds
                    && failure.get() == null
                    && open.get()) {
                if (remaining <= 0L) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(completionMonitor, remaining);
                remaining = Math.max(0L, budget - (System.nanoTime() - started));
            }
        }
        throwIfFailed();
        return completedRounds.get() >= expectedRounds;
    }

    public long completedRounds() {
        return completedRounds.get();
    }

    private void smokeWhenSelected(Ingredient ingredient) {
        Semaphore signal = smokerSignals.get(ingredient);
        while (open.get()) {
            try {
                signal.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!open.get()) {
                return;
            }
            try {
                smokeAction.accept(ingredient);
                completedRounds.incrementAndGet();
                synchronized (completionMonitor) {
                    completionMonitor.notifyAll();
                }
            } catch (RuntimeException runtimeFailure) {
                failure.compareAndSet(null, runtimeFailure);
                synchronized (completionMonitor) {
                    completionMonitor.notifyAll();
                }
                return;
            } finally {
                agentMaySupply.release();
            }
        }
    }

    private void throwIfFailed() {
        RuntimeException runtimeFailure = failure.get();
        if (runtimeFailure != null) {
            throw new IllegalStateException("a smoker worker failed", runtimeFailure);
        }
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        for (Semaphore signal : smokerSignals.values()) {
            signal.release();
        }
        synchronized (completionMonitor) {
            completionMonitor.notifyAll();
        }
        smokers.shutdownNow();
        try {
            smokers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
