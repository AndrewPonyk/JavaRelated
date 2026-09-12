import 'package:sqflite/sqflite.dart';
import '../../constants/db_constants.dart';
import 'migration.dart';

/// Migration 1: Initial schema for habits and habit_completions
class V1Schema implements Migration {
  @override
  int get version => 1;

  @override
  Future<void> up(Database db) async {
    // 1. Habits Table
    await db.execute('''
      CREATE TABLE ${DbConstants.tableHabits} (
        ${DbConstants.colHabitId} TEXT PRIMARY KEY NOT NULL,
        ${DbConstants.colHabitTitle} TEXT NOT NULL,
        ${DbConstants.colHabitDescription} TEXT,
        ${DbConstants.colHabitFrequencyType} TEXT NOT NULL,
        ${DbConstants.colHabitSpecificDays} TEXT,
        ${DbConstants.colHabitTargetCount} INTEGER NOT NULL DEFAULT 1,
        ${DbConstants.colHabitReminderTime} TEXT,
        ${DbConstants.colHabitColorValue} INTEGER NOT NULL DEFAULT 4282557941,
        ${DbConstants.colHabitCreatedAt} TEXT NOT NULL,
        ${DbConstants.colHabitIsArchived} INTEGER NOT NULL DEFAULT 0
      );
    ''');

    // Index for quick habit filtering by archive state
    await db.execute('''
      CREATE INDEX idx_habits_is_archived 
      ON ${DbConstants.tableHabits} (${DbConstants.colHabitIsArchived});
    ''');

    // 2. Habit Completions Table
    await db.execute('''
      CREATE TABLE ${DbConstants.tableCompletions} (
        ${DbConstants.colCompletionId} TEXT PRIMARY KEY NOT NULL,
        ${DbConstants.colCompletionHabitId} TEXT NOT NULL,
        ${DbConstants.colCompletionDate} TEXT NOT NULL,
        ${DbConstants.colCompletionCount} INTEGER NOT NULL DEFAULT 1,
        ${DbConstants.colCompletionCreatedAt} TEXT NOT NULL,
        FOREIGN KEY (${DbConstants.colCompletionHabitId}) 
          REFERENCES ${DbConstants.tableHabits} (${DbConstants.colHabitId}) 
          ON DELETE CASCADE
      );
    ''');

    // Unique index ensures one completion entry per habit per date
    await db.execute('''
      CREATE UNIQUE INDEX idx_completions_habit_date 
      ON ${DbConstants.tableCompletions} (
        ${DbConstants.colCompletionHabitId}, 
        ${DbConstants.colCompletionDate}
      );
    ''');

    // Index on completed_date to accelerate calendar heatmap range queries
    await db.execute('''
      CREATE INDEX idx_completions_completed_date 
      ON ${DbConstants.tableCompletions} (${DbConstants.colCompletionDate});
    ''');
  }

  @override
  Future<void> down(Database db) async {
    await db.execute('DROP TABLE IF EXISTS ${DbConstants.tableCompletions};');
    await db.execute('DROP TABLE IF EXISTS ${DbConstants.tableHabits};');
  }
}
