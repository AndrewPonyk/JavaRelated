package com.example.a4_fitness_tracker_app.domain.model

enum class WorkoutType {
    RUNNING,
    CYCLING,
    SWIMMING,
    STRENGTH_TRAINING,
    HIIT,
    YOGA,
    WALKING
}

enum class IntensityLevel {
    LOW,
    MODERATE,
    HIGH,
    EXTREME
}

enum class SyncState {
    PENDING,
    SYNCED,
    FAILED
}

data class Workout(
    val id: String,
    val userId: String,
    val type: WorkoutType,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val distanceMeters: Double = 0.0,
    val intensity: IntensityLevel = IntensityLevel.MODERATE,
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val syncState: SyncState = SyncState.PENDING
)
