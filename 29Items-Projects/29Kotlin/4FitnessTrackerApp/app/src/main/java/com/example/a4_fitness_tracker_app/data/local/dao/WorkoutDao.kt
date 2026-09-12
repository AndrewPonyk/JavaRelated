package com.example.a4_fitness_tracker_app.data.local.dao

import androidx.room.*
import com.example.a4_fitness_tracker_app.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Query("SELECT * FROM workouts ORDER BY timestamp DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY timestamp DESC")
    fun getWorkoutsByUser(userId: String): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun getWorkoutById(id: String): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE syncState = 'PENDING'")
    suspend fun getPendingSyncWorkouts(): List<WorkoutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workouts: List<WorkoutEntity>)

    @Query("UPDATE workouts SET syncState = :syncState WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, syncState: String)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkoutById(id: String)
}
