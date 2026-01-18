package com.healthcare.claims.infrastructure.fraud;

import com.healthcare.claims.domain.model.Claim;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Local ML-based fraud detection for fallback and offline scoring.
 * TODO: Replace with actual ML model integration (ONNX, TensorFlow Serving, etc.)
 */
@ApplicationScoped
public class MLFraudDetector {

    private static final Logger LOG = Logger.getLogger(MLFraudDetector.class);

    /**
     * Calculates fraud score using local heuristics.
     * This is a placeholder for actual ML model inference.
     */
    public Uni<FraudPrediction> predict(Claim claim) {
        return Uni.createFrom().item(() -> {
            double score = 0.0;
            List<String> factors = new ArrayList<>();

            // Feature: High claim amount
            if (claim.getAmount().compareTo(new BigDecimal("5000")) > 0) {
                score += 0.15;
                factors.add("high_amount");
            }

            // Feature: Claim amount exactly round number
            if (isRoundAmount(claim.getAmount())) {
                score += 0.1;
                factors.add("round_amount");
            }

            // Feature: Weekend service date
            var dayOfWeek = claim.getServiceDate().getDayOfWeek();
            if (dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                score += 0.1;
                factors.add("weekend_service");
            }

            // Feature: Service date very recent (same day submission)
            if (claim.getServiceDate().equals(java.time.LocalDate.now())) {
                score += 0.05;
                factors.add("same_day_submission");
            }

            // Feature: Missing diagnosis codes
            if (claim.getDiagnosisCodes() == null || claim.getDiagnosisCodes().isBlank()) {
                score += 0.2;
                factors.add("missing_diagnosis");
            }

            // Feature: Missing procedure codes
            if (claim.getProcedureCodes() == null || claim.getProcedureCodes().isBlank()) {
                score += 0.15;
                factors.add("missing_procedures");
            }

            // Normalize score to 0-1 range
            score = Math.min(score, 1.0);

            LOG.infof("Local ML prediction for claim %s: score=%.2f, factors=%s",
                claim.getClaimNumber(), score, factors);

            return new FraudPrediction(score, factors, determineRiskLevel(score));
        });
    }

    /**
     * Checks if amount is suspiciously round.
     */
    private boolean isRoundAmount(BigDecimal amount) {
        return amount.remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Determines risk level from score.
     */
    private String determineRiskLevel(double score) {
        if (score >= 0.7) return "HIGH";
        if (score >= 0.5) return "MEDIUM";
        if (score >= 0.3) return "LOW";
        return "MINIMAL";
    }

    /**
     * Result of fraud prediction.
     */
    public record FraudPrediction(
        double score,
        List<String> contributingFactors,
        String riskLevel
    ) {}
}
