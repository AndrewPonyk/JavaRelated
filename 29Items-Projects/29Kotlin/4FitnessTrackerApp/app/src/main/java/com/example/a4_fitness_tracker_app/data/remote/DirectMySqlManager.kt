package com.example.a4_fitness_tracker_app.data.remote

import android.util.Log
import com.example.a4_fitness_tracker_app.data.local.DatabaseConfigManager
import com.example.a4_fitness_tracker_app.data.local.entity.GoalEntity
import com.example.a4_fitness_tracker_app.data.local.entity.WorkoutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.Properties

/**
 * Standard JDBC Manager for Cloud MySQL.
 * Uses official Oracle MySQL JDBC Driver (5.1.49) with standard PreparedStatements.
 */
class DirectMySqlManager(private val configManager: DatabaseConfigManager) {

    private val tag = "DirectMySqlManager"

    private fun getConnection(): Connection {
        val config = configManager.loadConfig()

        // 1. Quick TCP socket probe to verify reachability
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(config.dbHost, config.dbPort), 4000)
            socket.close()
        } catch (sockEx: Exception) {
            throw SQLException("Cannot reach ${config.dbHost}:${config.dbPort} (Host unreachable or port blocked): ${sockEx.message}")
        }

        // 2. Load official Oracle MySQL JDBC driver
        try {
            Class.forName("com.mysql.jdbc.Driver")
        } catch (_: Throwable) {
            // Driver discovery fallback
        }

        // 3. Connect via standard JDBC URL & Properties
        val props = Properties().apply {
            put("user", config.dbUser)
            put("password", config.dbPassword)
            put("useSSL", "false")
            put("allowPublicKeyRetrieval", "true")
            put("serverTimezone", "UTC")
            put("connectTimeout", "6000")
            put("socketTimeout", "10000")
        }

        val jdbcUrl = "jdbc:mysql://${config.dbHost}:${config.dbPort}/${config.dbName}"
        return DriverManager.getConnection(jdbcUrl, props)
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT 1")
                    if (rs.next()) {
                        Result.success("Connected directly to Cloud MySQL database successfully!")
                    } else {
                        Result.failure(Exception("Query returned empty result"))
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "Direct MySQL connection test failed", t)
            val rawMsg = t.localizedMessage ?: t.message ?: "Unknown error"
            val errorMsg = when {
                rawMsg.contains("timed out", ignoreCase = true) ->
                    "Connection timed out. Ensure your Cloud MySQL allows incoming connections on port 3306."
                rawMsg.contains("Access denied", ignoreCase = true) ->
                    "Access denied: Please verify your MySQL Password."
                rawMsg.contains("Unknown database", ignoreCase = true) ->
                    "Unknown database: The database name does not exist on the server."
                else ->
                    "MySQL Connection: $rawMsg"
            }
            Result.failure(Exception(errorMsg))
        }
    }

    suspend fun initializeCloudSchema(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    // 1. Users table
                    stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS users (
                            id VARCHAR(36) PRIMARY KEY,
                            email VARCHAR(255) UNIQUE,
                            username VARCHAR(100) NOT NULL,
                            created_at BIGINT NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """.trimIndent()
                    )

                    // 2. Workouts table
                    stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS workouts (
                            id VARCHAR(36) PRIMARY KEY,
                            user_id VARCHAR(36) NOT NULL,
                            workout_type VARCHAR(50) NOT NULL,
                            duration_minutes INT NOT NULL,
                            calories_burned INT NOT NULL,
                            distance_meters DOUBLE DEFAULT 0.0,
                            intensity_level VARCHAR(20) DEFAULT 'MODERATE',
                            notes TEXT,
                            start_time BIGINT NOT NULL,
                            sync_status VARCHAR(20) DEFAULT 'SYNCED',
                            INDEX idx_user_start_time (user_id, start_time)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """.trimIndent()
                    )

                    // 3. Goals table
                    stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS goals (
                            id VARCHAR(36) PRIMARY KEY,
                            user_id VARCHAR(36) NOT NULL,
                            goal_type VARCHAR(50) NOT NULL,
                            target_value DOUBLE NOT NULL,
                            current_value DOUBLE DEFAULT 0.0,
                            is_completed BOOLEAN DEFAULT FALSE,
                            INDEX idx_user_goal (user_id, is_completed)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """.trimIndent()
                    )
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(tag, "Failed to initialize cloud schema", t)
            Result.failure(Exception(t.localizedMessage ?: t.message))
        }
    }

    suspend fun syncWorkouts(workouts: List<WorkoutEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (workouts.isEmpty()) return@withContext Result.success(0)

        try {
            initializeCloudSchema() // Ensure tables exist on cloud MySQL
            getConnection().use { conn ->
                val sql = """
                    INSERT INTO workouts (
                        id, user_id, workout_type, duration_minutes, calories_burned,
                        distance_meters, intensity_level, notes, start_time, sync_status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYNCED')
                    ON DUPLICATE KEY UPDATE
                        duration_minutes = VALUES(duration_minutes),
                        calories_burned = VALUES(calories_burned),
                        notes = VALUES(notes),
                        sync_status = 'SYNCED'
                """.trimIndent()

                conn.prepareStatement(sql).use { ps: PreparedStatement ->
                    conn.autoCommit = false
                    for (w in workouts) {
                        ps.setString(1, w.id)
                        ps.setString(2, w.userId)
                        ps.setString(3, w.type)
                        ps.setInt(4, w.durationMinutes)
                        ps.setInt(5, w.caloriesBurned)
                        ps.setDouble(6, w.distanceMeters)
                        ps.setString(7, w.intensity)
                        ps.setString(8, w.notes)
                        ps.setLong(9, w.timestamp)
                        ps.addBatch()
                    }
                    val results = ps.executeBatch()
                    conn.commit()
                    Result.success(results.size)
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "Direct MySQL workout sync failed", t)
            Result.failure(Exception(t.localizedMessage ?: t.message))
        }
    }

    suspend fun syncGoals(goals: List<GoalEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (goals.isEmpty()) return@withContext Result.success(0)

        try {
            initializeCloudSchema()
            getConnection().use { conn ->
                val sql = """
                    INSERT INTO goals (
                        id, user_id, goal_type, target_value, current_value, is_completed
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        current_value = VALUES(current_value),
                        is_completed = VALUES(is_completed)
                """.trimIndent()

                conn.prepareStatement(sql).use { ps: PreparedStatement ->
                    conn.autoCommit = false
                    for (g in goals) {
                        ps.setString(1, g.id)
                        ps.setString(2, g.userId)
                        ps.setString(3, g.type)
                        ps.setDouble(4, g.targetValue)
                        ps.setDouble(5, g.currentValue)
                        ps.setBoolean(6, g.isCompleted)
                        ps.addBatch()
                    }
                    val results = ps.executeBatch()
                    conn.commit()
                    Result.success(results.size)
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "Direct MySQL goals sync failed", t)
            Result.failure(Exception(t.localizedMessage ?: t.message))
        }
    }
}
