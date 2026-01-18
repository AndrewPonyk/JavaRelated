package com.bank.fraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for fraud check result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResponse {

    private UUID transactionId;

    /**
     * Risk score between 0.0 (safe) and 1.0 (high risk)
     */
    private double riskScore;

    /**
     * Human-readable risk level
     */
    private RiskLevel riskLevel;

    /**
     * Factors contributing to the risk score
     */
    private List<String> riskFactors;

    /**
     * Recommended action
     */
    private RecommendedAction recommendedAction;

    /**
     * Time taken for ML inference in milliseconds
     */
    private long inferenceTimeMs;

    private Instant checkedAt;

    public enum RiskLevel {
        LOW,      // 0.0 - 0.3
        MEDIUM,   // 0.3 - 0.6
        HIGH,     // 0.6 - 0.8
        CRITICAL  // 0.8 - 1.0
    }

    public enum RecommendedAction {
        ALLOW,
        REVIEW,
        BLOCK,
        REQUIRE_2FA
    }
}
