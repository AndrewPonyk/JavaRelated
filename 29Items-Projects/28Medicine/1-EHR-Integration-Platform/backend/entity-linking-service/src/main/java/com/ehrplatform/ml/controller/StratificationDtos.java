package com.ehrplatform.ml.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request/response DTOs for the risk-stratification and entity-linking APIs.
 */
public final class StratificationDtos {

    private StratificationDtos() {
    }

    /**
     * @param patientId FHIR Patient logical id to stratify
     * @param noteText  free-text clinical note used to find similar cohorts
     * @param topK      number of similar notes to retrieve (1..50)
     */
    public record StratificationRequest(
            @NotBlank String patientId,
            @NotBlank String noteText,
            @Min(1) @Max(50) int topK) {
    }

    /**
     * @param patientId echoed patient id
     * @param riskScore normalized risk score in [0,1]
     * @param riskTier  LOW | MODERATE | HIGH
     * @param cohort    ids of similar notes that informed the score
     */
    public record StratificationResponse(
            String patientId,
            double riskScore,
            String riskTier,
            List<String> cohort) {
    }

    /**
     * Entity-linking request: do two patients' notes describe the same/related case?
     */
    public record LinkRequest(
            @NotBlank String leftPatientId,
            @NotBlank String leftNote,
            @NotBlank String rightPatientId,
            @NotBlank String rightNote) {
    }

    /**
     * @param confidence cosine similarity in [0,1]
     * @param status     CANDIDATE (>= threshold) | REJECTED
     */
    public record LinkResponse(
            String leftPatientId,
            String rightPatientId,
            double confidence,
            String status) {
    }
}
