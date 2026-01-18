package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.AdjudicationResult;
import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.repository.ClaimRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service responsible for claim adjudication using rules engine.
 */
@ApplicationScoped
public class AdjudicationService {

    private static final Logger LOG = Logger.getLogger(AdjudicationService.class);

    private static final BigDecimal AUTO_ADJUDICATION_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal STANDARD_COPAY = new BigDecimal("25");
    private static final BigDecimal COINSURANCE_RATE = new BigDecimal("0.20");

    @Inject
    ClaimRepository claimRepository;

    @Inject
    RulesEngineService rulesEngineService;

    /**
     * Adjudicates a claim based on configured rules.
     */
    @WithTransaction
    public Uni<Claim> adjudicateClaim(Claim claim) {
        LOG.infof("Adjudicating claim: %s", claim.getClaimNumber());

        if (claim.isEligibleForAutoAdjudication()) {
            return performAutoAdjudication(claim);
        } else {
            return routeToManualReview(claim);
        }
    }

    /**
     * Performs automatic adjudication for eligible claims.
     */
    private Uni<Claim> performAutoAdjudication(Claim claim) {
        LOG.infof("Auto-adjudicating claim: %s", claim.getClaimNumber());

        return rulesEngineService.evaluateClaim(claim)
            .flatMap(ruleResult -> {
                AdjudicationResult result = createAdjudicationResult(claim, ruleResult);
                claim.addAdjudicationResult(result);

                if (result.isApproved()) {
                    claim.transitionTo(ClaimStatus.AUTO_ADJUDICATED);
                    claim.setAllowedAmount(result.getAllowedAmount());
                    // Auto-approve simple claims
                    claim.transitionTo(ClaimStatus.APPROVED);
                } else if (result.isDenied()) {
                    claim.transitionTo(ClaimStatus.AUTO_ADJUDICATED);
                    claim.transitionTo(ClaimStatus.DENIED);
                    claim.setDenialReason(result.getReason());
                } else {
                    claim.transitionTo(ClaimStatus.PENDING_REVIEW);
                }

                return claimRepository.persist(claim);
            })
            .onItem().invoke(c ->
                LOG.infof("Claim %s auto-adjudicated with status: %s",
                    c.getClaimNumber(), c.getStatus())
            );
    }

    /**
     * Routes claim to manual review queue.
     */
    private Uni<Claim> routeToManualReview(Claim claim) {
        LOG.infof("Routing claim to manual review: %s", claim.getClaimNumber());

        AdjudicationResult result = AdjudicationResult.builder()
            .ruleApplied("MANUAL_REVIEW_REQUIRED")
            .ruleVersion("1.0")
            .decision("PENDING_REVIEW")
            .reason(determineReviewReason(claim))
            .isAutomated(true)
            .build();

        claim.addAdjudicationResult(result);
        claim.transitionTo(ClaimStatus.PENDING_REVIEW);

        return claimRepository.persist(claim);
    }

    /**
     * Creates an adjudication result from rule evaluation.
     */
    private AdjudicationResult createAdjudicationResult(Claim claim, RuleResult ruleResult) {
        BigDecimal allowedAmount = calculateAllowedAmount(claim, ruleResult);
        BigDecimal copay = ruleResult.isApproved() ? STANDARD_COPAY : BigDecimal.ZERO;
        BigDecimal coinsurance = ruleResult.isApproved() ?
            allowedAmount.multiply(COINSURANCE_RATE).setScale(2, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;

        return AdjudicationResult.builder()
            .ruleApplied(ruleResult.ruleName())
            .ruleVersion(ruleResult.ruleVersion())
            .decision(ruleResult.isApproved() ? "APPROVED" : "DENIED")
            .allowedAmount(allowedAmount)
            .copayAmount(copay)
            .coinsuranceAmount(coinsurance)
            .patientResponsibility(copay.add(coinsurance))
            .reason(ruleResult.reason())
            .denialCode(ruleResult.denialCode())
            .isAutomated(true)
            .build();
    }

    /**
     * Calculates the allowed amount for a claim.
     */
    private BigDecimal calculateAllowedAmount(Claim claim, RuleResult ruleResult) {
        if (!ruleResult.isApproved()) {
            return BigDecimal.ZERO;
        }

        // TODO: Implement fee schedule lookup
        // For now, use a simple percentage of billed amount
        BigDecimal allowedPercentage = new BigDecimal("0.80");
        return claim.getAmount()
            .multiply(allowedPercentage)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Determines why a claim needs manual review.
     */
    private String determineReviewReason(Claim claim) {
        StringBuilder reasons = new StringBuilder();

        if (claim.isFraudFlagged()) {
            reasons.append("High fraud risk score. ");
        }
        if (claim.getAmount().compareTo(AUTO_ADJUDICATION_THRESHOLD) > 0) {
            reasons.append("Claim amount exceeds auto-adjudication threshold. ");
        }
        if (claim.getType().requiresPreAuthorization()) {
            reasons.append("Claim type requires pre-authorization verification. ");
        }

        return reasons.toString().trim();
    }

    /**
     * Record to hold rule evaluation results.
     */
    public record RuleResult(
        String ruleName,
        String ruleVersion,
        boolean isApproved,
        String reason,
        String denialCode
    ) {}
}
