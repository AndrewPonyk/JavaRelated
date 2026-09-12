package com.rtap.streaming.common.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A raw business event as produced to {@code events.raw.v1}.
 *
 * <p>Contract notes:
 * <ul>
 *   <li>{@code eventId} is minted by the producer (UUID) — it is the end-to-end
 *       correlation key across logs and the idempotence key for audits.</li>
 *   <li>{@code occurredAt} is producer event time (epoch millis, UTC) and drives
 *       event-time windowing; broker ingestion time is deliberately ignored.</li>
 *   <li>{@code schemaVersion} is present from day 1 so the planned Avro/Schema
 *       Registry migration (ADR #4) has a hinge to swing on.</li>
 *   <li>Kept as a mutable Flink POJO (no-arg ctor + getters/setters) so Flink's
 *       POJO serializer handles it without Kryo fallback.</li>
 * </ul>
 */
public class BusinessEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String eventType;      // e.g. "orders.completed" — becomes the metric key
    private String source;         // producing system, e.g. "checkout-service"
    private long occurredAt;       // event time, epoch millis UTC
    private double value;          // the measured quantity (amount, count=1, latency ms, …)
    private Map<String, String> dimensions = new HashMap<>(); // low-cardinality tags: region, channel…
    private int schemaVersion = 1;

    public BusinessEvent() {
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public long getOccurredAt() { return occurredAt; }
    public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public Map<String, String> getDimensions() { return dimensions; }
    /** Defensive copy: Flink's serializers require mutable map instances. */
    public void setDimensions(Map<String, String> dimensions) {
        this.dimensions = dimensions == null ? new HashMap<>() : new HashMap<>(dimensions);
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BusinessEvent that)) return false;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(eventId);
    }

    @Override
    public String toString() {
        return "BusinessEvent{eventId='%s', eventType='%s', occurredAt=%d, value=%s}"
                .formatted(eventId, eventType, occurredAt, value);
    }
}
