package com.ehrplatform.events.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import com.ehrplatform.events.model.PatientProjection;
import com.ehrplatform.events.service.ProjectionStore;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ProjectionControllerTest {

    private final ProjectionStore store = new ProjectionStore();
    private final ProjectionController controller = new ProjectionController(store);

    private void apply(String id) {
        store.apply(new EhrEventEnvelope("evt-" + id, "ehr.patient.created", "Patient", id,
                "src", Instant.now(), "t", "{}"));
    }

    @Test
    void patientReturnsProjectionWhenPresent() {
        apply("p1");
        ResponseEntity<PatientProjection> response = controller.patient("p1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPatientId()).isEqualTo("p1");
    }

    @Test
    void patientReturns404WhenMissing() {
        assertThat(controller.patient("missing").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void statsReflectProjectionState() {
        apply("a");
        apply("b");
        ProjectionController.Stats stats = controller.stats();

        assertThat(stats.distinctPatients()).isEqualTo(2);
        assertThat(stats.totalEventsApplied()).isEqualTo(2);
    }
}
