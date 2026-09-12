package com.ehrplatform.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import com.ehrplatform.events.service.ProjectionStore;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EhrEventConsumerTest {

    private final ProjectionStore store = new ProjectionStore();
    private final EhrEventConsumer consumer = new EhrEventConsumer(store);

    @Test
    void onPatientEvent_appliesToProjection() {
        consumer.onPatientEvent(new EhrEventEnvelope(
                "e1", "ehr.patient.created", "Patient", "p1",
                "fhir-gateway", Instant.now(), "trace", "{}"));

        assertThat(store.findPatient("p1")).isPresent();
    }

    @Test
    void onPatientEvent_malformedThrowsForDltRouting() {
        EhrEventEnvelope malformed = new EhrEventEnvelope(
                null, "ehr.patient.created", "Patient", null,
                "fhir-gateway", Instant.now(), "trace", "{}");

        assertThatThrownBy(() -> consumer.onPatientEvent(malformed))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
