package com.example.trading.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.trading.application.SimulationReport;
import com.example.trading.presentation.cli.SimulatorApplication;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Duration;
import com.example.trading.infrastructure.config.SimulatorConfig;
import org.junit.jupiter.api.Test;

class TenThousandClientLoadTest {

    @Test
    void completesTenThousandVirtualClientsWithoutCorruptingValue() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            SimulatorConfig config = new SimulatorConfig(
                    "load-test",
                    10_000,
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(25),
                    4,
                    42L,
                    new BigDecimal("100000.00"),
                    100L,
                    new BigDecimal("25"));
            try (PrintStream output = new PrintStream(OutputStream.nullOutputStream())) {
                SimulationReport report = SimulatorApplication.run(config, output);
                assertEquals(10_000, report.orderCount());
                assertTrue(report.tradeCount() > 0);
                assertEquals(0, report.aggregateProfit().compareTo(BigDecimal.ZERO));
            }
        });
    }
}
