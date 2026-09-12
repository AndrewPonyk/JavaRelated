import 'package:sqflite/sqflite.dart';
import '../../../../core/constants/db_constants.dart';
import '../../../../core/database/app_database.dart';
import '../../../../core/errors/exceptions.dart';
import '../models/habit_model.dart';

/// Direct Data Access Object for habits table in SQLite
class HabitLocalDatasource {
  final AppDatabase appDatabase;

  HabitLocalDatasource({required this.appDatabase});

  Future<List<HabitModel>> getActiveHabits() async {
    try {
      final db = await appDatabase.database;
      final List<Map<String, dynamic>> maps = await db.query(
        DbConstants.tableHabits,
        where: '${DbConstants.colHabitIsArchived} = ?',
        whereArgs: [0],
        orderBy: '${DbConstants.colHabitCreatedAt} ASC',
      );
      return maps.map((m) => HabitModel.fromMap(m)).toList();
    } catch (e) {
      throw AppDatabaseException('Failed to fetch active habits', e);
    }
  }

  Future<HabitModel?> getHabitById(String id) async {
    try {
      final db = await appDatabase.database;
      final List<Map<String, dynamic>> maps = await db.query(
        DbConstants.tableHabits,
        where: '${DbConstants.colHabitId} = ?',
        whereArgs: [id],
        limit: 1,
      );
      if (maps.isEmpty) return null;
      return HabitModel.fromMap(maps.first);
    } catch (e) {
      throw AppDatabaseException('Failed to fetch habit by id: $id', e);
    }
  }

  Future<void> insertHabit(HabitModel model) async {
    try {
      final db = await appDatabase.database;
      await db.insert(
        DbConstants.tableHabits,
        model.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    } catch (e) {
      throw AppDatabaseException('Failed to insert habit', e);
    }
  }

  Future<void> updateHabit(HabitModel model) async {
    try {
      final db = await appDatabase.database;
      await db.update(
        DbConstants.tableHabits,
        model.toMap(),
        where: '${DbConstants.colHabitId} = ?',
        whereArgs: [model.id],
      );
    } catch (e) {
      throw AppDatabaseException('Failed to update habit', e);
    }
  }

  Future<void> archiveHabit(String id) async {
    try {
      final db = await appDatabase.database;
      await db.update(
        DbConstants.tableHabits,
        {DbConstants.colHabitIsArchived: 1},
        where: '${DbConstants.colHabitId} = ?',
        whereArgs: [id],
      );
    } catch (e) {
      throw AppDatabaseException('Failed to archive habit: $id', e);
    }
  }

  Future<void> deleteHabit(String id) async {
    try {
      final db = await appDatabase.database;
      await db.delete(
        DbConstants.tableHabits,
        where: '${DbConstants.colHabitId} = ?',
        whereArgs: [id],
      );
    } catch (e) {
      throw AppDatabaseException('Failed to delete habit: $id', e);
    }
  }
}
