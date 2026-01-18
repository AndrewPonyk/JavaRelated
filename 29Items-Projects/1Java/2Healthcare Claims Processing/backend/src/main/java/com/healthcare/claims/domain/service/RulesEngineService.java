package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimType;
import com.healthcare.claims.domain.service.AdjudicationService.RuleResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Rules engine service for claim adjudication.
 * TODO: Integrate with Drools or Easy Rules for complex rule management.
 */
@ApplicationScoped
public class RulesEngineService {

    private static final Logger LOG = Logger.getLogger(RulesEngineService.class);

    private static final String RULES_VERSION = "1.0";

    /**
     * Evaluates a claim against all applicable rules.
     */
    public Uni<RuleResult> evaluateClaim(Claim claim) {
        LOG.infof("Evaluating rules for claim: %s", claim.getClaimNumber());

        return Uni.createFrom().item(() -> {
            List<RuleResult> results = new ArrayList<>();

            // Run all rules
            results.add(evaluateTimeliness(claim));
            results.add(evaluateCoverage(claim));
            results.add(evaluateDuplicateClaim(claim));
            results.add(evaluateProviderEligibility(claim));
            results.add(evaluateAmountReasonableness(claim));

            // Return first denial or approve
            return results.stream()
                .filter(r -> !r.isApproved())
                .findFirst()
                .orElse(new RuleResult(
                    "ALL_RULES_PASSED",
                    RULES_VERSION,
                    true,
                    "Claim meets all adjudication criteria",
                    null
                ));
        });
    }

    /**
     * Rule: Check if claim was submitted within allowed timeframe.
     */
    private RuleResult evaluateTimeliness(Claim claim) {
        long daysSinceService = ChronoUnit.DAYS.between(
            claim.getServiceDate(),
            LocalDate.now()
        );

        // Claims must be submitted within 365 days
        if (daysSinceService > 365) {
            return new RuleResult(
                "TIMELY_FILING",
                RULES_VERSION,
                false,
                "Claim exceeds timely filing limit of 365 days",
                "TF001"
            );
        }

        return approvedResult("TIMELY_FILING");
    }

    /**
     * Rule: Check if service is covered under the plan.
     */
    private RuleResult evaluateCoverage(Claim claim) {
        // TODO: Implement actual coverage lookup from benefits table
        // For now, all standard claim types are covered

        List<ClaimType> coveredTypes = List.of(
            ClaimType.MEDICAL,
            ClaimType.DENTAL,
            ClaimType.VISION,
            ClaimType.PHARMACY,
            ClaimType.LABORATORY,
            ClaimType.EMERGENCY
        );

        if (!coveredTypes.contains(claim.getType())) {
            return new RuleResult(
                "COVERAGE_CHECK",
                RULES_VERSION,
                false,
                "Service type not covered under current plan",
                "NC001"
            );
        }

        return approvedResult("COVERAGE_CHECK");
    }

    /**
     * Rule: Check for duplicate claims.
     */
    private RuleResult evaluateDuplicateClaim(Claim claim) {
        // TODO: Implement duplicate detection query
        // Check for same patient, provider, service date, and amount
        // For now, assume no duplicates

        return approvedResult("DUPLICATE_CHECK");
    }

    /**
     * Rule: Verify provider is eligible to submit claims.
     */
    private RuleResult evaluateProviderEligibility(Claim claim) {
        // TODO: Implement provider eligibility lookup
        // Provider validation is done at submission time
        // This rule does additional checks

        return approvedResult("PROVIDER_ELIGIBILITY");
    }

    /**
     * Rule: Check if billed amount is reasonable for the service.
     */
    private RuleResult evaluateAmountReasonableness(Claim claim) {
        // TODO: Implement fee schedule comparison
        BigDecimal maxReasonableAmount = getMaxReasonableAmount(claim.getType());

        if (claim.getAmount().compareTo(maxReasonableAmount) > 0) {
            return new RuleResult(
                "AMOUNT_REASONABLENESS",
                RULES_VERSION,
                false,
                String.format("Billed amount exceeds reasonable limit for %s",
                    claim.getType().getDescription()),
                "AR001"
            );
        }

        return approvedResult("AMOUNT_REASONABLENESS");
    }

    /**
     * Gets the maximum reasonable amount for a claim type.
     * TODO: Replace with fee schedule lookup.
     */
    private BigDecimal getMaxReasonableAmount(ClaimType type) {
        return switch (type) {
            case EMERGENCY, INPATIENT -> new BigDecimal("50000");
            case MEDICAL, OUTPATIENT -> new BigDecimal("10000");
            case PHARMACY -> new BigDecimal("5000");
            case DENTAL, VISION -> new BigDecimal("3000");
            case LABORATORY, RADIOLOGY -> new BigDecimal("2000");
            default -> new BigDecimal("10000");
        };
    }

    /**
     * Creates an approved rule result.
     */
    private RuleResult approvedResult(String ruleName) {
        return new RuleResult(
            ruleName,
            RULES_VERSION,
            true,
            "Rule passed",
            null
        );
    }
}
