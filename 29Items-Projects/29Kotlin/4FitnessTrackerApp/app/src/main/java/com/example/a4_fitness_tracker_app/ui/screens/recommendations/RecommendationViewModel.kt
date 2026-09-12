package com.example.a4_fitness_tracker_app.ui.screens.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a4_fitness_tracker_app.domain.model.Recommendation
import com.example.a4_fitness_tracker_app.domain.usecase.GetRecommendationsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecommendationUiState(
    val recommendations: List<Recommendation> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class RecommendationViewModel(
    private val getRecommendationsUseCase: GetRecommendationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()

    init {
        observeRecommendations()
        refreshRecommendations()
    }

    private fun observeRecommendations() {
        viewModelScope.launch {
            getRecommendationsUseCase().collect { list ->
                _uiState.update { it.copy(recommendations = list) }
            }
        }
    }

    fun refreshRecommendations(userId: String = "user_default_1") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = getRecommendationsUseCase.refresh(userId)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Failed to generate recommendations")
                }
            }
        }
    }

    fun dismissRecommendation(id: String) {
        viewModelScope.launch {
            getRecommendationsUseCase.dismiss(id)
        }
    }
}
