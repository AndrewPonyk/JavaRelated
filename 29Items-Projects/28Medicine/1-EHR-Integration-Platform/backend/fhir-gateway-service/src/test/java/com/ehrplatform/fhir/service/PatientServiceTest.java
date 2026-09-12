package com.ehrplatform.fhir.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import com.ehrplatform.common.exception.ResourceNotFoundException;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.fhir.domain.PatientIndex;
import com.ehrplatform.fhir.repository.PatientIndexRepository;
import java.util.Optional;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PatientService} — pure logic, no Spring context or I/O.
 * Repository, outbox, and audit are mocked; the FHIR context is real.
 */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock private PatientIndexRepository repository;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private AuditService auditService;

    private PatientService service;

    @BeforeEach
    void setUp() {
        service = new PatientService(repository, outboxPublisher, auditService, FhirContext.forR4());
    }

    private static Patient validPatient() {
        Patient p = new Patient();
        p.addIdentifier().setSystem("urn:mrn").setValue("123");
        p.addName().setFamily("Doe").addGiven("Jane");
        return p;
    }

    @Test
    void create_persistsIndexEnqueuesEventAndAudits() {
        MethodOutcome outcome = service.create(validPatient());

        assertThat(outcome.getCreated()).isTrue();
        assertThat(outcome.getId().getResourceType()).isEqualTo("Patient");
        verify(repository).save(any(PatientIndex.class));
        verify(auditService).record(eq("CREATE"), eq("Patient"), anyString());

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(outboxPublisher).enqueue(type.capture(), eq("Patient"), anyString(), anyString());
        assertThat(type.getValue()).isEqualTo("ehr.patient.created");
    }

    @Test
    void create_rejectsPatientWithoutFamilyName() {
        Patient invalid = new Patient();

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("family");
        verify(repository, never()).save(any());
    }

    @Test
    void read_throwsWhenMissing() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void read_returnsResourceFromStoredJson() {
        PatientIndex entity = new PatientIndex("abc");
        entity.setResourceJson(FhirContext.forR4().newJsonParser()
                .encodeResourceToString(validPatient()));
        when(repository.findById("abc")).thenReturn(Optional.of(entity));

        Patient result = service.read("abc");

        assertThat(result.getIdElement().getIdPart()).isEqualTo("abc");
        assertThat(result.getNameFirstRep().getFamily()).isEqualTo("Doe");
        verify(auditService).record(eq("READ"), eq("Patient"), eq("abc"));
    }

    @Test
    void update_requiresExistingPatient() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", validPatient()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesAndEmitsEvent() {
        PatientIndex entity = new PatientIndex("xyz");
        when(repository.findById("xyz")).thenReturn(Optional.of(entity));

        service.delete("xyz");

        verify(repository).delete(entity);
        verify(outboxPublisher).enqueue(eq("ehr.patient.deleted"), eq("Patient"), eq("xyz"), anyString());
        verify(auditService).record(eq("DELETE"), eq("Patient"), eq("xyz"));
    }

    @Test
    void searchByIdentifier_requiresValue() {
        assertThatThrownBy(() -> service.searchByIdentifier("urn:mrn", " "))
                .isInstanceOf(ValidationException.class);
    }
}
