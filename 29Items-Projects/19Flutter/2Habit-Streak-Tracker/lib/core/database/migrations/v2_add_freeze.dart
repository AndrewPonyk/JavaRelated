import 'package:sqflite/sqflite.dart';
import '../../constants/db_constants.dart';
import 'migration.dart';

/// Migration 2: Adds streak_freezes table for rest & sick days
class V2AddFreeze implements Migration {
  @override
  int get version => 2;

  @override
  Future<void> up(Database db) async {
    await db.execute('''
      CREATE TABLE ${DbConstants.tableStreakFreezes} (
        ${DbConstants.colFreezeId} TEXT PRIMARY KEY NOT NULL,
        ${DbConstants.colFreezeHabitId} TEXT NOT NULL,
        ${DbConstants.colFreezeDate} TEXT NOT NULL,
        ${DbConstants.colFreezeReason} TEXT,
        ${DbConstants.colFreezeCreatedAt} TEXT NOT NULL,
        FOREIGN KEY (${DbConstants.colFreezeHabitId}) 
          REFERENCES ${DbConstants.tableHabits} (${DbConstants.colHabitId}) 
          ON DELETE CASCADE
      );
    ''');

    await db.execute('''
      CREATE UNIQUE INDEX idx_freezes_habit_date 
      ON ${DbConstants.tableStreakFreezes} (
        ${DbConstants.colFreezeHabitId}, 
        ${DbConstants.colFreezeDate}
      );
    ''');
  }

  @override
  Future<void> down(Database db) async {
    await db.execute('DROP TABLE IF EXISTS ${DbConstants.tableStreakFreezes};');
  }
}
