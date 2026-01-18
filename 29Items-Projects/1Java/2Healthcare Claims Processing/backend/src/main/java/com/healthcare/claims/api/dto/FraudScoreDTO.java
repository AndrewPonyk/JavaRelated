package com.healthcare.claims.api.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * DTO for fraud scoring results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScoreDTO {

    private UUID claimId;
    private String claimNumber;
    private Double score;
    private String riskLevel;
    private List<String> reasons;
    private boolean requiresReview;
    private boolean likelyFraud;

    /**
     * Determines the risk level based on score.
     */
    public static String determineRiskLevel(Double score) {
        if (score == null) return "UNKNOWN";
        if (score >= 0.7) return "HIGH";
        if (score >= 0.5) return "MEDIUM";
        if (score >= 0.3) return "LOW";
        return "MINIMAL";
    }

    /**
     * Creates a FraudScoreDTO from claim data.
     */
    public static FraudScoreDTO fromClaim(UUID claimId, String claimNumber,
                                           Double score, List<String> reasons) {
        return FraudScoreDTO.builder()
            .claimId(claimId)
            .claimNumber(claimNumber)
            .score(score)
            .riskLevel(determineRiskLevel(score))
            .reasons(reasons)
            .requiresReview(score != null && score >= 0.5)
            .likelyFraud(score != null && score >= 0.7)
            .build();
    }
}
