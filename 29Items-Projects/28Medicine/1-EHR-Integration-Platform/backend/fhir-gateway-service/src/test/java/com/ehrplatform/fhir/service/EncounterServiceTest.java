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
import com.ehrplatform.common.exception.ResourceNotFoundException;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.fhir.domain.EncounterIndex;
import com.ehrplatform.fhir.repository.EncounterIndexRepository;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock private EncounterIndexRepository repository;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private AuditService auditService;

    private EncounterService service;

    @BeforeEach
    void setUp() {
        service = new EncounterService(repository, outboxPublisher, auditService, FhirContext.forR4());
    }

    private static Encounter validEncounter() {
        Encounter e = new Encounter();
        e.setStatus(Encounter.EncounterStatus.INPROGRESS);
        e.getClass_().setCode("IMP");
        e.getSubject().setReference("Patient/p1");
        return e;
    }

    @Test
    void create_projectsPatientAndEmitsEvent() {
        MethodOutcome outcome = service.create(validEncounter());

        assertThat(outcome.getCreated()).isTrue();
        verify(repository).save(any(EncounterIndex.class));
        verify(outboxPublisher).enqueue(eq("ehr.encounter.created"), eq("Encounter"), anyString(), anyString());
        verify(auditService).record(eq("CREATE"), eq("Encounter"), anyString());
    }

    @Test
    void create_rejectsWithoutStatus() {
        Encounter e = new Encounter();
        e.getSubject().setReference("Patient/p1");
        assertThatThrownBy(() -> service.create(e))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("status");
    }

    @Test
    void searchByPatient_mapsResults() {
        EncounterIndex idx = new EncounterIndex("e1");
        idx.setResourceJson(FhirContext.forR4().newJsonParser().encodeResourceToString(validEncounter()));
        when(repository.findByPatientIdOrderByPeriodStartDesc("p1")).thenReturn(List.of(idx));

        List<Encounter> result = service.searchByPatient("p1");

        assertThat(result).hasSize(1);
        verify(auditService).record(eq("SEARCH"), eq("Encounter"), eq("patient=p1"));
    }

    @Test
    void delete_removesAndEmitsEvent() {
        EncounterIndex idx = new EncounterIndex("e9");
        when(repository.findById("e9")).thenReturn(Optional.of(idx));

        service.delete("e9");

        verify(repository).delete(idx);
        verify(outboxPublisher).enqueue(eq("ehr.encounter.deleted"), eq("Encounter"), eq("e9"), anyString());
    }

    @Test
    void read_throwsWhenMissing() {
        when(repository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.read("nope")).isInstanceOf(ResourceNotFoundException.class);
    }
}
