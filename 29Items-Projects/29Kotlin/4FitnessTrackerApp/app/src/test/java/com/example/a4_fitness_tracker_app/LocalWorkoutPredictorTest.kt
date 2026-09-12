package com.example.a4_fitness_tracker_app

import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.ml.LocalWorkoutPredictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWorkoutPredictorTest {

    private val predictor = LocalWorkoutPredictor()

    @Test
    fun `predictNextWorkouts for empty history returns baseline starter walking session`() {
        val predictions = predictor.predictNextWorkouts(emptyList())
        assertEquals(2, predictions.size)
        assertEquals(WorkoutType.WALKING, predictions.first().suggestedType)
        assertEquals(30, predictions.first().suggestedDurationMinutes)
        assertTrue(predictions.first().confidenceScore > 0.8)
    }

    @Test
    fun `predictNextWorkouts after intense workout recommends active recovery yoga`() {
        val history = listOf(
            Workout(
                id = "w1",
                userId = "user_1",
                type = WorkoutType.RUNNING,
                durationMinutes = 45,
                caloriesBurned = 400,
                intensity = IntensityLevel.HIGH,
                timestamp = System.currentTimeMillis()
            )
        )

        val predictions = predictor.predictNextWorkouts(history)
        assertTrue(predictions.isNotEmpty())
        assertEquals(WorkoutType.YOGA, predictions.first().suggestedType)
        assertTrue(predictions.any { it.suggestedType == WorkoutType.STRENGTH_TRAINING })
    }

    @Test
    fun `predictNextWorkouts after moderate strength training suggests cycling cross training`() {
        val history = listOf(
            Workout(
                id = "w2",
                userId = "user_1",
                type = WorkoutType.STRENGTH_TRAINING,
                durationMinutes = 50,
                caloriesBurned = 350,
                intensity = IntensityLevel.MODERATE,
                timestamp = System.currentTimeMillis()
            )
        )

        val predictions = predictor.predictNextWorkouts(history)
        assertEquals(1, predictions.size)
        assertEquals(WorkoutType.CYCLING, predictions.first().suggestedType)
    }
}
