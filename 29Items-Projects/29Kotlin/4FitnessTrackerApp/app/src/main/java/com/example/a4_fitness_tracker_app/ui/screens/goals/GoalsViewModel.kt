package com.example.a4_fitness_tracker_app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a4_fitness_tracker_app.domain.model.GoalType
import com.example.a4_fitness_tracker_app.domain.usecase.GetGoalsUseCase
import com.example.a4_fitness_tracker_app.domain.usecase.UpdateGoalProgressUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GoalsViewModel(
    private val getGoalsUseCase: GetGoalsUseCase,
    private val updateGoalProgressUseCase: UpdateGoalProgressUseCase
) : ViewModel() {

    private val _screenState = MutableStateFlow(GoalsScreenState())
    val screenState: StateFlow<GoalsScreenState> = _screenState.asStateFlow()

    private val _uiState = MutableStateFlow<GoalsUiState>(GoalsUiState.Idle)
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    init {
        loadGoals()
    }

    private fun loadGoals() {
        viewModelScope.launch {
            _screenState.update { it.copy(isLoading = true) }
            getGoalsUseCase().collect { goals ->
                _screenState.update { it.copy(activeGoals = goals, isLoading = false) }
            }
        }
    }

    fun onTypeSelected(type: GoalType) {
        _screenState.update { it.copy(selectedType = type) }
    }

    fun onTargetValueChanged(value: String) {
        _screenState.update { it.copy(targetValueText = value, validationError = null) }
    }

    fun createGoal(userId: String = "user_default_1") {
        val target = _screenState.value.targetValueText.toDoubleOrNull()
        if (target == null || target <= 0) {
            _screenState.update { it.copy(validationError = "Please enter a valid positive target number") }
            return
        }

        viewModelScope.launch {
            val result = getGoalsUseCase.createGoal(
                userId = userId,
                type = _screenState.value.selectedType,
                targetValue = target
            )
            result.onSuccess {
                _uiState.value = GoalsUiState.Success("Goal created successfully!")
                _screenState.update { it.copy(targetValueText = "", validationError = null) }
            }.onFailure { error ->
                _uiState.value = GoalsUiState.Error(error.message ?: "Failed to create goal")
            }
        }
    }

    fun addProgressToGoal(goalId: String, amount: Double) {
        viewModelScope.launch {
            val currentGoal = _screenState.value.activeGoals.find { it.id == goalId }
            val newProgress = (currentGoal?.currentValue ?: 0.0) + amount
            updateGoalProgressUseCase(goalId, newProgress)
        }
    }

    fun resetUiState() {
        _uiState.value = GoalsUiState.Idle
    }
}
