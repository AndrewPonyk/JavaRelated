package com.example.concurrency.smokers;

import com.example.concurrency.smokers.CigaretteSmokers.Ingredient;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone agent/smokers semaphore-coordination demonstration. */
public final class CigaretteSmokersDemo {
    private CigaretteSmokersDemo() {
    }

    public static void main(String[] args) throws Exception {
        Map<Ingredient, AtomicInteger> smoked = new EnumMap<>(Ingredient.class);
        for (Ingredient ingredient : Ingredient.values()) {
            smoked.put(ingredient, new AtomicInteger());
        }

        try (CigaretteSmokers smokers = new CigaretteSmokers(
                ingredient -> smoked.get(ingredient).incrementAndGet())) {
            for (int round = 0; round < 9; round++) {
                Ingredient missing = Ingredient.values()[round % Ingredient.values().length];
                if (!smokers.supplyFor(missing, Duration.ofSeconds(1))) {
                    throw new IllegalStateException("agent timed out in round " + round);
                }
            }
            if (!smokers.awaitRounds(9, Duration.ofSeconds(2))) {
                throw new IllegalStateException("smokers did not complete all rounds");
            }
            System.out.printf(
                    "Cigarette Smokers: rounds=%d, tobaccoOwner=%d, paperOwner=%d, matchesOwner=%d%n",
                    smokers.completedRounds(),
                    smoked.get(Ingredient.TOBACCO).get(),
                    smoked.get(Ingredient.PAPER).get(),
                    smoked.get(Ingredient.MATCHES).get());
        }
    }
}
