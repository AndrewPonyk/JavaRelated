package com.example.pipeline.infrastructure.persistence;

import com.example.pipeline.application.port.AggregateRepository;
import com.example.pipeline.domain.AggregateSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default repository: keeps the latest snapshot in memory.
 *
 * <p>The default because this project is a local, single-process demonstration — a
 * database would add a dependency, a container and a migration step for no gain over the
 * console and CSV reports. {@link JdbcAggregateRepository} exists for when a run's
 * results genuinely need to outlive the process.
 *
 * <p>An {@link AtomicReference} to an immutable snapshot rather than a synchronised
 * field: writes come from the aggregation dispatcher, reads from the dashboard thread
 * and from tests, and publishing a whole immutable value means a reader can never
 * observe a half-updated map. This is the same publication pattern the aggregation stage
 * uses for its live snapshot.
 */
public final class InMemoryAggregateRepository implements AggregateRepository {

    private final AtomicReference<AggregateSnapshot> latest = new AtomicReference<>();

    @Override
    public void save(AggregateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        latest.set(snapshot);
    }

    @Override
    public Optional<AggregateSnapshot> findLatest() {
        return Optional.ofNullable(latest.get());
    }

    /** Forgets the stored snapshot — for reuse between test cases. */
    public void clear() {
        latest.set(null);
    }
}
