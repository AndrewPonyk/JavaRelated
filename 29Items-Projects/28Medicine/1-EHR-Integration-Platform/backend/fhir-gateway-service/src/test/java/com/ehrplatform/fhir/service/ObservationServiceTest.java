package com.ehrplatform.fhir.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.fhir.domain.ObservationIndex;
import com.ehrplatform.fhir.repository.ObservationIndexRepository;
import java.util.List;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObservationServiceTest {

    @Mock private ObservationIndexRepository repository;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private AuditService auditService;

    private ObservationService service;

    @BeforeEach
    void setUp() {
        service = new ObservationService(repository, outboxPublisher, auditService, FhirContext.forR4());
    }

    private static Observation validObservation() {
        Observation o = new Observation();
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.getCode().addCoding().setSystem("http://loinc.org").setCode("8867-4");
        o.getSubject().setReference("Patient/p1");
        return o;
    }

    @Test
    void create_projectsPatientAndCodeAndEmitsEvent() {
        MethodOutcome outcome = service.create(validObservation());

        assertThat(outcome.getCreated()).isTrue();
        verify(repository).save(any(ObservationIndex.class));
        verify(outboxPublisher).enqueue(eq("ehr.observation.created"), eq("Observation"), anyString(), anyString());
        verify(auditService).record(eq("CREATE"), eq("Observation"), anyString());
    }

    @Test
    void create_rejectsWithoutSubject() {
        Observation o = new Observation();
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.getCode().addCoding().setSystem("http://loinc.org").setCode("8867-4");

        assertThatThrownBy(() -> service.create(o))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void searchByPatient_mapsResults() {
        ObservationIndex idx = new ObservationIndex("o1");
        idx.setResourceJson(FhirContext.forR4().newJsonParser()
                .encodeResourceToString(validObservation()));
        when(repository.findByPatientIdOrderByEffectiveAtDesc("p1")).thenReturn(List.of(idx));

        List<Observation> result = service.searchByPatient("p1");

        assertThat(result).hasSize(1);
        verify(auditService).record(eq("SEARCH"), eq("Observation"), eq("patient=p1"));
    }
}
