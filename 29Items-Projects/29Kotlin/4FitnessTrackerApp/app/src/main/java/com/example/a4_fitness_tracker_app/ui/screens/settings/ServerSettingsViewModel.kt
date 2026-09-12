package com.example.a4_fitness_tracker_app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a4_fitness_tracker_app.data.local.DatabaseConfigManager
import com.example.a4_fitness_tracker_app.data.local.ServerConfig
import com.example.a4_fitness_tracker_app.data.remote.DirectMySqlManager
import com.example.a4_fitness_tracker_app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ConnectionTestStatus {
    data object Idle : ConnectionTestStatus
    data object Testing : ConnectionTestStatus
    data class Success(val message: String) : ConnectionTestStatus
    data class Failure(val error: String) : ConnectionTestStatus
}

data class ServerSettingsUiState(
    val dbHost: String = "z3t77r.h.filess.io",
    val dbPortText: String = "3306",
    val dbName: String = "fitness_app_db_joineddead",
    val dbUser: String = "fitness_app_db_joineddead",
    val dbPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val testStatus: ConnectionTestStatus = ConnectionTestStatus.Idle,
    val syncStatusMessage: String? = null
)

class ServerSettingsViewModel(
    private val configManager: DatabaseConfigManager,
    private val workoutRepository: WorkoutRepository,
    private val directMySqlManager: DirectMySqlManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSettingsUiState())
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSavedConfig()
    }

    private fun loadSavedConfig() {
        val current = configManager.loadConfig()
        _uiState.update {
            it.copy(
                dbHost = current.dbHost,
                dbPortText = current.dbPort.toString(),
                dbName = current.dbName,
                dbUser = current.dbUser,
                dbPassword = current.dbPassword
            )
        }
    }

    fun onDbHostChanged(value: String) = _uiState.update { it.copy(dbHost = value) }
    fun onDbPortChanged(value: String) = _uiState.update { it.copy(dbPortText = value) }
    fun onDbNameChanged(value: String) = _uiState.update { it.copy(dbName = value) }
    fun onDbUserChanged(value: String) = _uiState.update { it.copy(dbUser = value) }
    fun onDbPasswordChanged(value: String) = _uiState.update { it.copy(dbPassword = value) }
    fun togglePasswordVisibility() = _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun saveSettings(): Boolean {
        val state = _uiState.value
        val dbPort = state.dbPortText.toIntOrNull() ?: 3306

        val newConfig = ServerConfig(
            serverHost = state.dbHost.trim(),
            serverPort = 8080,
            dbHost = state.dbHost.trim(),
            dbPort = dbPort,
            dbName = state.dbName.trim(),
            dbUser = state.dbUser.trim(),
            dbPassword = state.dbPassword
        )
        configManager.saveConfig(newConfig)
        return true
    }

    fun testConnection() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(testStatus = ConnectionTestStatus.Testing) }
                saveSettings() // Persist current credentials before testing

                // 1. Direct MySQL JDBC test if available
                if (directMySqlManager != null) {
                    val result = directMySqlManager.testConnection()
                    result.onSuccess { msg ->
                        _uiState.update { it.copy(testStatus = ConnectionTestStatus.Success(msg)) }
                    }.onFailure { err ->
                        _uiState.update { it.copy(testStatus = ConnectionTestStatus.Failure(err.message ?: "MySQL Connection failed")) }
                    }
                    return@launch
                }

                // 2. Fallback
                val state = _uiState.value
                val port = state.dbPortText.toIntOrNull() ?: 3306
                val result = configManager.testServerConnection(state.dbHost.trim(), port)
                result.onSuccess { msg ->
                    _uiState.update { it.copy(testStatus = ConnectionTestStatus.Success(msg)) }
                }.onFailure { err ->
                    _uiState.update { it.copy(testStatus = ConnectionTestStatus.Failure(err.message ?: "Connection failed")) }
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(testStatus = ConnectionTestStatus.Failure("Error: ${t.localizedMessage ?: t.message}")) }
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(syncStatusMessage = "Syncing local workouts directly with Cloud MySQL...") }
                val result = workoutRepository.syncPendingWorkouts()
                result.onSuccess { count ->
                    _uiState.update { it.copy(syncStatusMessage = "✅ Successfully synced $count records to Cloud MySQL!") }
                }.onFailure { error ->
                    _uiState.update { it.copy(syncStatusMessage = "❌ Sync failed: ${error.message}") }
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(syncStatusMessage = "❌ Sync error: ${t.message}") }
            }
        }
    }

    fun clearStatusMessages() {
        _uiState.update { it.copy(syncStatusMessage = null, testStatus = ConnectionTestStatus.Idle) }
    }
}
