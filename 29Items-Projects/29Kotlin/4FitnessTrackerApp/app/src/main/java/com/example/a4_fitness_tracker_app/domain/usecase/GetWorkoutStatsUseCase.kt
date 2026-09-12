package com.example.a4_fitness_tracker_app.domain.usecase

import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WorkoutStats(
    val totalWorkouts: Int,
    val totalCaloriesBurned: Int,
    val totalDurationMinutes: Int,
    val totalDistanceMeters: Double,
    val mostFrequentActivity: WorkoutType?,
    val recentWorkouts: List<Workout>
)

class GetWorkoutStatsUseCase(
    private val workoutRepository: WorkoutRepository
) {
    operator fun invoke(): Flow<WorkoutStats> {
        return workoutRepository.getAllWorkouts().map { list ->
            val totalWorkouts = list.size
            val totalCalories = list.sumOf { it.caloriesBurned }
            val totalDuration = list.sumOf { it.durationMinutes }
            val totalDistance = list.sumOf { it.distanceMeters }

            val mostFrequent = if (list.isNotEmpty()) {
                list.groupingBy { it.type }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
            } else null

            WorkoutStats(
                totalWorkouts = totalWorkouts,
                totalCaloriesBurned = totalCalories,
                totalDurationMinutes = totalDuration,
                totalDistanceMeters = totalDistance,
                mostFrequentActivity = mostFrequent,
                recentWorkouts = list.take(10)
            )
        }
    }
}
