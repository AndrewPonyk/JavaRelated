package com.example.a4_fitness_tracker_app.domain.usecase

import com.example.a4_fitness_tracker_app.domain.repository.GoalRepository

class UpdateGoalProgressUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(goalId: String, currentValue: Double): Result<Unit> {
        if (currentValue < 0) {
            return Result.failure(IllegalArgumentException("Goal progress value cannot be negative"))
        }
        return goalRepository.updateGoalProgress(goalId, currentValue)
    }
}
