package com.example.concurrency.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone dynamic-party Phaser demonstration. */
public final class PhasedSimulationDemo {
    private PhasedSimulationDemo() {
    }

    public static void main(String[] args) throws Exception {
        PhasedSimulation simulation = new PhasedSimulation();
        List<PhasedSimulation.Participant> participants = new ArrayList<>();
        for (int count = 0; count < 3; count++) {
            participants.add(simulation.register());
        }
        AtomicInteger completed = new AtomicInteger();

        int phase;
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            List<Future<?>> futures = new ArrayList<>();
            for (PhasedSimulation.Participant participant : participants) {
                futures.add(executor.submit(() -> {
                    try (participant) {
                        participant.arriveAndAwaitAdvance();
                        completed.incrementAndGet();
                    }
                    return null;
                }));
            }
            phase = simulation.advanceCoordinator();
            for (Future<?> future : futures) {
                future.get(2, TimeUnit.SECONDS);
            }
        } finally {
            simulation.closeRegistration();
        }

        System.out.printf(
                "Phaser: registeredWorkers=3, advancedToPhase=%d, completedWorkers=%d%n",
                phase,
                completed.get());
    }
}
