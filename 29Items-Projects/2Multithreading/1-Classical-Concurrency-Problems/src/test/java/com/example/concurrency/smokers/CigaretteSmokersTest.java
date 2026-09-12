package com.example.concurrency.smokers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.concurrency.smokers.CigaretteSmokers.Ingredient;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CigaretteSmokersTest {

    @Test
    void validatesArgumentsAndRejectsSupplyAfterClose() throws Exception {
        CigaretteSmokers smokers = new CigaretteSmokers(ingredient -> { });
        assertThrows(NullPointerException.class, () -> smokers.supplyFor(null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> smokers.supplyFor(Ingredient.PAPER, null));
        assertThrows(IllegalArgumentException.class, () -> smokers.supplyFor(
                Ingredient.PAPER, Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> smokers.awaitRounds(
                -1, Duration.ZERO));
        smokers.close();
        smokers.close();
        assertFalse(smokers.supplyFor(Ingredient.PAPER, Duration.ZERO));
        assertFalse(smokers.awaitRounds(1, Duration.ZERO));
    }

    @Test
    @Timeout(5)
    void propagatesWorkerFailureToAgentAndWaiter() throws Exception {
        try (CigaretteSmokers smokers = new CigaretteSmokers(ingredient -> {
            throw new IllegalStateException("smoker failed");
        })) {
            assertTrue(smokers.supplyFor(Ingredient.MATCHES, Duration.ofSeconds(1)));
            assertThrows(IllegalStateException.class, () -> smokers.awaitRounds(
                    1, Duration.ofSeconds(1)));
            assertThrows(IllegalStateException.class, () -> smokers.supplyFor(
                    Ingredient.PAPER, Duration.ofSeconds(1)));
        }
    }

    @Test
    @Timeout(10)
    void oneMatchingSmokerCompletesEachAgentRound() throws Exception {
        Map<Ingredient, AtomicInteger> counts = new EnumMap<>(Ingredient.class);
        for (Ingredient ingredient : Ingredient.values()) {
            counts.put(ingredient, new AtomicInteger());
        }

        try (CigaretteSmokers smokers = new CigaretteSmokers(
                ingredient -> counts.get(ingredient).incrementAndGet())) {
            for (int round = 0; round < 30; round++) {
                Ingredient missing = Ingredient.values()[round % Ingredient.values().length];
                assertTrue(smokers.supplyFor(missing, Duration.ofSeconds(1)));
            }
            assertTrue(smokers.awaitRounds(30, Duration.ofSeconds(2)));
            assertEquals(30, smokers.completedRounds());
        }

        for (AtomicInteger count : counts.values()) {
            assertEquals(10, count.get());
        }
    }
}
