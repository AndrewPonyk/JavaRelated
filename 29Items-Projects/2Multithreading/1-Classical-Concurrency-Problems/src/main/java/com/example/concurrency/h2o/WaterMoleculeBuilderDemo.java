package com.example.concurrency.h2o;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone two-hydrogen/one-oxygen barrier demonstration. */
public final class WaterMoleculeBuilderDemo {
    private WaterMoleculeBuilderDemo() {
    }

    public static void main(String[] args) throws Exception {
        WaterMoleculeBuilder builder = new WaterMoleculeBuilder();
        AtomicInteger hydrogenBonds = new AtomicInteger();
        AtomicInteger oxygenBonds = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(9)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int atom = 0; atom < 6; atom++) {
                futures.add(executor.submit(() -> {
                    builder.hydrogen(hydrogenBonds::incrementAndGet);
                    return null;
                }));
            }
            for (int atom = 0; atom < 3; atom++) {
                futures.add(executor.submit(() -> {
                    builder.oxygen(oxygenBonds::incrementAndGet);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(3, TimeUnit.SECONDS);
            }
        }

        System.out.printf(
                "H2O Builder: molecules=%d, hydrogenAtoms=%d, oxygenAtoms=%d, ratioValid=%s%n",
                builder.moleculeCount(),
                hydrogenBonds.get(),
                oxygenBonds.get(),
                hydrogenBonds.get() == oxygenBonds.get() * 2);
    }
}
