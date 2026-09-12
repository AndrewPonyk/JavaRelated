import 'package:sqflite/sqflite.dart';
import '../../../../core/constants/db_constants.dart';
import '../../../../core/database/app_database.dart';
import '../../../../core/errors/exceptions.dart';
import '../models/habit_completion_model.dart';

/// Direct Data Access Object for completions and streak freezes
class HabitCompletionLocalDatasource {
  final AppDatabase appDatabase;

  HabitCompletionLocalDatasource({required this.appDatabase});

  /// Check if completed on specific date
  Future<bool> isCompleted({
    required String habitId,
    required String dateString,
  }) async {
    try {
      final db = await appDatabase.database;
      final result = await db.query(
        DbConstants.tableCompletions,
        where:
            '${DbConstants.colCompletionHabitId} = ? AND ${DbConstants.colCompletionDate} = ?',
        whereArgs: [habitId, dateString],
        limit: 1,
      );
      return result.isNotEmpty;
    } catch (e) {
      throw AppDatabaseException('Failed to check completion status', e);
    }
  }

  /// Toggle completion record (atomically insert or delete)
  Future<bool> toggleCompletion({
    required String id,
    required String habitId,
    required String dateString,
  }) async {
    try {
      final db = await appDatabase.database;
      return await db.transaction<bool>((txn) async {
        final existing = await txn.query(
          DbConstants.tableCompletions,
          where:
              '${DbConstants.colCompletionHabitId} = ? AND ${DbConstants.colCompletionDate} = ?',
          whereArgs: [habitId, dateString],
          limit: 1,
        );

        if (existing.isNotEmpty) {
          await txn.delete(
            DbConstants.tableCompletions,
            where:
                '${DbConstants.colCompletionHabitId} = ? AND ${DbConstants.colCompletionDate} = ?',
            whereArgs: [habitId, dateString],
          );
          return false; // Unchecked
        } else {
          await txn.insert(
            DbConstants.tableCompletions,
            {
              DbConstants.colCompletionId: id,
              DbConstants.colCompletionHabitId: habitId,
              DbConstants.colCompletionDate: dateString,
              DbConstants.colCompletionCount: 1,
              DbConstants.colCompletionCreatedAt: DateTime.now()
                  .toIso8601String(),
            },
            conflictAlgorithm: ConflictAlgorithm.replace,
          );
          return true; // Marked completed
        }
      });
    } catch (e) {
      throw AppDatabaseException('Failed to toggle completion', e);
    }
  }

  /// Fetch all completed dates for a specific habit
  Future<Set<String>> getCompletedDates(String habitId) async {
    try {
      final db = await appDatabase.database;
      final List<Map<String, dynamic>> maps = await db.query(
        DbConstants.tableCompletions,
        columns: [DbConstants.colCompletionDate],
        where: '${DbConstants.colCompletionHabitId} = ?',
        whereArgs: [habitId],
      );
      return maps
          .map((m) => m[DbConstants.colCompletionDate] as String)
          .toSet();
    } catch (e) {
      throw AppDatabaseException('Failed to fetch completed dates', e);
    }
  }

  /// Batch fetch all completed dates for multiple habits in a single query (fixes N+1)
  Future<Map<String, Set<String>>> getBatchCompletedDates(
    List<String> habitIds,
  ) async {
    if (habitIds.isEmpty) return {};
    try {
      final db = await appDatabase.database;
      final placeholders = List.filled(habitIds.length, '?').join(',');
      final List<Map<String, dynamic>> maps = await db.query(
        DbConstants.tableCompletions,
        columns: [
          DbConstants.colCompletionHabitId,
          DbConstants.colCompletionDate,
        ],
        where: '${DbConstants.colCompletionHabitId} IN ($placeholders)',
        whereArgs: habitIds,
      );

      final Map<String, Set<String>> result = {
        for (final id in habitIds) id: <String>{},
      };
      for (final map in maps) {
        final habitId = map[DbConstants.colCompletionHabitId] as String;
        final date = map[DbConstants.colCompletionDate] as String;
        result[habitId]?.add(date);
      }
      return result;
    } catch (e) {
      throw AppDatabaseException('Failed to batch fetch completed dates', e);
    }
  }

  /// Fetch all completions within a date range (for heatmaps)
  Future<List<HabitCompletionModel>> getCompletionsForRange({
    required String startDate,
    required String endDate,
  }) async {
    try {
      final db = await appDatabase.database;
      final List<Map<String, dynamic>> maps = await db.query(
        DbConstants.tableCompletions,
        where:
            '${DbConstants.colCompletionDate} >= ? AND ${DbConstants.colCompletionDate} <= ?',
        whereArgs: [startDate, endDate],
        orderBy: '${DbConstants.colCompletionDate} ASC',
      );
      return maps.map((m) => HabitCompletionModel.fromMap(m)).toList();
    } catch (e) {
      throw AppDatabaseException('Failed to fetch completions for range', e);
    }
  }

  /// Apply a streak freeze record
  Future<void> insertStreakFreeze({
    required String id,
    required String habitId,
    required String dateString,
    String? reason,
  }) async {
    try {
      final db = await appDatabase.database;
      await db.insert(
        DbConstants.tableStreakFreezes,
        {
          DbConstants.colFreezeId: id,
          DbConstants.colFreezeHabitId: habitId,
          DbConstants.colFreezeDate: dateString,
          DbConstants.colFreezeReason: reason,
          DbConstants.colFreezeCreatedAt: DateTime.now().toIso8601String(),
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    } catch (e) {
      throw AppDatabaseException('Failed to insert streak freeze', e);
    }
  }

  /// Fetch all freeze dates for a habit
  Future<Set<String>> getFreezeDates(String habitId) async {
    try {
      final db = await appDatabase.database;
      final List<Map<String, dynamic>> maps = await db.query(
        DbConstants.tableStreakFreezes,
        columns: [DbConstants.colFreezeDate],
        where: '${DbConstants.colFreezeHabitId} = ?',
        whereArgs: [habitId],
      );
      return maps.map((m) => m[DbConstants.colFreezeDate] as String).toSet();
    } catch (e) {
      throw AppDatabaseException('Failed to fetch freeze dates', e);
    }
  }

  /// Batch fetch all freeze dates for multiple habits in a single query (fixes N+1)
  Future<Map<String, Set<String>>> getBatchFreezeDates(
    List<String> habitIds,
  ) async {
    if (habitIds.isEmpty) return {};
    try {
      final db = await appDatabase.database;
      final placeholders = List.filled(habitIds.length, '?').join(',');
      final List<Map<String, dynamic>> maps = await db.query(
        DbConstants.tableStreakFreezes,
        columns: [DbConstants.colFreezeHabitId, DbConstants.colFreezeDate],
        where: '${DbConstants.colFreezeHabitId} IN ($placeholders)',
        whereArgs: habitIds,
      );

      final Map<String, Set<String>> result = {
        for (final id in habitIds) id: <String>{},
      };
      for (final map in maps) {
        final habitId = map[DbConstants.colFreezeHabitId] as String;
        final date = map[DbConstants.colFreezeDate] as String;
        result[habitId]?.add(date);
      }
      return result;
    } catch (e) {
      throw AppDatabaseException('Failed to batch fetch freeze dates', e);
    }
  }
}
