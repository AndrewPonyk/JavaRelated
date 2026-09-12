package com.example.a4_fitness_tracker_app.data.local

import androidx.room.TypeConverter
import com.example.a4_fitness_tracker_app.domain.model.GoalType
import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.SyncState
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType

class Converters {

    @TypeConverter
    fun fromWorkoutType(value: WorkoutType): String = value.name

    @TypeConverter
    fun toWorkoutType(value: String): WorkoutType = try {
        WorkoutType.valueOf(value)
    } catch (e: Exception) {
        WorkoutType.RUNNING
    }

    @TypeConverter
    fun fromIntensityLevel(value: IntensityLevel): String = value.name

    @TypeConverter
    fun toIntensityLevel(value: String): IntensityLevel = try {
        IntensityLevel.valueOf(value)
    } catch (e: Exception) {
        IntensityLevel.MODERATE
    }

    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = try {
        SyncState.valueOf(value)
    } catch (e: Exception) {
        SyncState.PENDING
    }

    @TypeConverter
    fun fromGoalType(value: GoalType): String = value.name

    @TypeConverter
    fun toGoalType(value: String): GoalType = try {
        GoalType.valueOf(value)
    } catch (e: Exception) {
        GoalType.DAILY_STEPS
    }
}
