package com.example.pipeline.application.port;

import com.example.pipeline.domain.SensorEvent;

/**
 * Filter rule applied by every stage-2 consumer.
 *
 * <p><strong>Contract:</strong> implementations <em>must be thread-safe</em> — the
 * same instance is called concurrently by every consumer thread — and <em>must not
 * block</em>. Blocking here would idle the fixed pool and make thread count, not
 * CPU, the throughput limit.
 *
 * <p>Not {@code java.util.function.Predicate} on purpose: a named port documents
 * the threading contract and gives {@link #description()} a home for the report.
 */
public interface EventPredicate {

    /**
     * @return {@code true} to forward the event to stage 3, {@code false} to reject it
     */
    boolean test(SensorEvent event);

    /** Human-readable rule description for the final report, e.g. {@code value > 50.0}. */
    String description();

    /** Predicate that accepts everything — useful in tests and for saturation runs. */
    static EventPredicate acceptAll() {
        return new EventPredicate() {
            @Override
            public boolean test(SensorEvent event) {
                return true;
            }

            @Override
            public String description() {
                return "accept-all";
            }
        };
    }
}
