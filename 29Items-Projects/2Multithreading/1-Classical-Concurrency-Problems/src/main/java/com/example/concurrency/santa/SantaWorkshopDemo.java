package com.example.concurrency.santa;

import com.example.concurrency.santa.SantaWorkshop.WakeReason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Standalone reindeer and elf-group coordination demonstration. */
public final class SantaWorkshopDemo {
    private SantaWorkshopDemo() {
    }

    public static void main(String[] args) throws Exception {
        try (SantaWorkshop workshop = new SantaWorkshop();
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> reindeer = new ArrayList<>();
            for (int id = 0; id < SantaWorkshop.REINDEER_TEAM_SIZE; id++) {
                int reindeerId = id;
                reindeer.add(executor.submit(() -> {
                    workshop.reindeerReturns(reindeerId);
                    return null;
                }));
            }
            requireWakeReason(workshop, WakeReason.REINDEER);
            workshop.prepareSleigh();
            awaitAll(reindeer);

            List<Future<?>> elves = new ArrayList<>();
            for (int id = 0; id < SantaWorkshop.ELF_GROUP_SIZE; id++) {
                int elfId = id;
                elves.add(executor.submit(() -> {
                    workshop.elfNeedsHelp(elfId);
                    return null;
                }));
            }
            requireWakeReason(workshop, WakeReason.ELVES);
            workshop.helpElves();
            awaitAll(elves);

            System.out.printf(
                    "Santa Claus: reindeerReady=%d, sleighPrepared=true, elfGroupsHelped=%d%n",
                    SantaWorkshop.REINDEER_TEAM_SIZE,
                    workshop.elfGroupsHelped());
        }
    }

    private static void requireWakeReason(SantaWorkshop workshop, WakeReason expected)
            throws InterruptedException {
        WakeReason actual = workshop.awaitWakeup(Duration.ofSeconds(2)).orElseThrow();
        if (actual != expected) {
            throw new IllegalStateException("expected " + expected + " but received " + actual);
        }
    }

    private static void awaitAll(List<Future<?>> futures) throws Exception {
        for (Future<?> future : futures) {
            future.get(2, TimeUnit.SECONDS);
        }
    }
}
