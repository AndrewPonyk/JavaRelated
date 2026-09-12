package com.example.a4_fitness_tracker_app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.SyncState
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType

@Entity(
    tableName = "workouts",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["timestamp"]),
        Index(value = ["syncState"])
    ]
)
data class WorkoutEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: String,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val distanceMeters: Double,
    val intensity: String,
    val notes: String?,
    val timestamp: Long,
    val syncState: String
) {
    fun toDomain(): Workout = Workout(
        id = id,
        userId = userId,
        type = WorkoutType.valueOf(type),
        durationMinutes = durationMinutes,
        caloriesBurned = caloriesBurned,
        distanceMeters = distanceMeters,
        intensity = IntensityLevel.valueOf(intensity),
        notes = notes,
        timestamp = timestamp,
        syncState = SyncState.valueOf(syncState)
    )

    companion object {
        fun fromDomain(domain: Workout): WorkoutEntity = WorkoutEntity(
            id = domain.id,
            userId = domain.userId,
            type = domain.type.name,
            durationMinutes = domain.durationMinutes,
            caloriesBurned = domain.caloriesBurned,
            distanceMeters = domain.distanceMeters,
            intensity = domain.intensity.name,
            notes = domain.notes,
            timestamp = domain.timestamp,
            syncState = domain.syncState.name
        )
    }
}
