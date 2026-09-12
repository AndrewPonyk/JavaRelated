package org.example.project.data.local

/**
 * iOS Native SQLite Driver using NativeSqliteDriver.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): Any {
        // In full SQLDelight build: NativeSqliteDriver(NewsDatabase.Schema, "news.db")
        return "NativeSqliteDriver(news.db)"
    }
}
