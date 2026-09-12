package com.example.a4_fitness_tracker_app.domain.repository

import com.example.a4_fitness_tracker_app.domain.model.Goal
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun getActiveGoals(): Flow<List<Goal>>
    suspend fun saveGoal(goal: Goal): Result<Unit>
    suspend fun updateGoalProgress(goalId: String, currentValue: Double): Result<Unit>
}
