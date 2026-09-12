package com.example.a4_fitness_tracker_app.ui.screens.workout

import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType

sealed interface WorkoutUiState {
    data object Idle : WorkoutUiState
    data object Submitting : WorkoutUiState
    data class Success(val message: String) : WorkoutUiState
    data class Error(val errorMessage: String) : WorkoutUiState
}

data class WorkoutFormState(
    val selectedType: WorkoutType = WorkoutType.RUNNING,
    val durationText: String = "",
    val caloriesText: String = "",
    val distanceText: String = "",
    val selectedIntensity: IntensityLevel = IntensityLevel.MODERATE,
    val notesText: String = "",
    val recentWorkouts: List<Workout> = emptyList(),
    val validationError: String? = null
)
