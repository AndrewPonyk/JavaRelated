package com.example.trading.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.trading.application.SimulationReport;
import com.example.trading.infrastructure.config.SimulatorConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SimulatorApplicationTest {

    @Test
    void runsTheCompleteCliFlowAndPrintsMetrics() throws Exception {
        SimulatorConfig config = config(200, Duration.ofSeconds(10));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        SimulationReport report;
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            report = SimulatorApplication.run(config, output);
        }

        String text = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(200, report.orderCount());
        assertTrue(report.tradeCount() > 0);
        assertEquals(0, report.aggregateProfit().compareTo(BigDecimal.ZERO));
        assertTrue(text.contains("Complete: 200 orders accepted."));
        assertTrue(text.contains("p95 order latency"));
    }

    static SimulatorConfig config(int traders, Duration simulationTimeout) {
        return new SimulatorConfig(
                "test",
                traders,
                Duration.ofSeconds(1),
                simulationTimeout,
                2,
                42L,
                new BigDecimal("100000.00"),
                100L,
                new BigDecimal("25"));
    }
}
