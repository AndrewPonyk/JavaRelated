package com.example.a4_fitness_tracker_app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

data class ServerConfig(
    val serverHost: String = "z3t77r.h.filess.io",
    val serverPort: Int = 3306,
    val dbHost: String = "z3t77r.h.filess.io",
    val dbPort: Int = 3306,
    val dbName: String = "fitness_app_db_joineddead",
    val dbUser: String = "fitness_app_db_joineddead",
    val dbPassword: String = ""
) {
    val apiBaseUrl: String
        get() = "http://$serverHost:$serverPort/"
}

class DatabaseConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    fun loadConfig(): ServerConfig {
        return ServerConfig(
            serverHost = prefs.getString(KEY_SERVER_HOST, "z3t77r.h.filess.io") ?: "z3t77r.h.filess.io",
            serverPort = prefs.getInt(KEY_SERVER_PORT, 3306),
            dbHost = prefs.getString(KEY_DB_HOST, "z3t77r.h.filess.io") ?: "z3t77r.h.filess.io",
            dbPort = prefs.getInt(KEY_DB_PORT, 3306),
            dbName = prefs.getString(KEY_DB_NAME, "fitness_app_db_joineddead") ?: "fitness_app_db_joineddead",
            dbUser = prefs.getString(KEY_DB_USER, "fitness_app_db_joineddead") ?: "fitness_app_db_joineddead",
            dbPassword = prefs.getString(KEY_DB_PASSWORD, "") ?: ""
        )
    }

    fun saveConfig(newConfig: ServerConfig) {
        prefs.edit()
            .putString(KEY_SERVER_HOST, newConfig.serverHost.trim())
            .putInt(KEY_SERVER_PORT, newConfig.serverPort)
            .putString(KEY_DB_HOST, newConfig.dbHost.trim())
            .putInt(KEY_DB_PORT, newConfig.dbPort)
            .putString(KEY_DB_NAME, newConfig.dbName.trim())
            .putString(KEY_DB_USER, newConfig.dbUser.trim())
            .putString(KEY_DB_PASSWORD, newConfig.dbPassword)
            .apply()

        _config.value = newConfig
    }

    suspend fun testServerConnection(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                Result.success("Connected to Backend Service successfully (HTTP 200 OK)!")
            } else {
                Result.failure(Exception("Server returned HTTP $responseCode"))
            }
        } catch (e: Exception) {
            // Fallback socket ping
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 3000)
                socket.close()
                Result.success("TCP Socket reachable at $host:$port!")
            } catch (sockEx: Exception) {
                Result.failure(Exception("Could not connect to $host:$port: ${e.message}"))
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "fitness_database_config"
        private const val KEY_SERVER_HOST = "server_host"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_DB_HOST = "db_host"
        private const val KEY_DB_PORT = "db_port"
        private const val KEY_DB_NAME = "db_name"
        private const val KEY_DB_USER = "db_user"
        private const val KEY_DB_PASSWORD = "db_password"
    }
}
