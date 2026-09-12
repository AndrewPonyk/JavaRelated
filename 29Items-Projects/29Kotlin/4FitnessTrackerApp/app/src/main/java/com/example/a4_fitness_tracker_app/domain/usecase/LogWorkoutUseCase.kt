package com.example.a4_fitness_tracker_app.domain.usecase

import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.SyncState
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.domain.repository.WorkoutRepository
import java.util.UUID

class LogWorkoutUseCase(
    private val repository: WorkoutRepository
) {
    suspend operator fun invoke(
        userId: String,
        type: WorkoutType,
        durationMinutes: Int,
        caloriesBurned: Int,
        distanceMeters: Double = 0.0,
        intensity: IntensityLevel = IntensityLevel.MODERATE,
        notes: String? = null
    ): Result<Workout> {
        if (durationMinutes <= 0) {
            return Result.failure(IllegalArgumentException("Workout duration must be positive"))
        }
        if (caloriesBurned < 0) {
            return Result.failure(IllegalArgumentException("Calories burned cannot be negative"))
        }

        val workout = Workout(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = type,
            durationMinutes = durationMinutes,
            caloriesBurned = caloriesBurned,
            distanceMeters = distanceMeters,
            intensity = intensity,
            notes = notes?.takeIf { it.isNotBlank() },
            timestamp = System.currentTimeMillis(),
            syncState = SyncState.PENDING
        )

        return repository.saveWorkout(workout).map { workout }
    }
}
