package com.example.pipeline.application.port;

import com.example.pipeline.domain.SensorEvent;

/**
 * Source of events (driving port of stage 1).
 *
 * <p>The synthetic generator is one implementation; pointing this at Kafka, MQTT
 * or a file replay is the seam that turns this local pipeline into a distributed
 * one without touching any stage code.
 *
 * <p><strong>Contract:</strong> implementations must be safe to call from the
 * producer thread and should be <em>pure with respect to {@code sequence}</em>
 * where possible — a deterministic mapping makes a failing run reproducible.
 */
@FunctionalInterface
public interface EventGenerator {

    /**
     * Produces the event for the given monotonically increasing sequence number.
     *
     * @param sequence event ordinal, {@code >= 0}
     * @return a valid event, never {@code null}
     */
    SensorEvent generate(long sequence);
}
