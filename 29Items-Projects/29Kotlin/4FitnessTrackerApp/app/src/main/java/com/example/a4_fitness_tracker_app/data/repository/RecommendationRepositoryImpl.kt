package com.example.a4_fitness_tracker_app.data.repository

import com.example.a4_fitness_tracker_app.data.local.dao.WorkoutDao
import com.example.a4_fitness_tracker_app.domain.model.Recommendation
import com.example.a4_fitness_tracker_app.domain.repository.RecommendationRepository
import com.example.a4_fitness_tracker_app.ml.LocalWorkoutPredictor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

class RecommendationRepositoryImpl(
    private val workoutDao: WorkoutDao? = null,
    private val localPredictor: LocalWorkoutPredictor = LocalWorkoutPredictor()
) : RecommendationRepository {

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())

    override fun getRecommendations(): Flow<List<Recommendation>> = _recommendations.asStateFlow()

    override suspend fun refreshRecommendations(userId: String): Result<List<Recommendation>> {
        return try {
            val history = workoutDao?.getWorkoutsByUser(userId)?.firstOrNull()?.map { it.toDomain() } ?: emptyList()
            val list = localPredictor.predictNextWorkouts(history)
            _recommendations.value = list
            Result.success(list)
        } catch (e: Exception) {
            val fallback = localPredictor.predictNextWorkouts(emptyList())
            _recommendations.value = fallback
            Result.success(fallback)
        }
    }

    override suspend fun dismissRecommendation(id: String): Result<Unit> {
        _recommendations.value = _recommendations.value.filterNot { it.id == id }
        return Result.success(Unit)
    }
}
