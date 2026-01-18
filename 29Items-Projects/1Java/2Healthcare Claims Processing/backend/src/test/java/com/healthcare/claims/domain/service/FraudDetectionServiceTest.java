package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.model.ClaimType;
import com.healthcare.claims.domain.repository.ClaimRepository;
import com.healthcare.claims.infrastructure.fraud.FraudScoringClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FraudDetectionService.
 */
@QuarkusTest
class FraudDetectionServiceTest {

    @Inject
    FraudDetectionService fraudDetectionService;

    @InjectMock
    ClaimRepository claimRepository;

    @InjectMock
    FraudScoringClient fraudScoringClient;

    private Claim createTestClaim() {
        return Claim.builder()
            .id(UUID.randomUUID())
            .claimNumber("CLM-FRAUD-001")
            .type(ClaimType.MEDICAL)
            .status(ClaimStatus.VALIDATING)
            .amount(new BigDecimal("500.00"))
            .serviceDate(LocalDate.now().minusDays(5))
            .patientId(UUID.randomUUID())
            .providerId(UUID.randomUUID())
            .diagnosisCodes("J06.9")
            .procedureCodes("99213")
            .build();
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should score claim with low fraud risk")
    void shouldScoreClaimWithLowFraudRisk(UniAsserter asserter) {
        Claim testClaim = createTestClaim();

        when(fraudScoringClient.getScore(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(0.1));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(inv -> {
                Claim c = inv.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> fraudDetectionService.scoreClaim(testClaim),
            result -> {
                assertThat(result.getFraudScore()).isLessThan(0.5);
                assertThat(result.isFraudFlagged()).isFalse();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should flag claim with high ML fraud score")
    void shouldFlagClaimWithHighMLFraudScore(UniAsserter asserter) {
        Claim testClaim = createTestClaim();

        when(fraudScoringClient.getScore(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(0.9));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(inv -> {
                Claim c = inv.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> fraudDetectionService.scoreClaim(testClaim),
            result -> assertThat(result.getFraudScore()).isGreaterThanOrEqualTo(0.5)
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should add risk for high-value claim")
    void shouldAddRiskForHighValueClaim(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setAmount(new BigDecimal("15000.00"));

        when(fraudScoringClient.getScore(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(0.1));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(inv -> {
                Claim c = inv.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> fraudDetectionService.scoreClaim(testClaim),
            result -> assertThat(result.getFraudReasons()).contains("High-value claim")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should add risk for weekend service")
    void shouldAddRiskForWeekendService(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        LocalDate saturday = LocalDate.now();
        while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) {
            saturday = saturday.minusDays(1);
        }
        testClaim.setServiceDate(saturday);

        when(fraudScoringClient.getScore(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(0.0));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(inv -> {
                Claim c = inv.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> fraudDetectionService.scoreClaim(testClaim),
            result -> assertThat(result.getFraudReasons()).contains("Service on weekend")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should add risk for round dollar amount")
    void shouldAddRiskForRoundDollarAmount(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setAmount(new BigDecimal("500.00"));

        when(fraudScoringClient.getScore(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(0.0));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(inv -> {
                Claim c = inv.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> fraudDetectionService.scoreClaim(testClaim),
            result -> assertThat(result.getFraudReasons()).contains("Round dollar amount")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should handle ML API failure gracefully")
    void shouldHandleMLAPIFailureGracefully(UniAsserter asserter) {
        Claim testClaim = createTestClaim();

        when(fraudScoringClient.getScore(any(Claim.class)))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("API unavailable")));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(inv -> {
                Claim c = inv.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> fraudDetectionService.scoreClaim(testClaim),
            result -> assertThat(result.getFraudScore()).isNotNull()
        );
    }

    @Test
    @DisplayName("Should correctly identify claims requiring fraud review")
    void shouldCorrectlyIdentifyClaimsRequiringFraudReview() {
        Claim testClaim = createTestClaim();
        testClaim.setFraudScore(0.6);
        assertThat(fraudDetectionService.requiresFraudReview(testClaim)).isTrue();

        testClaim.setFraudScore(0.3);
        assertThat(fraudDetectionService.requiresFraudReview(testClaim)).isFalse();
    }

    @Test
    @DisplayName("Should correctly identify likely fraudulent claims")
    void shouldCorrectlyIdentifyLikelyFraudulentClaims() {
        Claim testClaim = createTestClaim();
        testClaim.setFraudScore(0.8);
        assertThat(fraudDetectionService.isLikelyFraud(testClaim)).isTrue();

        testClaim.setFraudScore(0.6);
        assertThat(fraudDetectionService.isLikelyFraud(testClaim)).isFalse();
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should combine ML and rule-based scores")
    void shouldCombineMLAndRuleBasedScores(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setAmount(new BigDecimal("15000.00"));

        when(fraudScoringClient.getScore(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(0.5));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(inv -> {
                Claim c = inv.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> fraudDetectionService.scoreClaim(testClaim),
            result -> assertThat(result.getFraudScore()).isGreaterThan(0.3)
        );
    }
}
