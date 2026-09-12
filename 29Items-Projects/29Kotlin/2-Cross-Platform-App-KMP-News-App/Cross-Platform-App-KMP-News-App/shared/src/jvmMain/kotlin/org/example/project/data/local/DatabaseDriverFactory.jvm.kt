package org.example.project.data.local

/**
 * Desktop JVM SQLite Driver using JdbcSqliteDriver.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): Any {
        // In full SQLDelight build: JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        return "JdbcSqliteDriver(news.db)"
    }
}
