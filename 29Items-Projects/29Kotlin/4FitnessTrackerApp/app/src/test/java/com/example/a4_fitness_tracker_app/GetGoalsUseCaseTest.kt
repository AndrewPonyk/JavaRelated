package com.example.a4_fitness_tracker_app

import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.GoalType
import com.example.a4_fitness_tracker_app.domain.repository.GoalRepository
import com.example.a4_fitness_tracker_app.domain.usecase.GetGoalsUseCase
import com.example.a4_fitness_tracker_app.domain.usecase.UpdateGoalProgressUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeGoalRepository : GoalRepository {
    val goals = mutableListOf<Goal>()

    override fun getActiveGoals(): Flow<List<Goal>> = flowOf(goals)

    override suspend fun saveGoal(goal: Goal): Result<Unit> {
        goals.add(goal)
        return Result.success(Unit)
    }

    override suspend fun updateGoalProgress(goalId: String, currentValue: Double): Result<Unit> {
        val index = goals.indexOfFirst { it.id == goalId }
        if (index != -1) {
            val updated = goals[index].copy(
                currentValue = currentValue,
                isCompleted = currentValue >= goals[index].targetValue
            )
            goals[index] = updated
        }
        return Result.success(Unit)
    }
}

class GetGoalsUseCaseTest {

    private lateinit var repository: FakeGoalRepository
    private lateinit var getGoalsUseCase: GetGoalsUseCase
    private lateinit var updateGoalProgressUseCase: UpdateGoalProgressUseCase

    @Before
    fun setup() {
        repository = FakeGoalRepository()
        getGoalsUseCase = GetGoalsUseCase(repository)
        updateGoalProgressUseCase = UpdateGoalProgressUseCase(repository)
    }

    @Test
    fun `createGoal saves goal and retrieves active goals`() = runTest {
        val createResult = getGoalsUseCase.createGoal(
            userId = "user_1",
            type = GoalType.DAILY_STEPS,
            targetValue = 10000.0
        )

        assertTrue(createResult.isSuccess)
        val active = getGoalsUseCase().first()
        assertEquals(1, active.size)
        assertEquals(10000.0, active.first().targetValue, 0.01)
    }

    @Test
    fun `updateGoalProgress marks goal complete when target reached`() = runTest {
        getGoalsUseCase.createGoal("user_1", GoalType.DAILY_STEPS, 10000.0)
        val goalId = repository.goals.first().id

        val updateResult = updateGoalProgressUseCase(goalId, 10500.0)
        assertTrue(updateResult.isSuccess)
        assertTrue(repository.goals.first().isCompleted)
    }
}
