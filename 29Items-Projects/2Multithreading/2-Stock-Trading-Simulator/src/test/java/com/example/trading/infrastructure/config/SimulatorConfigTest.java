package com.example.trading.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulatorConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsProfilePropertiesAndAppliesEnvironmentPrecedence() throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("application-test.properties"),
                "sim.trader-count=50\n"
                        + "sim.price-timeout-ms=500\n"
                        + "sim.simulation-timeout-ms=5000\n"
                        + "sim.profit-parallelism=2\n"
                        + "sim.random-seed=7\n"
                        + "sim.initial-cash=5000.00\n"
                        + "sim.initial-position=20\n"
                        + "sim.max-price-deviation-percent=10\n");

        SimulatorConfig config = SimulatorConfig.load(
                temporaryDirectory,
                Map.of("SIM_ENV", "test", "SIM_TRADER_COUNT", "75"));

        assertEquals("test", config.environment());
        assertEquals(75, config.traderCount());
        assertEquals(Duration.ofMillis(500), config.priceTimeout());
        assertEquals(7L, config.randomSeed());
        assertEquals(new BigDecimal("5000.00"), config.initialCash());
        assertEquals(20L, config.initialPosition());
    }

    @Test
    void usesDefaultsForMissingProfileAndRejectsMalformedValues() {
        SimulatorConfig defaults = SimulatorConfig.load(
                temporaryDirectory,
                Map.of("SIM_ENV", "missing"));
        assertEquals(10_000, defaults.traderCount());

        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.load(
                temporaryDirectory,
                Map.of("SIM_TRADER_COUNT", "not-a-number")));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.load(
                temporaryDirectory,
                Map.of("SIM_ENV", "../prod")));
        assertThrows(IllegalArgumentException.class, () -> new SimulatorConfig(
                "dev", 0, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1L,
                BigDecimal.ZERO, 0L, BigDecimal.ZERO));
    }
}
