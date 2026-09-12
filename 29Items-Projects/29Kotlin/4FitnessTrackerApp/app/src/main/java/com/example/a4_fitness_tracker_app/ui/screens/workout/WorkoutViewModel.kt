package com.example.a4_fitness_tracker_app.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.domain.repository.WorkoutRepository
import com.example.a4_fitness_tracker_app.domain.usecase.LogWorkoutUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WorkoutViewModel(
    private val logWorkoutUseCase: LogWorkoutUseCase,
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(WorkoutFormState())
    val formState: StateFlow<WorkoutFormState> = _formState.asStateFlow()

    private val _uiState = MutableStateFlow<WorkoutUiState>(WorkoutUiState.Idle)
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    val todaySpecialRoutineCount: StateFlow<Int> = _formState.map { state ->
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        state.recentWorkouts.count {
            it.timestamp >= startOfDay && it.notes?.contains("15 Push ups + 40sec Plank + 15 StepUps") == true
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        loadRecentWorkouts()
    }

    private fun loadRecentWorkouts() {
        viewModelScope.launch {
            repository.getAllWorkouts().collect { workouts ->
                _formState.update { it.copy(recentWorkouts = workouts) }
            }
        }
    }

    fun onTypeSelected(type: WorkoutType) {
        _formState.update { it.copy(selectedType = type) }
    }

    fun onDurationChanged(duration: String) {
        _formState.update { it.copy(durationText = duration, validationError = null) }
    }

    fun onCaloriesChanged(calories: String) {
        _formState.update { it.copy(caloriesText = calories, validationError = null) }
    }

    fun onDistanceChanged(distance: String) {
        _formState.update { it.copy(distanceText = distance) }
    }

    fun onIntensitySelected(intensity: IntensityLevel) {
        _formState.update { it.copy(selectedIntensity = intensity) }
    }

    fun onNotesChanged(notes: String) {
        _formState.update { it.copy(notesText = notes) }
    }

    fun submitWorkout(userId: String = "user_default_1") {
        val current = _formState.value
        val duration = current.durationText.toIntOrNull()
        val calories = current.caloriesText.toIntOrNull()
        val distance = current.distanceText.toDoubleOrNull() ?: 0.0

        if (duration == null || duration <= 0) {
            _formState.update { it.copy(validationError = "Please enter a valid positive duration in minutes") }
            return
        }

        if (calories == null || calories < 0) {
            _formState.update { it.copy(validationError = "Please enter valid calories burned") }
            return
        }

        viewModelScope.launch {
            _uiState.value = WorkoutUiState.Submitting
            val result = logWorkoutUseCase(
                userId = userId,
                type = current.selectedType,
                durationMinutes = duration,
                caloriesBurned = calories,
                distanceMeters = distance * 1000.0,
                intensity = current.selectedIntensity,
                notes = current.notesText
            )

            result.onSuccess {
                _uiState.value = WorkoutUiState.Success("Workout logged successfully!")
                // Reset form fields
                _formState.update {
                    it.copy(
                        durationText = "",
                        caloriesText = "",
                        distanceText = "",
                        notesText = "",
                        validationError = null
                    )
                }
            }.onFailure { error ->
                _uiState.value = WorkoutUiState.Error(error.message ?: "Failed to log workout")
            }
        }
    }

    fun logSpecialRoutine(userId: String, locationTag: String = "Office") {
        viewModelScope.launch {
            _uiState.value = WorkoutUiState.Submitting
            val now = System.currentTimeMillis()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val todayCount = _formState.value.recentWorkouts.count {
                it.timestamp >= startOfDay && it.notes?.contains("15 Push ups + 40sec Plank + 15 StepUps") == true
            }
            val attemptNumber = todayCount + 1

            val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
            val timeString = LocalTime.now().format(timeFormatter)

            val notes = "[$locationTag] 15 Push ups + 40sec Plank + 15 StepUps (Attempt #$attemptNumber at $timeString)"

            val result = logWorkoutUseCase(
                userId = userId,
                type = WorkoutType.STRENGTH_TRAINING,
                durationMinutes = 3,
                caloriesBurned = 35,
                distanceMeters = 0.0,
                intensity = IntensityLevel.HIGH,
                notes = notes
            )

            result.onSuccess {
                _uiState.value = WorkoutUiState.Success("🔥 Attempt #$attemptNumber recorded for $locationTag at $timeString!")
            }.onFailure { error ->
                _uiState.value = WorkoutUiState.Error(error.message ?: "Failed to log quick workout")
            }
        }
    }

    fun resetUiState() {
        _uiState.value = WorkoutUiState.Idle
    }
}
