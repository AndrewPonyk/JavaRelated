package com.ehrplatform.events.model;

import java.time.Instant;

/**
 * Read-model projection for a patient, built from the event stream (CQRS).
 * Tracks lightweight, queryable state without touching the write-side DB.
 */
public class PatientProjection {

    private final String patientId;
    private long eventCount;
    private String lastEventType;
    private Instant lastUpdated;

    public PatientProjection(String patientId) {
        this.patientId = patientId;
    }

    public synchronized void apply(String eventType, Instant occurredAt) {
        this.eventCount++;
        this.lastEventType = eventType;
        this.lastUpdated = occurredAt;
    }

    public String getPatientId() {
        return patientId;
    }

    public long getEventCount() {
        return eventCount;
    }

    public String getLastEventType() {
        return lastEventType;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }
}
