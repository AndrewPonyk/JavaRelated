package com.example.a4_fitness_tracker_app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class WorkoutNetworkDto(
    val id: String,
    val userId: String,
    val workoutType: String,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val distanceMeters: Double,
    val intensityLevel: String,
    val notes: String?,
    val startTime: Long,
    val syncStatus: String
)

data class SyncRequest(
    val userId: String,
    val workouts: List<WorkoutNetworkDto>,
    val clientVersion: String = "1.0.0"
)

data class SyncResponse(
    val syncedCount: Int,
    val success: Boolean,
    val timestamp: Long
)

data class RecommendationNetworkDto(
    val id: String,
    val userId: String,
    val suggestedType: String,
    val suggestedDurationMinutes: Int,
    val suggestedIntensity: String,
    val rationale: String,
    val confidenceScore: Double,
    val createdAt: Long
)

data class RecommendationListResponse(
    val userId: String,
    val recommendations: List<RecommendationNetworkDto>,
    val generatedAt: Long
)

interface FitnessApiService {

    @GET("api/v1/workouts")
    suspend fun getWorkouts(@Query("userId") userId: String): Response<List<WorkoutNetworkDto>>

    @POST("api/v1/workouts/sync")
    suspend fun syncWorkouts(@Body request: SyncRequest): Response<SyncResponse>

    @GET("api/v1/recommendations")
    suspend fun getRecommendations(@Query("userId") userId: String): Response<RecommendationListResponse>
}
