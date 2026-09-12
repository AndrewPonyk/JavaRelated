package com.example.pipeline.infrastructure.sink;

import com.example.pipeline.application.port.AggregateSink;
import com.example.pipeline.domain.AggregateSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fans one snapshot out to several sinks.
 *
 * <p>Exists so {@code AggregationStage} can keep a single {@link AggregateSink} field —
 * one collaborator, one contract — while a run still writes both the console table and the
 * CSV report. The alternative, a list inside the stage, would put a presentation concern
 * (how many outputs there are) into the application layer.
 *
 * <p><strong>One failing sink must not hide the others.</strong> Every delegate is
 * attempted; failures are collected and rethrown once at the end. A CSV write that fails
 * on a read-only directory therefore still leaves the console table on screen, and the
 * aggregation stage still counts exactly one error. Suppressing later failures onto the
 * first keeps them all in the log rather than losing all but one.
 */
public final class CompositeAggregateSink implements AggregateSink {

    private static final Logger LOG = Logger.getLogger(CompositeAggregateSink.class.getName());

    private final List<AggregateSink> delegates;

    /**
     * @param delegates sinks to publish to, in order; at least one
     */
    public CompositeAggregateSink(List<AggregateSink> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates"));
        if (this.delegates.isEmpty()) {
            throw new IllegalArgumentException("at least one delegate sink is required");
        }
    }

    /** Convenience factory for the common two-sink case. */
    public static CompositeAggregateSink of(AggregateSink first, AggregateSink... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        List<AggregateSink> all = new ArrayList<>(rest.length + 1);
        all.add(first);
        for (AggregateSink sink : rest) {
            all.add(Objects.requireNonNull(sink, "sink"));
        }
        return new CompositeAggregateSink(all);
    }

    @Override
    public void publish(AggregateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        RuntimeException failure = null;
        for (AggregateSink delegate : delegates) {
            try {
                delegate.publish(snapshot);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "sink " + delegate.getClass().getSimpleName() + " failed", e);
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** How many sinks this composite writes to. */
    public int size() {
        return delegates.size();
    }
}
