package com.ehrplatform.events.service;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import com.ehrplatform.events.model.PatientProjection;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * In-memory CQRS read model built from EHR events.
 *
 * <p>Idempotent: dedupes on {@code eventId} (delivery is at-least-once). In
 * production this would be a durable store (Redis/Oracle); the projection logic
 * is identical. Scale note: swap the maps for a persistent store (Redis/Oracle)
 * to survive restarts and share across instances.
 */
@Component
public class ProjectionStore {

    private final ConcurrentHashMap<String, PatientProjection> patients = new ConcurrentHashMap<>();
    private final java.util.Set<String> seenEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong totalApplied = new AtomicLong();

    /**
     * Apply an event to the read model.
     *
     * @return true if applied, false if it was a duplicate (already seen)
     */
    public boolean apply(EhrEventEnvelope event) {
        if (!seenEventIds.add(event.eventId())) {
            return false;
        }
        // This store consumes the patient-events topic, so resourceId == patientId.
        patients.computeIfAbsent(event.resourceId(), PatientProjection::new)
                .apply(event.eventType(), event.occurredAt());
        totalApplied.incrementAndGet();
        return true;
    }

    public Optional<PatientProjection> findPatient(String patientId) {
        return Optional.ofNullable(patients.get(patientId));
    }

    public Collection<PatientProjection> allPatients() {
        return patients.values();
    }

    public long totalEventsApplied() {
        return totalApplied.get();
    }

    public int distinctPatients() {
        return patients.size();
    }
}
