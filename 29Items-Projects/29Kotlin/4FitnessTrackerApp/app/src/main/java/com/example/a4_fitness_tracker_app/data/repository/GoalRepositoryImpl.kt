package com.example.a4_fitness_tracker_app.data.repository

import com.example.a4_fitness_tracker_app.data.local.dao.GoalDao
import com.example.a4_fitness_tracker_app.data.local.entity.GoalEntity
import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepositoryImpl(
    private val goalDao: GoalDao
) : GoalRepository {

    override fun getActiveGoals(): Flow<List<Goal>> {
        return goalDao.getActiveGoals().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun saveGoal(goal: Goal): Result<Unit> {
        return try {
            goalDao.insertGoal(GoalEntity.fromDomain(goal))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGoalProgress(goalId: String, currentValue: Double): Result<Unit> {
        return try {
            goalDao.updateProgress(goalId, currentValue)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
