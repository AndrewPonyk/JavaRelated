package org.example.project.data.local

/**
 * Platform-agnostic factory for initializing SQLite drivers across Android, Desktop JVM, and iOS.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): Any // In SQLDelight: SqlDriver
}
