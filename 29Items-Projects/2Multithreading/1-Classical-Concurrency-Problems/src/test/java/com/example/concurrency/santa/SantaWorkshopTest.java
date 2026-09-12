package com.example.concurrency.santa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.concurrency.santa.SantaWorkshop.WakeReason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SantaWorkshopTest {

    @Test
    void validatesIdsStateAndOperationsAfterClose() throws Exception {
        SantaWorkshop workshop = new SantaWorkshop();
        assertThrows(IllegalArgumentException.class, () -> workshop.reindeerReturns(-1));
        assertThrows(IllegalArgumentException.class, () -> workshop.reindeerReturns(9));
        assertThrows(IllegalArgumentException.class, () -> workshop.elfNeedsHelp(-1));
        assertThrows(IllegalStateException.class, workshop::prepareSleigh);
        assertThrows(IllegalStateException.class, workshop::helpElves);

        workshop.close();
        workshop.close();
        assertEquals(
                WakeReason.CLOSED,
                workshop.awaitWakeup(Duration.ZERO).orElseThrow());
        assertThrows(IllegalStateException.class, () -> workshop.reindeerReturns(0));
        assertThrows(IllegalStateException.class, () -> workshop.elfNeedsHelp(0));
        assertThrows(IllegalStateException.class, workshop::prepareSleigh);
        assertThrows(IllegalStateException.class, workshop::helpElves);
    }

    @Test
    @Timeout(5)
    void rejectsDuplicateReindeerWithoutAdvancingTheTeam() throws Exception {
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        SantaWorkshop workshop = new SantaWorkshop();
        try {
            Thread first = Thread.ofPlatform().start(() -> {
                try {
                    workshop.reindeerReturns(0);
                } catch (Throwable failure) {
                    firstFailure.set(failure);
                }
            });
            assertTrue(awaitState(first, Thread.State.WAITING, Duration.ofSeconds(1)));
            assertThrows(IllegalArgumentException.class, () -> workshop.reindeerReturns(0));
            workshop.close();
            first.join(1_000);
            assertNull(firstFailure.get());
        } finally {
            workshop.close();
        }
    }

    @Test
    @Timeout(10)
    void coordinatesCompleteReindeerAndElfGroups() throws Exception {
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

            assertEquals(
                    WakeReason.REINDEER,
                    workshop.awaitWakeup(Duration.ofSeconds(2)).orElseThrow());
            workshop.prepareSleigh();
            waitForAll(reindeer);

            List<Future<?>> elves = new ArrayList<>();
            for (int id = 0; id < SantaWorkshop.ELF_GROUP_SIZE; id++) {
                int elfId = id;
                elves.add(executor.submit(() -> {
                    workshop.elfNeedsHelp(elfId);
                    return null;
                }));
            }

            assertEquals(
                    WakeReason.ELVES,
                    workshop.awaitWakeup(Duration.ofSeconds(2)).orElseThrow());
            workshop.helpElves();
            waitForAll(elves);
            assertEquals(1, workshop.elfGroupsHelped());
        }
    }

    private static void waitForAll(List<Future<?>> futures) throws Exception {
        for (Future<?> future : futures) {
            future.get(2, TimeUnit.SECONDS);
        }
    }

    private static boolean awaitState(Thread thread, Thread.State state, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (thread.getState() == state) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }
}
