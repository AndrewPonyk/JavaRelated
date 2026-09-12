package com.example.a4_fitness_tracker_app.domain.usecase

import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.GoalType
import com.example.a4_fitness_tracker_app.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class GetGoalsUseCase(
    private val goalRepository: GoalRepository
) {
    operator fun invoke(): Flow<List<Goal>> {
        return goalRepository.getActiveGoals()
    }

    suspend fun createGoal(
        userId: String,
        type: GoalType,
        targetValue: Double
    ): Result<Unit> {
        if (targetValue <= 0) {
            return Result.failure(IllegalArgumentException("Target value must be positive"))
        }
        val goal = Goal(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = type,
            targetValue = targetValue,
            currentValue = 0.0,
            isCompleted = false
        )
        return goalRepository.saveGoal(goal)
    }
}
