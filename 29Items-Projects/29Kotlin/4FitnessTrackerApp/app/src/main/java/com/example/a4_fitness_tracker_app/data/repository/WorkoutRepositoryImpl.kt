package com.example.a4_fitness_tracker_app.data.repository

import com.example.a4_fitness_tracker_app.data.local.dao.GoalDao
import com.example.a4_fitness_tracker_app.data.local.dao.WorkoutDao
import com.example.a4_fitness_tracker_app.data.local.entity.WorkoutEntity
import com.example.a4_fitness_tracker_app.data.remote.DirectMySqlManager
import com.example.a4_fitness_tracker_app.data.remote.FitnessApiService
import com.example.a4_fitness_tracker_app.data.remote.SyncRequest
import com.example.a4_fitness_tracker_app.data.remote.WorkoutNetworkDto
import com.example.a4_fitness_tracker_app.domain.model.Goal
import com.example.a4_fitness_tracker_app.domain.model.SyncState
import com.example.a4_fitness_tracker_app.domain.model.Workout
import com.example.a4_fitness_tracker_app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutRepositoryImpl(
    private val workoutDao: WorkoutDao,
    private val goalDao: GoalDao,
    private val directMySqlManager: DirectMySqlManager? = null,
    private val apiService: FitnessApiService? = null
) : WorkoutRepository {

    override fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getWorkoutById(id: String): Flow<Workout?> {
        return workoutDao.getWorkoutById(id).map { it?.toDomain() }
    }

    override suspend fun saveWorkout(workout: Workout): Result<Unit> {
        return try {
            workoutDao.insertWorkout(WorkoutEntity.fromDomain(workout))
            // 1. Attempt immediate cloud sync right away if network is available
            try {
                if (directMySqlManager != null) {
                    syncPendingWorkouts()
                }
            } catch (_: Exception) {
                // If offline, PeriodicSyncWorker will automatically sync in background
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteWorkout(workoutId: String): Result<Unit> {
        return try {
            workoutDao.deleteWorkoutById(workoutId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getActiveGoals(): Flow<List<Goal>> {
        return goalDao.getActiveGoals().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun updateGoalProgress(goalId: String, newValue: Double): Result<Unit> {
        return try {
            goalDao.updateProgress(goalId, newValue)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncPendingWorkouts(): Result<Int> {
        val pending = workoutDao.getPendingSyncWorkouts()
        if (pending.isEmpty()) return Result.success(0)

        // 1. Direct Cloud MySQL sync (Preferred: No backend required!)
        if (directMySqlManager != null) {
            val directResult = directMySqlManager.syncWorkouts(pending)
            if (directResult.isSuccess) {
                workoutDao.updateSyncStatus(pending.map { it.id }, SyncState.SYNCED.name)
                return directResult
            }
        }

        // 2. REST API fallback if configured
        val api = apiService ?: return Result.failure(Exception("No sync transport (Direct MySQL or REST API) configured"))

        return try {
            val dtos = pending.map { entity ->
                WorkoutNetworkDto(
                    id = entity.id,
                    userId = entity.userId,
                    workoutType = entity.type,
                    durationMinutes = entity.durationMinutes,
                    caloriesBurned = entity.caloriesBurned,
                    distanceMeters = entity.distanceMeters,
                    intensityLevel = entity.intensity,
                    notes = entity.notes,
                    startTime = entity.timestamp,
                    syncStatus = "SYNCED"
                )
            }
            val response = api.syncWorkouts(SyncRequest(userId = pending.first().userId, workouts = dtos))
            if (response.isSuccessful && response.body()?.success == true) {
                workoutDao.updateSyncStatus(pending.map { it.id }, SyncState.SYNCED.name)
                Result.success(pending.size)
            } else {
                Result.failure(Exception("Sync failed with code ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
