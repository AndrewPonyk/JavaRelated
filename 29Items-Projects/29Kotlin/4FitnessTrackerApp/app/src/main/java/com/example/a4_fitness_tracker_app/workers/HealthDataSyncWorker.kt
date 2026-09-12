package com.example.a4_fitness_tracker_app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.a4_fitness_tracker_app.data.healthconnect.HealthConnectManager
import com.example.a4_fitness_tracker_app.data.local.AppDatabase
import com.example.a4_fitness_tracker_app.data.repository.WorkoutRepositoryImpl

class HealthDataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getInstance(applicationContext)
            val healthConnectManager = HealthConnectManager(applicationContext)
            val repository = WorkoutRepositoryImpl(database.workoutDao(), database.goalDao())

            // 1. Sync Health Connect steps to Goals
            if (healthConnectManager.hasAllPermissions()) {
                val stepsToday = healthConnectManager.readTodaySteps()
                val caloriesToday = healthConnectManager.readTodayActiveCalories()
                // Update local goal progress
                repository.updateGoalProgress("goal_daily_steps", stepsToday.toDouble())
            }

            // 2. Sync pending local workouts to remote MySQL cloud backend
            val syncResult = repository.syncPendingWorkouts()

            if (syncResult.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "HealthDataSyncWorker"
    }
}
