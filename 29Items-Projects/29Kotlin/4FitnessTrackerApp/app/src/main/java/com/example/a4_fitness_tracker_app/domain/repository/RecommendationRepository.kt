package com.example.a4_fitness_tracker_app.domain.repository

import com.example.a4_fitness_tracker_app.domain.model.Recommendation
import kotlinx.coroutines.flow.Flow

interface RecommendationRepository {
    fun getRecommendations(): Flow<List<Recommendation>>
    suspend fun refreshRecommendations(userId: String): Result<List<Recommendation>>
    suspend fun dismissRecommendation(id: String): Result<Unit>
}
