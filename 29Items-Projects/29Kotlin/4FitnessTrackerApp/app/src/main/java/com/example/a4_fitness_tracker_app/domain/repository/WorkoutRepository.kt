package com.example.a4_fitness_tracker_app.domain.repository

import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.Workout
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun getAllWorkouts(): Flow<List<Workout>>
    fun getWorkoutById(id: String): Flow<Workout?>
    suspend fun saveWorkout(workout: Workout): Result<Unit>
    suspend fun deleteWorkout(workoutId: String): Result<Unit>
    fun getActiveGoals(): Flow<List<Goal>>
    suspend fun updateGoalProgress(goalId: String, newValue: Double): Result<Unit>
    suspend fun syncPendingWorkouts(): Result<Int>
}
