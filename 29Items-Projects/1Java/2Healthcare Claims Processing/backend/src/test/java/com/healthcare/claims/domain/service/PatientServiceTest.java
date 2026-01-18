package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Patient;
import com.healthcare.claims.domain.repository.PatientRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PatientService.
 */
@QuarkusTest
class PatientServiceTest {

    @Inject
    PatientService patientService;

    @InjectMock
    PatientRepository patientRepository;

    private Patient createTestPatient() {
        return Patient.builder()
            .id(UUID.randomUUID())
            .memberId("MEM-TEST-001")
            .firstName("Jane")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 5, 15))
            .policyNumber("POL-TEST-001")
            .groupNumber("GRP-100")
            .policyStartDate(LocalDate.of(2024, 1, 1))
            .policyEndDate(LocalDate.of(2024, 12, 31))
            .email("jane.doe@email.com")
            .phone("555-1234")
            .isActive(true)
            .build();
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should create new patient successfully")
    void shouldCreateNewPatientSuccessfully(UniAsserter asserter) {
        Patient testPatient = createTestPatient();

        when(patientRepository.existsByMemberId(anyString()))
            .thenReturn(Uni.createFrom().item(false));
        when(patientRepository.persist(any(Patient.class)))
            .thenAnswer(inv -> {
                Patient p = inv.getArgument(0, Patient.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> patientService.createPatient(testPatient),
            result -> {
                assertThat(result).isNotNull();
                assertThat(result.getMemberId()).isEqualTo("MEM-TEST-001");
                assertThat(result.getIsActive()).isTrue();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should reject duplicate member ID")
    void shouldRejectDuplicateMemberId(UniAsserter asserter) {
        Patient testPatient = createTestPatient();

        when(patientRepository.existsByMemberId(anyString()))
            .thenReturn(Uni.createFrom().item(true));

        asserter.assertFailedWith(
            () -> patientService.createPatient(testPatient),
            throwable -> assertThat(throwable.getMessage()).contains("already exists")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should retrieve patient by ID")
    void shouldRetrievePatientById(UniAsserter asserter) {
        Patient testPatient = createTestPatient();

        when(patientRepository.findById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(testPatient));

        asserter.assertThat(
            () -> patientService.getPatientById(testPatient.getId()),
            result -> {
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(testPatient.getId());
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should retrieve patient by member ID")
    void shouldRetrievePatientByMemberId(UniAsserter asserter) {
        Patient testPatient = createTestPatient();

        when(patientRepository.findByMemberId("MEM-TEST-001"))
            .thenReturn(Uni.createFrom().item(testPatient));

        asserter.assertThat(
            () -> patientService.getPatientByMemberId("MEM-TEST-001"),
            result -> {
                assertThat(result).isNotNull();
                assertThat(result.getMemberId()).isEqualTo("MEM-TEST-001");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should update patient successfully")
    void shouldUpdatePatientSuccessfully(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        Patient updatedPatient = Patient.builder()
            .memberId("MEM-TEST-001")
            .firstName("Jane")
            .lastName("Smith")
            .dateOfBirth(LocalDate.of(1990, 5, 15))
            .policyNumber("POL-TEST-001")
            .email("jane.smith@email.com")
            .isActive(true)
            .build();

        when(patientRepository.findById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(testPatient));
        when(patientRepository.persist(any(Patient.class)))
            .thenAnswer(inv -> {
                Patient p = inv.getArgument(0, Patient.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> patientService.updatePatient(testPatient.getId(), updatedPatient),
            result -> {
                assertThat(result.getLastName()).isEqualTo("Smith");
                assertThat(result.getEmail()).isEqualTo("jane.smith@email.com");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should reject update when changing to existing member ID")
    void shouldRejectUpdateWhenChangingToExistingMemberId(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        Patient updatedPatient = Patient.builder()
            .memberId("MEM-EXISTING")
            .firstName("Jane")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 5, 15))
            .policyNumber("POL-TEST-001")
            .build();

        when(patientRepository.findById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(testPatient));
        when(patientRepository.existsByMemberId("MEM-EXISTING"))
            .thenReturn(Uni.createFrom().item(true));

        asserter.assertFailedWith(
            () -> patientService.updatePatient(testPatient.getId(), updatedPatient),
            throwable -> assertThat(throwable.getMessage()).contains("already in use")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should deactivate patient successfully")
    void shouldDeactivatePatientSuccessfully(UniAsserter asserter) {
        Patient testPatient = createTestPatient();

        when(patientRepository.findById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(testPatient));
        when(patientRepository.persist(any(Patient.class)))
            .thenAnswer(inv -> {
                Patient p = inv.getArgument(0, Patient.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> patientService.deactivatePatient(testPatient.getId()),
            result -> assertThat(result.getIsActive()).isFalse()
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should reactivate patient successfully")
    void shouldReactivatePatientSuccessfully(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        testPatient.setIsActive(false);

        when(patientRepository.findById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(testPatient));
        when(patientRepository.persist(any(Patient.class)))
            .thenAnswer(inv -> {
                Patient p = inv.getArgument(0, Patient.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> patientService.reactivatePatient(testPatient.getId()),
            result -> assertThat(result.getIsActive()).isTrue()
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should delete patient successfully")
    void shouldDeletePatientSuccessfully(UniAsserter asserter) {
        Patient testPatient = createTestPatient();

        when(patientRepository.deleteById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(true));

        asserter.assertThat(
            () -> patientService.deletePatient(testPatient.getId()),
            result -> assertThat(result).isTrue()
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should return false when deleting non-existent patient")
    void shouldReturnFalseWhenDeletingNonExistentPatient(UniAsserter asserter) {
        UUID nonExistentId = UUID.randomUUID();

        when(patientRepository.deleteById(nonExistentId))
            .thenReturn(Uni.createFrom().item(false));

        asserter.assertThat(
            () -> patientService.deletePatient(nonExistentId),
            result -> assertThat(result).isFalse()
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should search patients by name")
    void shouldSearchPatientsByName(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        List<Patient> patients = List.of(testPatient);

        when(patientRepository.list(anyString(), (Object[]) any()))
            .thenReturn(Uni.createFrom().item(patients));

        asserter.assertThat(
            () -> patientService.searchPatientsByName("jane"),
            result -> {
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getFirstName()).isEqualTo("Jane");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should get active patients")
    void shouldGetActivePatients(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        List<Patient> activePatients = List.of(testPatient);

        when(patientRepository.findActivePatients())
            .thenReturn(Uni.createFrom().item(activePatients));

        asserter.assertThat(
            () -> patientService.getActivePatients(),
            result -> {
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getIsActive()).isTrue();
            }
        );
    }
}
