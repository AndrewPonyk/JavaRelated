package com.example.pipeline.domain;

import java.util.List;
import java.util.Objects;

/**
 * An immutable group of events moved through a queue as one unit.
 *
 * <p>Batching exists for one reason: a {@code BlockingQueue} handoff costs a lock
 * acquisition plus a signal, which at high event rates costs more than the
 * filtering work itself. Enqueueing {@code N} events as one message divides that
 * overhead by {@code N}.
 *
 * <p><strong>Thread safety:</strong> the canonical constructor calls
 * {@link List#copyOf(java.util.Collection)}, so the batch cannot alias a list the
 * producer still mutates. Records are only <em>shallowly</em> immutable — without
 * that copy this class would be a data race waiting to happen.
 *
 * @param batchId sequential batch number assigned by the producer, {@code >= 0}
 * @param events  the events, non-null and containing no nulls (never empty)
 */
public record EventBatch(long batchId, List<SensorEvent> events) implements PipelineMessage {

    /** Copies the event list defensively and rejects empty or null-bearing batches. */
    public EventBatch {
        if (batchId < 0) {
            throw new IllegalArgumentException("batchId must be >= 0 but was " + batchId);
        }
        Objects.requireNonNull(events, "events");
        events = List.copyOf(events);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("batch " + batchId + " must contain at least one event");
        }
    }

    /** Number of events in this batch. */
    public int size() {
        return events.size();
    }
}
