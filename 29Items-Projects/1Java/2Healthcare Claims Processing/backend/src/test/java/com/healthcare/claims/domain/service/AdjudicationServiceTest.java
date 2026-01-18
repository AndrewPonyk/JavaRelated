package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.*;
import com.healthcare.claims.domain.repository.ClaimRepository;
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
 * Unit tests for AdjudicationService.
 */
@QuarkusTest
class AdjudicationServiceTest {

    @Inject
    AdjudicationService adjudicationService;

    @InjectMock
    ClaimRepository claimRepository;

    @InjectMock
    RulesEngineService rulesEngineService;

    private Claim createTestClaim() {
        return Claim.builder()
            .id(UUID.randomUUID())
            .claimNumber("CLM-ADJ-001")
            .type(ClaimType.MEDICAL)
            .status(ClaimStatus.PENDING_ADJUDICATION)
            .amount(new BigDecimal("250.00"))
            .serviceDate(LocalDate.now().minusDays(5))
            .patientId(UUID.randomUUID())
            .providerId(UUID.randomUUID())
            .diagnosisCodes("J06.9")
            .procedureCodes("99213")
            .build();
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should auto-adjudicate eligible low-value claim")
    void shouldAutoAdjudicateEligibleClaim(UniAsserter asserter) {
        Claim testClaim = createTestClaim();

        AdjudicationService.RuleResult ruleResult = new AdjudicationService.RuleResult(
            "ALL_RULES_PASSED",
            "1.0",
            true,
            "Claim meets all criteria",
            null
        );

        when(rulesEngineService.evaluateClaim(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(ruleResult));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> adjudicationService.adjudicateClaim(testClaim),
            result -> {
                assertThat(result.getStatus()).isEqualTo(ClaimStatus.APPROVED);
                assertThat(result.getAllowedAmount()).isNotNull();
                assertThat(result.getAdjudicationResults()).isNotEmpty();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should deny claim when rules fail")
    void shouldDenyClaimWhenRulesFail(UniAsserter asserter) {
        Claim testClaim = createTestClaim();

        AdjudicationService.RuleResult ruleResult = new AdjudicationService.RuleResult(
            "TIMELY_FILING",
            "1.0",
            false,
            "Claim exceeds timely filing limit",
            "TF001"
        );

        when(rulesEngineService.evaluateClaim(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(ruleResult));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> adjudicationService.adjudicateClaim(testClaim),
            result -> {
                assertThat(result.getStatus()).isEqualTo(ClaimStatus.DENIED);
                assertThat(result.getDenialReason()).contains("timely filing");
                assertThat(result.getAdjudicationResults()).isNotEmpty();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should route high-value claim to manual review")
    void shouldRouteHighValueClaimToManualReview(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setAmount(new BigDecimal("5000.00"));

        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> adjudicationService.adjudicateClaim(testClaim),
            result -> {
                assertThat(result.getStatus()).isEqualTo(ClaimStatus.PENDING_REVIEW);
                assertThat(result.getAdjudicationResults()).isNotEmpty();
                assertThat(result.getAdjudicationResults().get(0).getReason())
                    .contains("auto-adjudication threshold");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should route fraud-flagged claim to manual review")
    void shouldRouteFraudFlaggedClaimToManualReview(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setFraudScore(0.85);
        testClaim.setAmount(new BigDecimal("100.00"));

        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> adjudicationService.adjudicateClaim(testClaim),
            result -> {
                assertThat(result.getStatus()).isEqualTo(ClaimStatus.PENDING_REVIEW);
                assertThat(result.getAdjudicationResults().get(0).getReason())
                    .contains("fraud risk");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should calculate allowed amount correctly")
    void shouldCalculateAllowedAmountCorrectly(UniAsserter asserter) {
        Claim testClaim = createTestClaim();
        testClaim.setAmount(new BigDecimal("200.00"));

        AdjudicationService.RuleResult ruleResult = new AdjudicationService.RuleResult(
            "ALL_RULES_PASSED",
            "1.0",
            true,
            "Approved",
            null
        );

        when(rulesEngineService.evaluateClaim(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(ruleResult));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> adjudicationService.adjudicateClaim(testClaim),
            result -> {
                // 80% of 200.00 = 160.00
                assertThat(result.getAllowedAmount()).isEqualByComparingTo(new BigDecimal("160.00"));
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should populate adjudication result with copay and coinsurance")
    void shouldPopulateAdjudicationResultWithCopayAndCoinsurance(UniAsserter asserter) {
        Claim testClaim = createTestClaim();

        AdjudicationService.RuleResult ruleResult = new AdjudicationService.RuleResult(
            "ALL_RULES_PASSED",
            "1.0",
            true,
            "Approved",
            null
        );

        when(rulesEngineService.evaluateClaim(any(Claim.class)))
            .thenReturn(Uni.createFrom().item(ruleResult));
        when(claimRepository.persist(any(Claim.class)))
            .thenAnswer(invocation -> {
                Claim c = invocation.getArgument(0, Claim.class);
                return Uni.createFrom().item(c);
            });

        asserter.assertThat(
            () -> adjudicationService.adjudicateClaim(testClaim),
            result -> {
                assertThat(result.getAdjudicationResults()).hasSize(1);
                AdjudicationResult adjResult = result.getAdjudicationResults().get(0);
                assertThat(adjResult.getCopayAmount()).isNotNull();
                assertThat(adjResult.getCoinsuranceAmount()).isNotNull();
                assertThat(adjResult.getPatientResponsibility()).isNotNull();
                assertThat(adjResult.getIsAutomated()).isTrue();
            }
        );
    }
}
