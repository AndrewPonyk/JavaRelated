package com.example.a4_fitness_tracker_app.domain.usecase

import com.example.a4_fitness_tracker_app.domain.model.Recommendation
import com.example.a4_fitness_tracker_app.domain.repository.RecommendationRepository
import kotlinx.coroutines.flow.Flow

class GetRecommendationsUseCase(
    private val recommendationRepository: RecommendationRepository
) {
    operator fun invoke(): Flow<List<Recommendation>> {
        return recommendationRepository.getRecommendations()
    }

    suspend fun refresh(userId: String): Result<List<Recommendation>> {
        return recommendationRepository.refreshRecommendations(userId)
    }

    suspend fun dismiss(id: String): Result<Unit> {
        return recommendationRepository.dismissRecommendation(id)
    }
}
