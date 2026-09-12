package org.example.project.data.local

import android.content.Context

/**
 * Android SQLite Driver using AndroidSqliteDriver.
 */
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): Any {
        // In full SQLDelight build: AndroidSqliteDriver(NewsDatabase.Schema, context, "news.db")
        return "AndroidSqliteDriver(news.db)"
    }
}
