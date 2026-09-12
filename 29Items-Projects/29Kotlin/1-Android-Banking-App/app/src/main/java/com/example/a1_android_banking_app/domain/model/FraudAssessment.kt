package com.example.a1_android_banking_app.domain.model

enum class FraudRisk { LOW, MEDIUM, HIGH }

data class FraudAssessment(
    /** Normalized 0.0..1.0 risk score produced by the detection engine. */
    val score: Double,
    val risk: FraudRisk,
    /** Human-readable rule hits, surfaced directly in the blocked-transfer UI. */
    val reasons: List<String> = emptyList(),
)
