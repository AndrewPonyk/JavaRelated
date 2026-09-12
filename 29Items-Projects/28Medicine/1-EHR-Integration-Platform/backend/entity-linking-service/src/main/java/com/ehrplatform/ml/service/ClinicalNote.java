package com.ehrplatform.ml.service;

import java.util.Map;

/**
 * A clinical note in the similarity corpus.
 *
 * @param id        note id (e.g. FHIR DocumentReference id)
 * @param patientId owning patient
 * @param text      raw note text
 * @param riskTier  known outcome tier (HIGH/MODERATE/LOW) used as the kNN label
 * @param vector    precomputed term-frequency vector
 */
public record ClinicalNote(String id, String patientId, String text, String riskTier,
                           Map<String, Double> vector) {

    /** Numeric risk weight for this note's tier, used by the kNN regressor. */
    public double riskWeight() {
        return switch (riskTier) {
            case "HIGH" -> 1.0;
            case "MODERATE" -> 0.5;
            default -> 0.0;
        };
    }
}
