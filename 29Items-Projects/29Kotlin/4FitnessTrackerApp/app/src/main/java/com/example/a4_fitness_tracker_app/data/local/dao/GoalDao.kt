package com.example.a4_fitness_tracker_app.data.local.dao

import androidx.room.*
import com.example.a4_fitness_tracker_app.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals WHERE isCompleted = 0")
    fun getActiveGoals(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Query("UPDATE goals SET currentValue = :value, isCompleted = CASE WHEN :value >= targetValue THEN 1 ELSE 0 END WHERE id = :id")
    suspend fun updateProgress(id: String, value: Double)
}
