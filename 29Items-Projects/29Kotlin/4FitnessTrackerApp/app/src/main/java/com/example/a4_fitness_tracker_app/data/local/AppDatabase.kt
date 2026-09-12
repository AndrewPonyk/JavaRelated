package com.example.a4_fitness_tracker_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.a4_fitness_tracker_app.data.local.dao.GoalDao
import com.example.a4_fitness_tracker_app.data.local.dao.WorkoutDao
import com.example.a4_fitness_tracker_app.data.local.entity.GoalEntity
import com.example.a4_fitness_tracker_app.data.local.entity.WorkoutEntity

@Database(
    entities = [WorkoutEntity::class, GoalEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitness_tracker.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
