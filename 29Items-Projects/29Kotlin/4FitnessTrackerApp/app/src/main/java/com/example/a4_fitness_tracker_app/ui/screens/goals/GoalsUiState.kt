package com.example.a4_fitness_tracker_app.ui.screens.goals

import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.GoalType

sealed interface GoalsUiState {
    data object Idle : GoalsUiState
    data class Success(val message: String) : GoalsUiState
    data class Error(val errorMessage: String) : GoalsUiState
}

data class GoalsScreenState(
    val activeGoals: List<Goal> = emptyList(),
    val selectedType: GoalType = GoalType.DAILY_STEPS,
    val targetValueText: String = "",
    val isLoading: Boolean = false,
    val validationError: String? = null
)
