import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/core/constants/db_constants.dart';
import 'package:habit_streak_tracker/core/database/migrations/v1_schema.dart';
import 'package:habit_streak_tracker/core/database/migrations/v2_add_freeze.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  // Initialize ffi loader for in-memory SQLite test execution
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  group('Database Schema & Migration Tests', () {
    late Database db;

    setUp(() async {
      db = await openDatabase(inMemoryDatabasePath, version: 1);
    });

    tearDown(() async {
      await db.close();
    });

    test(
      'V1Schema creates habits and completions tables with proper columns',
      () async {
        final v1 = V1Schema();
        await v1.up(db);

        // Verify habits table exists
        final habitsInfo = await db.rawQuery(
          'PRAGMA table_info(${DbConstants.tableHabits})',
        );
        final habitColumns = habitsInfo
            .map((row) => row['name'] as String)
            .toList();

        expect(habitColumns, contains(DbConstants.colHabitId));
        expect(habitColumns, contains(DbConstants.colHabitTitle));
        expect(habitColumns, contains(DbConstants.colHabitFrequencyType));
        expect(habitColumns, contains(DbConstants.colHabitIsArchived));

        // Verify completions table exists
        final completionsInfo = await db.rawQuery(
          'PRAGMA table_info(${DbConstants.tableCompletions})',
        );
        final completionColumns = completionsInfo
            .map((row) => row['name'] as String)
            .toList();

        expect(completionColumns, contains(DbConstants.colCompletionId));
        expect(completionColumns, contains(DbConstants.colCompletionHabitId));
        expect(completionColumns, contains(DbConstants.colCompletionDate));
      },
    );

    test(
      'V2AddFreeze adds streak_freezes table with foreign key to habits',
      () async {
        final v1 = V1Schema();
        await v1.up(db);

        final v2 = V2AddFreeze();
        await v2.up(db);

        // Verify streak_freezes table
        final freezeInfo = await db.rawQuery(
          'PRAGMA table_info(${DbConstants.tableStreakFreezes})',
        );
        final freezeColumns = freezeInfo
            .map((row) => row['name'] as String)
            .toList();

        expect(freezeColumns, contains(DbConstants.colFreezeId));
        expect(freezeColumns, contains(DbConstants.colFreezeHabitId));
        expect(freezeColumns, contains(DbConstants.colFreezeDate));
        expect(freezeColumns, contains(DbConstants.colFreezeReason));
      },
    );

    test('V1 and V2 down migrations drop tables cleanly', () async {
      final v1 = V1Schema();
      final v2 = V2AddFreeze();
      await v1.up(db);
      await v2.up(db);

      await v2.down(db);
      final freezeCheck = await db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='${DbConstants.tableStreakFreezes}';",
      );
      expect(freezeCheck, isEmpty);

      await v1.down(db);
      final habitCheck = await db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='${DbConstants.tableHabits}';",
      );
      expect(habitCheck, isEmpty);
    });
  });
}
