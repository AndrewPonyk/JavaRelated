package com.example.a4_fitness_tracker_app

import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.domain.repository.WorkoutRepository
import com.example.a4_fitness_tracker_app.domain.usecase.LogWorkoutUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeWorkoutRepository : WorkoutRepository {
    val workouts = mutableListOf<Workout>()

    override fun getAllWorkouts(): Flow<List<Workout>> = flowOf(workouts)
    override fun getWorkoutById(id: String): Flow<Workout?> = flowOf(workouts.find { it.id == id })
    override suspend fun saveWorkout(workout: Workout): Result<Unit> {
        workouts.add(workout)
        return Result.success(Unit)
    }
    override suspend fun deleteWorkout(workoutId: String): Result<Unit> {
        workouts.removeAll { it.id == workoutId }
        return Result.success(Unit)
    }
    override fun getActiveGoals(): Flow<List<Goal>> = flowOf(emptyList())
    override suspend fun updateGoalProgress(goalId: String, newValue: Double): Result<Unit> = Result.success(Unit)
    override suspend fun syncPendingWorkouts(): Result<Int> = Result.success(workouts.size)
}

class LogWorkoutUseCaseTest {

    private lateinit var repository: FakeWorkoutRepository
    private lateinit var useCase: LogWorkoutUseCase

    @Before
    fun setup() {
        repository = FakeWorkoutRepository()
        useCase = LogWorkoutUseCase(repository)
    }

    @Test
    fun `invoke with valid parameters saves workout successfully`() = runTest {
        val result = useCase(
            userId = "user_test_1",
            type = WorkoutType.RUNNING,
            durationMinutes = 30,
            caloriesBurned = 300,
            distanceMeters = 5000.0,
            intensity = IntensityLevel.HIGH,
            notes = "Test run"
        )

        assertTrue(result.isSuccess)
        val workout = result.getOrNull()
        assertEquals("user_test_1", workout?.userId)
        assertEquals(WorkoutType.RUNNING, workout?.type)
        assertEquals(30, workout?.durationMinutes)
        assertEquals(300, workout?.caloriesBurned)
        assertEquals(1, repository.workouts.size)
    }

    @Test
    fun `invoke with invalid duration fails with IllegalArgumentException`() = runTest {
        val result = useCase(
            userId = "user_test_1",
            type = WorkoutType.RUNNING,
            durationMinutes = -5,
            caloriesBurned = 100
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `invoke with negative calories fails with IllegalArgumentException`() = runTest {
        val result = useCase(
            userId = "user_test_1",
            type = WorkoutType.RUNNING,
            durationMinutes = 30,
            caloriesBurned = -50
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke special routine stores location tag attempt number and timestamp in notes`() = runTest {
        val locationTag = "Office"
        val attemptNumber = 3
        val timeString = "02:15 PM"
        val notes = "[$locationTag] 15 Push ups + 40sec Plank + 15 StepUps (Attempt #$attemptNumber at $timeString)"

        val result = useCase(
            userId = "user_test_1",
            type = WorkoutType.STRENGTH_TRAINING,
            durationMinutes = 3,
            caloriesBurned = 35,
            intensity = IntensityLevel.HIGH,
            notes = notes
        )

        assertTrue(result.isSuccess)
        val workout = result.getOrNull()
        assertEquals(WorkoutType.STRENGTH_TRAINING, workout?.type)
        assertEquals(notes, workout?.notes)
        assertTrue(workout?.notes?.contains("[Office]") == true)
        assertTrue(workout?.notes?.contains("Attempt #3") == true)
        assertTrue(workout?.notes?.contains("15 Push ups + 40sec Plank + 15 StepUps") == true)
    }
}
