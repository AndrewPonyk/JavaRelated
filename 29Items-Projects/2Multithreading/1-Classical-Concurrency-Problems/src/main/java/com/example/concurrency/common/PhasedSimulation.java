package com.example.concurrency.common;

import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small reusable phase gate for demos that need coordinated start and completion phases. */
public final class PhasedSimulation {
    private final Phaser phaser = new Phaser(1);
    private final AtomicBoolean registrationOpen = new AtomicBoolean(true);

    public synchronized Participant register() {
        if (!registrationOpen.get()) {
            throw new IllegalStateException("registration is closed");
        }
        phaser.register();
        return new Participant(phaser);
    }

    public synchronized int advanceCoordinator() {
        if (!registrationOpen.get()) {
            throw new IllegalStateException("coordinator is closed");
        }
        return phaser.arriveAndAwaitAdvance();
    }

    public synchronized void closeRegistration() {
        if (registrationOpen.compareAndSet(true, false)) {
            phaser.arriveAndDeregister();
        }
    }

    public static final class Participant implements AutoCloseable {
        private final Phaser phaser;
        private final AtomicBoolean registered = new AtomicBoolean(true);

        private Participant(Phaser phaser) {
            this.phaser = phaser;
        }

        public int arriveAndAwaitAdvance() {
            if (!registered.get()) {
                throw new IllegalStateException("participant is already deregistered");
            }
            return phaser.arriveAndAwaitAdvance();
        }

        @Override
        public void close() {
            if (registered.compareAndSet(true, false)) {
                phaser.arriveAndDeregister();
            }
        }
    }
}
