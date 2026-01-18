package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.model.ClaimType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RulesEngineService.
 */
@QuarkusTest
class RulesEngineServiceTest {

    @Inject
    RulesEngineService rulesEngineService;

    private Claim createTestClaim() {
        return Claim.builder()
            .id(UUID.randomUUID())
            .claimNumber("CLM-RULE-001")
            .type(ClaimType.MEDICAL)
            .status(ClaimStatus.PENDING_ADJUDICATION)
            .amount(new BigDecimal("500.00"))
            .serviceDate(LocalDate.now().minusDays(30))
            .patientId(UUID.randomUUID())
            .providerId(UUID.randomUUID())
            .diagnosisCodes("J06.9")
            .procedureCodes("99213")
            .build();
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should approve claim that meets all criteria")
    void shouldApproveClaimMeetingAllCriteria(UniAsserter asserter) {
        Claim testClaim = createTestClaim();

        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(testClaim),
            result -> {
                assertThat(result.isApproved()).isTrue();
                assertThat(result.ruleName()).isEqualTo("ALL_RULES_PASSED");
                assertThat(result.denialCode()).isNull();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should deny claim exceeding timely filing limit")
    void shouldDenyClaimExceedingTimelyFilingLimit(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setServiceDate(LocalDate.now().minusDays(400));

        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(testClaim),
            result -> {
                assertThat(result.isApproved()).isFalse();
                assertThat(result.ruleName()).isEqualTo("TIMELY_FILING");
                assertThat(result.denialCode()).isEqualTo("TF001");
                assertThat(result.reason()).contains("365 days");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should approve covered claim types")
    void shouldApproveCoveredClaimTypes(UniAsserter asserter) {
        // Test MEDICAL type
        Claim medicalClaim = createTestClaim();
        medicalClaim.setType(ClaimType.MEDICAL);
        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(medicalClaim),
            result -> assertThat(result.isApproved()).isTrue()
        );

        // Test DENTAL type
        Claim dentalClaim = createTestClaim();
        dentalClaim.setType(ClaimType.DENTAL);
        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(dentalClaim),
            result -> assertThat(result.isApproved()).isTrue()
        );

        // Test VISION type
        Claim visionClaim = createTestClaim();
        visionClaim.setType(ClaimType.VISION);
        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(visionClaim),
            result -> assertThat(result.isApproved()).isTrue()
        );

        // Test PHARMACY type
        Claim pharmacyClaim = createTestClaim();
        pharmacyClaim.setType(ClaimType.PHARMACY);
        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(pharmacyClaim),
            result -> assertThat(result.isApproved()).isTrue()
        );

        // Test LABORATORY type
        Claim labClaim = createTestClaim();
        labClaim.setType(ClaimType.LABORATORY);
        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(labClaim),
            result -> assertThat(result.isApproved()).isTrue()
        );

        // Test EMERGENCY type
        Claim emergencyClaim = createTestClaim();
        emergencyClaim.setType(ClaimType.EMERGENCY);
        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(emergencyClaim),
            result -> assertThat(result.isApproved()).isTrue()
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should deny claim with excessive amount for medical type")
    void shouldDenyClaimWithExcessiveAmountForMedicalType(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setType(ClaimType.MEDICAL);
        testClaim.setAmount(new BigDecimal("15000.00"));

        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(testClaim),
            result -> {
                assertThat(result.isApproved()).isFalse();
                assertThat(result.ruleName()).isEqualTo("AMOUNT_REASONABLENESS");
                assertThat(result.denialCode()).isEqualTo("AR001");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should approve high-value emergency claim within limits")
    void shouldApproveHighValueEmergencyClaimWithinLimits(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setType(ClaimType.EMERGENCY);
        testClaim.setAmount(new BigDecimal("45000.00"));

        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(testClaim),
            result -> assertThat(result.isApproved()).isTrue()
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should deny pharmacy claim exceeding limit")
    void shouldDenyPharmacyClaimExceedingLimit(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setType(ClaimType.PHARMACY);
        testClaim.setAmount(new BigDecimal("6000.00"));

        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(testClaim),
            result -> {
                assertThat(result.isApproved()).isFalse();
                assertThat(result.denialCode()).isEqualTo("AR001");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should include rule version in result")
    void shouldIncludeRuleVersionInResult(UniAsserter asserter) {
        Claim testClaim = createTestClaim();

        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(testClaim),
            result -> assertThat(result.ruleVersion()).isEqualTo("1.0")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should return first failing rule")
    void shouldReturnFirstFailingRule(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setServiceDate(LocalDate.now().minusDays(400));
        testClaim.setAmount(new BigDecimal("99999.00"));

        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(testClaim),
            result -> assertThat(result.ruleName()).isEqualTo("TIMELY_FILING")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should approve claim with boundary timely filing")
    void shouldApproveClaimWithBoundaryTimelyFiling(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setServiceDate(LocalDate.now().minusDays(365));

        asserter.assertThat(
            () -> rulesEngineService.evaluateClaim(testClaim),
            result -> assertThat(result.isApproved()).isTrue()
        );
    }
}
