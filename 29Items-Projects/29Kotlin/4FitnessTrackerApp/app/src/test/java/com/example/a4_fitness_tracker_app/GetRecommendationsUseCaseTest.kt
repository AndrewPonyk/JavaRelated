package com.example.a4_fitness_tracker_app

import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.Recommendation
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.domain.repository.RecommendationRepository
import com.example.a4_fitness_tracker_app.domain.usecase.GetRecommendationsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeRecommendationRepository : RecommendationRepository {
    val items = mutableListOf<Recommendation>()

    override fun getRecommendations(): Flow<List<Recommendation>> = flowOf(items)

    override suspend fun refreshRecommendations(userId: String): Result<List<Recommendation>> {
        val rec = Recommendation(
            id = "rec_1",
            suggestedType = WorkoutType.CYCLING,
            suggestedDurationMinutes = 40,
            suggestedIntensity = IntensityLevel.MODERATE,
            rationale = "Cardio endurance training",
            confidenceScore = 0.9
        )
        items.clear()
        items.add(rec)
        return Result.success(items)
    }

    override suspend fun dismissRecommendation(id: String): Result<Unit> {
        items.removeAll { it.id == id }
        return Result.success(Unit)
    }
}

class GetRecommendationsUseCaseTest {

    private lateinit var repository: FakeRecommendationRepository
    private lateinit var useCase: GetRecommendationsUseCase

    @Before
    fun setup() {
        repository = FakeRecommendationRepository()
        useCase = GetRecommendationsUseCase(repository)
    }

    @Test
    fun `refresh and observe recommendations succeeds`() = runTest {
        val refreshResult = useCase.refresh("user_123")
        assertTrue(refreshResult.isSuccess)

        val list = useCase().first()
        assertEquals(1, list.size)
        assertEquals(WorkoutType.CYCLING, list.first().suggestedType)
        assertEquals(40, list.first().suggestedDurationMinutes)
    }

    @Test
    fun `dismiss recommendation removes item`() = runTest {
        useCase.refresh("user_123")
        val dismissResult = useCase.dismiss("rec_1")
        assertTrue(dismissResult.isSuccess)
        assertEquals(0, repository.items.size)
    }
}
