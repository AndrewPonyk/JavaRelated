package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.repository.ClaimRepository;
import com.healthcare.claims.infrastructure.fraud.FraudScoringClient;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for ML-powered fraud detection and scoring.
 */
@ApplicationScoped
public class FraudDetectionService {

    private static final Logger LOG = Logger.getLogger(FraudDetectionService.class);

    private static final double FRAUD_THRESHOLD = 0.7;
    private static final double REVIEW_THRESHOLD = 0.5;

    @Inject
    ClaimRepository claimRepository;

    @Inject
    FraudScoringClient fraudScoringClient;

    /**
     * Scores a claim for potential fraud.
     */
    @WithTransaction
    public Uni<Claim> scoreClaim(Claim claim) {
        LOG.infof("Scoring claim for fraud: %s", claim.getClaimNumber());

        return calculateFraudScore(claim)
            .flatMap(scoreResult -> {
                claim.setFraudScore(scoreResult.score());
                claim.setFraudReasons(String.join("; ", scoreResult.reasons()));
                return claimRepository.persist(claim);
            })
            .onItem().invoke(c ->
                LOG.infof("Claim %s fraud score: %.2f", c.getClaimNumber(), c.getFraudScore())
            );
    }

    /**
     * Calculates fraud score using multiple signals.
     */
    private Uni<FraudScoreResult> calculateFraudScore(Claim claim) {
        // Combine ML model score with rule-based signals
        return Uni.combine().all().unis(
            getMLScore(claim),
            calculateRuleBasedScore(claim)
        ).asTuple()
        .map(tuple -> {
            double mlScore = tuple.getItem1();
            RuleBasedScore ruleScore = tuple.getItem2();

            // Weighted combination: 60% ML, 40% rules
            double combinedScore = (mlScore * 0.6) + (ruleScore.score() * 0.4);

            List<String> allReasons = new ArrayList<>(ruleScore.reasons());
            if (mlScore > REVIEW_THRESHOLD) {
                allReasons.add("ML model flagged potential anomaly");
            }

            return new FraudScoreResult(combinedScore, allReasons);
        });
    }

    /**
     * Gets fraud score from ML model API.
     */
    private Uni<Double> getMLScore(Claim claim) {
        return fraudScoringClient.getScore(claim)
            .onFailure().recoverWithItem(e -> {
                LOG.warnf("ML scoring failed for claim %s, using default: %s",
                    claim.getClaimNumber(), e.getMessage());
                return 0.0;
            });
    }

    /**
     * Calculates rule-based fraud indicators.
     */
    private Uni<RuleBasedScore> calculateRuleBasedScore(Claim claim) {
        return Uni.createFrom().item(() -> {
            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            // Check for high-value claim
            if (claim.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                score += 0.2;
                reasons.add("High-value claim");
            }

            // Check for weekend service date (potential red flag)
            var dayOfWeek = claim.getServiceDate().getDayOfWeek();
            if (dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                score += 0.1;
                reasons.add("Service on weekend");
            }

            // Check for round dollar amounts (potential red flag)
            if (claim.getAmount().remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0) {
                score += 0.1;
                reasons.add("Round dollar amount");
            }

            // TODO: Add more rule-based checks:
            // - Provider claim volume anomaly
            // - Patient claim frequency anomaly
            // - Geographic distance checks
            // - Procedure/diagnosis code mismatch
            // - Upcoding detection

            return new RuleBasedScore(Math.min(score, 1.0), reasons);
        });
    }

    /**
     * Checks if a claim requires fraud review.
     */
    public boolean requiresFraudReview(Claim claim) {
        return claim.getFraudScore() != null &&
               claim.getFraudScore() >= REVIEW_THRESHOLD;
    }

    /**
     * Checks if a claim is likely fraudulent.
     */
    public boolean isLikelyFraud(Claim claim) {
        return claim.getFraudScore() != null &&
               claim.getFraudScore() >= FRAUD_THRESHOLD;
    }

    /**
     * Record for fraud score results.
     */
    private record FraudScoreResult(double score, List<String> reasons) {}

    /**
     * Record for rule-based scoring.
     */
    private record RuleBasedScore(double score, List<String> reasons) {}
}
