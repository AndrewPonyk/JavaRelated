package com.example.concurrency.santa;

import com.example.concurrency.common.Timeouts;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** One-season Santa coordinator: nine reindeer and reusable groups of three elves. */
public final class SantaWorkshop implements AutoCloseable {
    public enum WakeReason {
        REINDEER,
        ELVES,
        CLOSED
    }

    public static final int REINDEER_TEAM_SIZE = 9;
    public static final int ELF_GROUP_SIZE = 3;

    private final CountDownLatch reindeerArrivals = new CountDownLatch(REINDEER_TEAM_SIZE);
    private final CountDownLatch sleighReady = new CountDownLatch(1);
    private final Semaphore elfSlots = new Semaphore(ELF_GROUP_SIZE, true);
    private final Semaphore santaWakeups = new Semaphore(0, true);
    private final Object elfGroupMonitor = new Object();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean reindeerWakeupSent = new AtomicBoolean();
    private final AtomicBoolean sleighPrepared = new AtomicBoolean();
    private final AtomicInteger elfGroupsHelped = new AtomicInteger();
    private final Set<Integer> returnedReindeer = ConcurrentHashMap.newKeySet();
    private ElfGroup currentElfGroup = new ElfGroup();

    public void reindeerReturns(int reindeerId) throws InterruptedException {
        if (reindeerId < 0 || reindeerId >= REINDEER_TEAM_SIZE) {
            throw new IllegalArgumentException("reindeer id must be between 0 and 8");
        }
        if (!open.get()) {
            throw new IllegalStateException("the workshop is closed");
        }
        if (!returnedReindeer.add(reindeerId)) {
            throw new IllegalArgumentException("reindeer already returned: " + reindeerId);
        }
        reindeerArrivals.countDown();
        if (reindeerArrivals.getCount() == 0L
                && reindeerWakeupSent.compareAndSet(false, true)) {
            santaWakeups.release();
        }
        awaitGroupUninterruptiblyThenRestore(sleighReady);
    }

    public void elfNeedsHelp(int elfId) throws InterruptedException {
        if (elfId < 0) {
            throw new IllegalArgumentException("elf id must be non-negative");
        }
        if (!open.get()) {
            throw new IllegalStateException("the workshop is closed");
        }
        elfSlots.acquire();
        ElfGroup group;
        try {
            synchronized (elfGroupMonitor) {
                if (!open.get()) {
                    throw new IllegalStateException("the workshop is closed");
                }
                group = currentElfGroup;
                if (!group.elfIds().add(elfId)) {
                    throw new IllegalArgumentException("elf already joined this group: " + elfId);
                }
                group.arrivals().countDown();
                if (group.arrivals().getCount() == 0L) {
                    santaWakeups.release();
                }
            }
            awaitGroupUninterruptiblyThenRestore(group.helped());
        } finally {
            elfSlots.release();
        }
    }

    public Optional<WakeReason> awaitWakeup(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (!santaWakeups.tryAcquire(Timeouts.toNanos(timeout), TimeUnit.NANOSECONDS)) {
            return Optional.empty();
        }
        if (!open.get()) {
            return Optional.of(WakeReason.CLOSED);
        }
        if (reindeerArrivals.getCount() == 0L && !sleighPrepared.get()) {
            return Optional.of(WakeReason.REINDEER);
        }
        synchronized (elfGroupMonitor) {
            if (currentElfGroup.arrivals().getCount() == 0L) {
                return Optional.of(WakeReason.ELVES);
            }
        }
        return Optional.empty();
    }

    public void prepareSleigh() {
        if (!open.get()) {
            throw new IllegalStateException("the workshop is closed");
        }
        if (reindeerArrivals.getCount() != 0L) {
            throw new IllegalStateException("the complete reindeer team has not arrived");
        }
        if (sleighPrepared.compareAndSet(false, true)) {
            sleighReady.countDown();
        }
    }

    public void helpElves() {
        if (!open.get()) {
            throw new IllegalStateException("the workshop is closed");
        }
        ElfGroup group;
        synchronized (elfGroupMonitor) {
            if (currentElfGroup.arrivals().getCount() != 0L) {
                throw new IllegalStateException("a complete group of elves is not waiting");
            }
            group = currentElfGroup;
            currentElfGroup = new ElfGroup();
        }
        elfGroupsHelped.incrementAndGet();
        group.helped().countDown();
    }

    public int elfGroupsHelped() {
        return elfGroupsHelped.get();
    }

    private static void awaitGroupUninterruptiblyThenRestore(CountDownLatch latch)
            throws InterruptedException {
        boolean interrupted = false;
        while (latch.getCount() > 0L) {
            try {
                latch.await();
            } catch (InterruptedException cancellation) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("interrupted while completing a formed group");
        }
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        sleighReady.countDown();
        synchronized (elfGroupMonitor) {
            currentElfGroup.helped().countDown();
        }
        santaWakeups.release();
    }

    private record ElfGroup(
            CountDownLatch arrivals,
            CountDownLatch helped,
            Set<Integer> elfIds) {
        private ElfGroup() {
            this(
                    new CountDownLatch(ELF_GROUP_SIZE),
                    new CountDownLatch(1),
                    ConcurrentHashMap.newKeySet());
        }
    }
}
