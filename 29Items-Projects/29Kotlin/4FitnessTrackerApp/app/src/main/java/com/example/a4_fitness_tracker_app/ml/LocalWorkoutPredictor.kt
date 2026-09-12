package com.example.a4_fitness_tracker_app.ml

import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.Recommendation
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import java.util.UUID

/**
 * On-Device ML & Heuristic Prediction Engine.
 * Evaluates historical workout patterns, fatigue levels, muscle-group balance,
 * and rest intervals directly on the user's phone.
 */
class LocalWorkoutPredictor {

    fun predictNextWorkouts(recentHistory: List<Workout>): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()

        if (recentHistory.isEmpty()) {
            recommendations.add(
                Recommendation(
                    id = UUID.randomUUID().toString(),
                    suggestedType = WorkoutType.WALKING,
                    suggestedDurationMinutes = 30,
                    suggestedIntensity = IntensityLevel.MODERATE,
                    rationale = "Kickstart your routine with a brisk 30-minute walk to build baseline cardio.",
                    confidenceScore = 0.95
                )
            )
            recommendations.add(
                Recommendation(
                    id = UUID.randomUUID().toString(),
                    suggestedType = WorkoutType.YOGA,
                    suggestedDurationMinutes = 20,
                    suggestedIntensity = IntensityLevel.LOW,
                    rationale = "Introductory mobility and stretching session to improve flexibility and joint stability.",
                    confidenceScore = 0.88
                )
            )
            return recommendations
        }

        val lastWorkout = recentHistory.first()
        val totalWorkouts = recentHistory.size
        val avgDuration = recentHistory.map { it.durationMinutes }.average().toInt().coerceIn(15, 90)

        // 1. Fatigue & Recovery Detection:
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val isRecentHighIntensity = lastWorkout.intensity == IntensityLevel.HIGH &&
                (System.currentTimeMillis() - lastWorkout.timestamp) < oneDayMillis

        if (isRecentHighIntensity) {
            recommendations.add(
                Recommendation(
                    id = UUID.randomUUID().toString(),
                    suggestedType = WorkoutType.YOGA,
                    suggestedDurationMinutes = 20,
                    suggestedIntensity = IntensityLevel.LOW,
                    rationale = "Active Recovery: Following your intense ${lastWorkout.type.name.replace('_', ' ')} session, a light stretching session helps flush lactic acid and prevent strain.",
                    confidenceScore = 0.94
                )
            )
        }

        // 2. Cross-Training Transition:
        val crossTrainingType = when (lastWorkout.type) {
            WorkoutType.RUNNING -> WorkoutType.STRENGTH_TRAINING
            WorkoutType.STRENGTH_TRAINING -> WorkoutType.CYCLING
            WorkoutType.CYCLING -> WorkoutType.HIIT
            WorkoutType.HIIT -> WorkoutType.WALKING
            WorkoutType.WALKING -> WorkoutType.RUNNING
            WorkoutType.SWIMMING -> WorkoutType.STRENGTH_TRAINING
            WorkoutType.YOGA -> WorkoutType.RUNNING
        }

        val targetDuration = (avgDuration * 1.05).toInt().coerceIn(20, 60)

        recommendations.add(
            Recommendation(
                id = UUID.randomUUID().toString(),
                suggestedType = crossTrainingType,
                suggestedDurationMinutes = targetDuration,
                suggestedIntensity = IntensityLevel.MODERATE,
                rationale = "Cross-training progression: Alternating to ${crossTrainingType.name.replace('_', ' ')} develops complementary muscle groups and aerobic endurance.",
                confidenceScore = 0.91
            )
        )

        // 3. High-Performance Challenge (if user is consistently active)
        if (totalWorkouts >= 3 && !isRecentHighIntensity) {
            recommendations.add(
                Recommendation(
                    id = UUID.randomUUID().toString(),
                    suggestedType = WorkoutType.HIIT,
                    suggestedDurationMinutes = 25,
                    suggestedIntensity = IntensityLevel.HIGH,
                    rationale = "Conditioning Boost: You've maintained great momentum! A 25-minute HIIT interval session will maximize metabolic rate.",
                    confidenceScore = 0.86
                )
            )
        }

        return recommendations
    }
}
