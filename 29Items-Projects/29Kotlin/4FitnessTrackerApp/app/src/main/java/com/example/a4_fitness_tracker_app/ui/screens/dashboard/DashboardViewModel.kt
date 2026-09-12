package com.example.a4_fitness_tracker_app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val todaySteps: Long = 0L,
    val targetSteps: Long = 10000L,
    val todayCalories: Int = 0,
    val targetCalories: Int = 2200,
    val weeklyWorkoutsCount: Int = 0,
    val totalActiveMinutes: Int = 0,
    val recentWorkouts: List<Workout> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                combine(
                    repository.getAllWorkouts(),
                    repository.getActiveGoals()
                ) { workouts, goals ->
                    val totalCalories = workouts.sumOf { it.caloriesBurned }
                    val totalMinutes = workouts.sumOf { it.durationMinutes }

                    _uiState.update {
                        it.copy(
                            recentWorkouts = workouts.take(5),
                            goals = goals,
                            todayCalories = totalCalories,
                            weeklyWorkoutsCount = workouts.size,
                            totalActiveMinutes = totalMinutes,
                            isLoading = false
                        )
                    }
                }.collect()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load dashboard data")
                }
            }
        }
    }
}
