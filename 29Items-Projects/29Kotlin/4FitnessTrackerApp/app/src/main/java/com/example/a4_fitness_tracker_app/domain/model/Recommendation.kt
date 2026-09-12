package com.example.a4_fitness_tracker_app.domain.model

data class Recommendation(
    val id: String,
    val suggestedType: WorkoutType,
    val suggestedDurationMinutes: Int,
    val suggestedIntensity: IntensityLevel,
    val rationale: String,
    val confidenceScore: Double,
    val isDismissed: Boolean = false
)
