package com.example.pipeline.infrastructure.sink;

import com.example.pipeline.application.port.AggregateSink;
import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Objects;

/**
 * Prints the aggregate table to a {@link PrintStream}.
 *
 * <p>ASCII only, fixed-width columns: box-drawing characters render as mojibake in the
 * default Windows console code page, and this project's primary target is local
 * execution.
 *
 * <p>The stream is injected rather than hard-coded to {@code System.out} so a test can
 * capture the output and assert on it — the same reason the report is a value returned
 * by the orchestrator rather than something printed deep in a stage.
 */
public final class ConsoleAggregateSink implements AggregateSink {

    private static final String HEADER =
            String.format(Locale.ROOT, "%-12s %8s %12s %12s %12s", "sensor", "count", "min", "max", "avg");

    private static final String RULE = "-".repeat(HEADER.length());

    private final PrintStream out;

    /** Sink writing to {@code System.out}. */
    public ConsoleAggregateSink() {
        this(System.out);
    }

    /**
     * @param out destination stream; not closed by this sink, since it does not own it
     */
    public ConsoleAggregateSink(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void publish(AggregateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.isEmpty()) {
            out.println("no events passed the filter - nothing to aggregate");
            return;
        }
        out.println(HEADER);
        out.println(RULE);
        // Sorted so two runs of the same seed produce byte-identical output, which makes
        // the report diffable.
        for (AggregateResult result : snapshot.sortedBySensorId()) {
            out.println(result.toDisplayRow());
        }
        out.println(RULE);
        out.printf(Locale.ROOT, "%-12s %8d%n", "TOTAL", snapshot.totalCount());
        out.flush();
    }
}
