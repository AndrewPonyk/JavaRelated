package com.example.a4_fitness_tracker_app

import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.domain.usecase.LogWorkoutUseCase
import com.example.a4_fitness_tracker_app.ui.screens.workout.WorkoutUiState
import com.example.a4_fitness_tracker_app.ui.screens.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeWorkoutRepository
    private lateinit var logWorkoutUseCase: LogWorkoutUseCase
    private lateinit var viewModel: WorkoutViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeWorkoutRepository()
        logWorkoutUseCase = LogWorkoutUseCase(fakeRepository)
        viewModel = WorkoutViewModel(logWorkoutUseCase, fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `form field changes update formState properly`() = runTest {
        viewModel.onTypeSelected(WorkoutType.CYCLING)
        viewModel.onDurationChanged("45")
        viewModel.onCaloriesChanged("350")
        viewModel.onDistanceChanged("15.5")
        viewModel.onIntensitySelected(IntensityLevel.HIGH)
        viewModel.onNotesChanged("Evening ride")

        val state = viewModel.formState.value
        assertEquals(WorkoutType.CYCLING, state.selectedType)
        assertEquals("45", state.durationText)
        assertEquals("350", state.caloriesText)
        assertEquals("15.5", state.distanceText)
        assertEquals(IntensityLevel.HIGH, state.selectedIntensity)
        assertEquals("Evening ride", state.notesText)
    }

    @Test
    fun `submitWorkout with invalid duration sets validation error`() = runTest {
        viewModel.onDurationChanged("-5")
        viewModel.onCaloriesChanged("100")
        viewModel.submitWorkout("user_1")

        val state = viewModel.formState.value
        assertNotNull(state.validationError)
        assertTrue(state.validationError!!.contains("duration"))
    }

    @Test
    fun `submitWorkout with valid fields submits and resets form`() = runTest {
        viewModel.onTypeSelected(WorkoutType.SWIMMING)
        viewModel.onDurationChanged("30")
        viewModel.onCaloriesChanged("250")
        viewModel.submitWorkout("user_1")

        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState is WorkoutUiState.Success)
        assertEquals(1, fakeRepository.workouts.size)
        assertEquals(WorkoutType.SWIMMING, fakeRepository.workouts.first().type)
        assertEquals("", viewModel.formState.value.durationText)
    }

    @Test
    fun `logSpecialRoutine creates workout with location and attempt tracking`() = runTest {
        viewModel.logSpecialRoutine("user_1", "Office")

        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState is WorkoutUiState.Success)
        assertEquals(1, fakeRepository.workouts.size)

        val workout = fakeRepository.workouts.first()
        assertEquals(WorkoutType.STRENGTH_TRAINING, workout.type)
        assertTrue(workout.notes?.contains("[Office]") == true)
        assertTrue(workout.notes?.contains("15 Push ups + 40sec Plank + 15 StepUps") == true)
    }
}
