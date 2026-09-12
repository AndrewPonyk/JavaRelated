package com.example.a4_fitness_tracker_app

import android.app.Application
import com.example.a4_fitness_tracker_app.data.local.AppDatabase
import com.example.a4_fitness_tracker_app.data.local.DatabaseConfigManager
import com.example.a4_fitness_tracker_app.data.local.UserSessionManager
import com.example.a4_fitness_tracker_app.data.remote.ApiClient
import com.example.a4_fitness_tracker_app.data.remote.DirectMySqlManager
import com.example.a4_fitness_tracker_app.data.repository.GoalRepositoryImpl
import com.example.a4_fitness_tracker_app.data.repository.RecommendationRepositoryImpl
import com.example.a4_fitness_tracker_app.data.repository.WorkoutRepositoryImpl
import com.example.a4_fitness_tracker_app.domain.usecase.*
import com.example.a4_fitness_tracker_app.workers.PeriodicSyncWorkerScheduler

class FitnessTrackerApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var workoutRepository: WorkoutRepositoryImpl
        private set

    lateinit var goalRepository: GoalRepositoryImpl
        private set

    lateinit var recommendationRepository: RecommendationRepositoryImpl
        private set

    lateinit var userSessionManager: UserSessionManager
        private set

    lateinit var databaseConfigManager: DatabaseConfigManager
        private set

    lateinit var directMySqlManager: DirectMySqlManager
        private set

    lateinit var logWorkoutUseCase: LogWorkoutUseCase
        private set

    lateinit var getGoalsUseCase: GetGoalsUseCase
        private set

    lateinit var updateGoalProgressUseCase: UpdateGoalProgressUseCase
        private set

    lateinit var getWorkoutStatsUseCase: GetWorkoutStatsUseCase
        private set

    lateinit var getRecommendationsUseCase: GetRecommendationsUseCase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize Local Room Database, User Session & Server Config
        database = AppDatabase.getInstance(this)
        userSessionManager = UserSessionManager(this)
        databaseConfigManager = DatabaseConfigManager(this)
        directMySqlManager = DirectMySqlManager(databaseConfigManager)

        // 2. Optional Remote API Service
        val serverConfig = databaseConfigManager.loadConfig()
        val apiService = try {
            ApiClient.createService(serverConfig.apiBaseUrl)
        } catch (e: Exception) {
            null
        }

        // 3. Initialize Repositories (with Direct Cloud MySQL Manager)
        workoutRepository = WorkoutRepositoryImpl(
            workoutDao = database.workoutDao(),
            goalDao = database.goalDao(),
            directMySqlManager = directMySqlManager,
            apiService = apiService
        )
        goalRepository = GoalRepositoryImpl(
            goalDao = database.goalDao()
        )
        recommendationRepository = RecommendationRepositoryImpl(
            workoutDao = database.workoutDao()
        )

        // 4. Initialize Domain Use Cases
        logWorkoutUseCase = LogWorkoutUseCase(workoutRepository)
        getGoalsUseCase = GetGoalsUseCase(goalRepository)
        updateGoalProgressUseCase = UpdateGoalProgressUseCase(goalRepository)
        getWorkoutStatsUseCase = GetWorkoutStatsUseCase(workoutRepository)
        getRecommendationsUseCase = GetRecommendationsUseCase(recommendationRepository)

        // 5. Schedule Background Periodic Sync via WorkManager
        PeriodicSyncWorkerScheduler.schedulePeriodicSync(this)
    }

    companion object {
        lateinit var instance: FitnessTrackerApp
            private set
    }
}
