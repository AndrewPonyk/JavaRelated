package com.example.concurrency.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CommonUtilitiesTest {

    @Test
    void simulationResultValidatesItsInvariant() {
        SimulationResult result = new SimulationResult(
                "bounded-buffer", 3, 10, Duration.ofMillis(5), true);

        assertEquals("bounded-buffer", result.scenario());
        assertEquals(3, result.participants());
        assertTrue(result.completed());
        assertThrows(NullPointerException.class, () -> new SimulationResult(
                null, 1, 1, Duration.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult(
                " ", 1, 1, Duration.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult(
                "test", -1, 1, Duration.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult(
                "test", 1, -1, Duration.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult(
                "test", 1, 1, Duration.ofNanos(-1), true));
    }

    @Test
    void timeoutConversionRejectsInvalidValuesAndSaturatesOverflow() {
        assertEquals(0L, Timeouts.toNanos(Duration.ZERO));
        assertEquals(1_000_000_000L, Timeouts.toNanos(Duration.ofSeconds(1)));
        assertEquals(Long.MAX_VALUE, Timeouts.toNanos(Duration.ofSeconds(Long.MAX_VALUE)));
        assertThrows(NullPointerException.class, () -> Timeouts.toNanos(null));
        assertThrows(IllegalArgumentException.class, () -> Timeouts.toNanos(Duration.ofNanos(-1)));
    }

    @Test
    void interruptionBoundaryRestoresTheFlagAndPreservesTheCause() {
        assertFalse(Thread.currentThread().isInterrupted());
        InterruptedException cause = new InterruptedException("cancelled");
        try {
            IllegalStateException result = Interruptions.restoreAndWrap("demo", cause);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(cause, result.getCause());
            assertTrue(result.getMessage().contains("demo"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void phaserLifecycleIsIdempotentAndRejectsUseAfterClose() {
        PhasedSimulation simulation = new PhasedSimulation();
        PhasedSimulation.Participant participant = simulation.register();
        participant.close();
        participant.close();
        assertThrows(IllegalStateException.class, participant::arriveAndAwaitAdvance);

        simulation.closeRegistration();
        simulation.closeRegistration();
        assertThrows(IllegalStateException.class, simulation::register);
        assertThrows(IllegalStateException.class, simulation::advanceCoordinator);
    }
}
