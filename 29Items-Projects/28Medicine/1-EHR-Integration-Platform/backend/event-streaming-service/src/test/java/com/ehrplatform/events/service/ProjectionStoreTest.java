package com.ehrplatform.events.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProjectionStoreTest {

    private final ProjectionStore store = new ProjectionStore();

    private EhrEventEnvelope event(String id, String patientId, String type) {
        return new EhrEventEnvelope(id, type, "Patient", patientId,
                "fhir-gateway", Instant.now(), "trace", "{}");
    }

    @Test
    void apply_buildsProjectionAndCountsEvents() {
        assertThat(store.apply(event("e1", "p1", "ehr.patient.created"))).isTrue();
        assertThat(store.apply(event("e2", "p1", "ehr.patient.updated"))).isTrue();

        var projection = store.findPatient("p1").orElseThrow();
        assertThat(projection.getEventCount()).isEqualTo(2);
        assertThat(projection.getLastEventType()).isEqualTo("ehr.patient.updated");
        assertThat(store.totalEventsApplied()).isEqualTo(2);
        assertThat(store.distinctPatients()).isEqualTo(1);
    }

    @Test
    void apply_isIdempotentOnDuplicateEventId() {
        assertThat(store.apply(event("dup", "p9", "ehr.patient.created"))).isTrue();
        assertThat(store.apply(event("dup", "p9", "ehr.patient.created"))).isFalse();

        assertThat(store.findPatient("p9").orElseThrow().getEventCount()).isEqualTo(1);
    }

    @Test
    void findPatient_missingReturnsEmpty() {
        assertThat(store.findPatient("nobody")).isEmpty();
    }
}
