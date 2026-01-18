package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.*;
import com.healthcare.claims.domain.repository.ClaimRepository;
import com.healthcare.claims.domain.repository.PatientRepository;
import com.healthcare.claims.domain.repository.ProviderRepository;
import com.healthcare.claims.infrastructure.kafka.ClaimEventProducer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClaimService.
 */
@QuarkusTest
class ClaimServiceTest {

    @Inject
    ClaimService claimService;

    @InjectMock
    ClaimRepository claimRepository;

    @InjectMock
    PatientRepository patientRepository;

    @InjectMock
    ProviderRepository providerRepository;

    @InjectMock
    ClaimEventProducer eventProducer;

    @InjectMock
    AdjudicationService adjudicationService;

    @InjectMock
    FraudDetectionService fraudDetectionService;

    private Patient createTestPatient() {
        return Patient.builder()
            .id(UUID.randomUUID())
            .memberId("MEM-001")
            .firstName("John")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1980, 1, 15))
            .policyNumber("POL-12345")
            .isActive(true)
            .policyStartDate(LocalDate.now().minusYears(1))
            .policyEndDate(LocalDate.now().plusYears(1))
            .build();
    }

    private Provider createTestProvider() {
        return Provider.builder()
            .id(UUID.randomUUID())
            .npi("1234567890")
            .name("Test Medical Center")
            .specialty("General Practice")
            .inNetwork(true)
            .isActive(true)
            .credentialingStatus("ACTIVE")
            .build();
    }

    private Claim createTestClaim(UUID patientId, UUID providerId) {
        return Claim.builder()
            .id(UUID.randomUUID())
            .claimNumber("CLM-TEST-001")
            .type(ClaimType.MEDICAL)
            .status(ClaimStatus.SUBMITTED)
            .amount(new BigDecimal("250.00"))
            .serviceDate(LocalDate.now().minusDays(5))
            .patientId(patientId)
            .providerId(providerId)
            .diagnosisCodes("J06.9")
            .procedureCodes("99213")
            .build();
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should submit a valid claim successfully")
    void shouldSubmitValidClaim(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        Provider testProvider = createTestProvider();
        Claim testClaim = createTestClaim(testPatient.getId(), testProvider.getId());

        when(patientRepository.findById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(testPatient));
        when(providerRepository.findById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(testProvider));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });
        doNothing().when(eventProducer).sendClaimSubmitted(any());

        asserter.assertThat(
            () -> claimService.submitClaim(testClaim),
            result -> {
                assertThat(result).isNotNull();
                assertThat(result.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should reject claim with inactive patient policy")
    void shouldRejectClaimWithInactivePatientPolicy(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        testPatient.setIsActive(false);
        Provider testProvider = createTestProvider();
        Claim testClaim = createTestClaim(testPatient.getId(), testProvider.getId());

        when(patientRepository.findById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(testPatient));
        when(providerRepository.findById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(testProvider));

        asserter.assertFailedWith(
            () -> claimService.submitClaim(testClaim),
            throwable -> assertThat(throwable.getMessage()).contains("policy is not active")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should reject claim with ineligible provider")
    void shouldRejectClaimWithIneligibleProvider(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        Provider testProvider = createTestProvider();
        testProvider.setIsActive(false);
        Claim testClaim = createTestClaim(testPatient.getId(), testProvider.getId());

        when(patientRepository.findById(testPatient.getId()))
            .thenReturn(Uni.createFrom().item(testPatient));
        when(providerRepository.findById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(testProvider));

        asserter.assertFailedWith(
            () -> claimService.submitClaim(testClaim),
            throwable -> assertThat(throwable.getMessage()).contains("not eligible to submit claims")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should retrieve claim by ID")
    void shouldRetrieveClaimById(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        Provider testProvider = createTestProvider();
        Claim testClaim = createTestClaim(testPatient.getId(), testProvider.getId());

        when(claimRepository.findById(testClaim.getId()))
            .thenReturn(Uni.createFrom().item(testClaim));

        asserter.assertThat(
            () -> claimService.getClaimById(testClaim.getId()),
            result -> {
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(testClaim.getId());
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should approve claim successfully")
    void shouldApproveClaimSuccessfully(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        Provider testProvider = createTestProvider();
        Claim testClaim = createTestClaim(testPatient.getId(), testProvider.getId());
        testClaim.setStatus(ClaimStatus.PENDING_REVIEW);

        when(claimRepository.findById(testClaim.getId()))
            .thenReturn(Uni.createFrom().item(testClaim));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });
        doNothing().when(eventProducer).sendClaimApproved(any());

        asserter.assertThat(
            () -> claimService.approveClaim(testClaim.getId(), "reviewer@example.com", "Approved after review"),
            result -> {
                assertThat(result.getStatus()).isEqualTo(ClaimStatus.APPROVED);
                assertThat(result.getReviewedBy()).isEqualTo("reviewer@example.com");
                assertThat(result.getReviewedAt()).isNotNull();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should deny claim with reason")
    void shouldDenyClaimWithReason(UniAsserter asserter) {
        Patient testPatient = createTestPatient();
        Provider testProvider = createTestProvider();
        Claim testClaim = createTestClaim(testPatient.getId(), testProvider.getId());
        testClaim.setStatus(ClaimStatus.PENDING_REVIEW);

        when(claimRepository.findById(testClaim.getId()))
            .thenReturn(Uni.createFrom().item(testClaim));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });
        doNothing().when(eventProducer).sendClaimDenied(any());

        asserter.assertThat(
            () -> claimService.denyClaim(testClaim.getId(), "Service not covered", "reviewer@example.com"),
            result -> {
                assertThat(result.getStatus()).isEqualTo(ClaimStatus.DENIED);
                assertThat(result.getDenialReason()).isEqualTo("Service not covered");
            }
        );
    }
}
