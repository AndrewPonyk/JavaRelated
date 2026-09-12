package com.example.concurrency.h2o;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class WaterMoleculeBuilderTest {

    @Test
    void rejectsMissingBondActions() {
        WaterMoleculeBuilder builder = new WaterMoleculeBuilder();
        assertThrows(NullPointerException.class, () -> builder.hydrogen(null));
        assertThrows(NullPointerException.class, () -> builder.oxygen(null));
    }

    @Test
    @Timeout(10)
    void formsOnlyTwoHydrogenOneOxygenBarrierGenerations() throws Exception {
        WaterMoleculeBuilder builder = new WaterMoleculeBuilder();
        List<String> bonded = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService executor = Executors.newFixedThreadPool(9)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int hydrogen = 0; hydrogen < 6; hydrogen++) {
                futures.add(executor.submit(() -> {
                    builder.hydrogen(() -> bonded.add("H"));
                    return null;
                }));
            }
            for (int oxygen = 0; oxygen < 3; oxygen++) {
                futures.add(executor.submit(() -> {
                    builder.oxygen(() -> bonded.add("O"));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        assertEquals(3, builder.moleculeCount());
        assertEquals(9, bonded.size());
        for (int start = 0; start < bonded.size(); start += 3) {
            List<String> molecule = bonded.subList(start, start + 3);
            assertEquals(2, molecule.stream().filter("H"::equals).count());
            assertEquals(1, molecule.stream().filter("O"::equals).count());
        }
    }
}
