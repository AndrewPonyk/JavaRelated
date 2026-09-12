package com.example.pipeline.application.port;

import com.example.pipeline.domain.AggregateSnapshot;
import java.util.Optional;

/**
 * Persistence boundary for aggregate results.
 *
 * <p>Separate from {@link AggregateSink} because they answer different questions:
 * a sink <em>reports</em> (console, CSV, dashboard), a repository <em>stores and
 * reads back</em>. Conflating them is how "just log it" quietly becomes the
 * system of record.
 *
 * <p><strong>Contract:</strong> thread-safe; may block on IO.
 */
public interface AggregateRepository {

    /** Stores (or replaces) the latest snapshot for the current run. */
    void save(AggregateSnapshot snapshot);

    /** Most recently stored snapshot, or empty if nothing has been stored. */
    Optional<AggregateSnapshot> findLatest();
}
