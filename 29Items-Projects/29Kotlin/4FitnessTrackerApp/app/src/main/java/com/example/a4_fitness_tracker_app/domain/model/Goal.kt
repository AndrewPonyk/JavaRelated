package com.example.a4_fitness_tracker_app.domain.model

enum class GoalType {
    DAILY_STEPS,
    DAILY_CALORIES,
    WEEKLY_WORKOUTS,
    DISTANCE_KM
}

data class Goal(
    val id: String,
    val userId: String,
    val type: GoalType,
    val targetValue: Double,
    val currentValue: Double,
    val isCompleted: Boolean = false
) {
    val progressPercentage: Float
        get() = if (targetValue > 0) ((currentValue / targetValue) * 100f).toFloat().coerceIn(0f, 100f) else 0f
}
