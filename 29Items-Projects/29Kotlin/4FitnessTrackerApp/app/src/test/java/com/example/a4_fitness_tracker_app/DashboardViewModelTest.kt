package com.example.a4_fitness_tracker_app

import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.GoalType
import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.ui.screens.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeWorkoutRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeWorkoutRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDashboardData computes aggregated calories and minutes correctly`() = runTest {
        fakeRepository.workouts.add(
            Workout(
                id = "w1",
                userId = "user_1",
                type = WorkoutType.RUNNING,
                durationMinutes = 40,
                caloriesBurned = 400,
                intensity = IntensityLevel.HIGH
            )
        )
        fakeRepository.workouts.add(
            Workout(
                id = "w2",
                userId = "user_1",
                type = WorkoutType.YOGA,
                durationMinutes = 20,
                caloriesBurned = 100,
                intensity = IntensityLevel.LOW
            )
        )

        viewModel = DashboardViewModel(fakeRepository)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertEquals(500, uiState.todayCalories)
        assertEquals(60, uiState.totalActiveMinutes)
        assertEquals(2, uiState.weeklyWorkoutsCount)
        assertEquals(2, uiState.recentWorkouts.size)
    }
}
