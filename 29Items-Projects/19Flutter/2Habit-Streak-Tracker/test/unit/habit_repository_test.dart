import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/core/database/app_database.dart';
import 'package:habit_streak_tracker/core/errors/failures.dart';
import 'package:habit_streak_tracker/core/utils/date_time_utils.dart';
import 'package:habit_streak_tracker/features/habits/data/datasources/habit_completion_local_datasource.dart';
import 'package:habit_streak_tracker/features/habits/data/datasources/habit_local_datasource.dart';
import 'package:habit_streak_tracker/features/habits/data/repositories/habit_repository_impl.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';
import 'package:mocktail/mocktail.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

class MockAppDatabase extends Mock implements AppDatabase {}

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  group('HabitRepositoryImpl Integration Tests', () {
    late Database db;
    late MockAppDatabase mockAppDb;
    late HabitLocalDatasource habitDs;
    late HabitCompletionLocalDatasource completionDs;
    late HabitRepositoryImpl repository;

    setUp(() async {
      db = await openDatabase(
        inMemoryDatabasePath,
        version: 2,
        onConfigure: (database) async {
          await database.execute('PRAGMA foreign_keys = ON;');
        },
        onCreate: (database, version) async {
          await database.execute('''
            CREATE TABLE habits (
              id TEXT PRIMARY KEY NOT NULL,
              title TEXT NOT NULL,
              description TEXT,
              frequency_type TEXT NOT NULL,
              specific_days TEXT,
              target_count INTEGER NOT NULL DEFAULT 1,
              reminder_time TEXT,
              color_value INTEGER NOT NULL DEFAULT 4282557941,
              created_at TEXT NOT NULL,
              is_archived INTEGER NOT NULL DEFAULT 0
            );
          ''');
          await database.execute('''
            CREATE TABLE habit_completions (
              id TEXT PRIMARY KEY NOT NULL,
              habit_id TEXT NOT NULL,
              completed_date TEXT NOT NULL,
              count INTEGER NOT NULL DEFAULT 1,
              created_at TEXT NOT NULL,
              FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE
            );
          ''');
          await database.execute('''
            CREATE TABLE streak_freezes (
              id TEXT PRIMARY KEY NOT NULL,
              habit_id TEXT NOT NULL,
              freeze_date TEXT NOT NULL,
              reason TEXT,
              created_at TEXT NOT NULL,
              FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE
            );
          ''');
        },
      );

      mockAppDb = MockAppDatabase();
      when(() => mockAppDb.database).thenAnswer((_) async => db);

      habitDs = HabitLocalDatasource(appDatabase: mockAppDb);
      completionDs = HabitCompletionLocalDatasource(appDatabase: mockAppDb);
      repository = HabitRepositoryImpl(
        habitDatasource: habitDs,
        completionDatasource: completionDs,
      );
    });

    tearDown(() async {
      await db.close();
    });

    test(
      'createHabit inserts habit and getActiveHabits retrieves it',
      () async {
        final habit = Habit(
          id: 'h-100',
          title: 'Drink Water',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime(2026, 9, 6),
        );

        await repository.createHabit(habit);
        final active = await repository.getActiveHabits();

        expect(active.length, equals(1));
        expect(active.first.id, equals('h-100'));
        expect(active.first.title, equals('Drink Water'));
        expect(active.first.currentStreak, equals(0));
      },
    );

    test(
      'toggleCompletion records completion and recalculates ongoing streak',
      () async {
        final habit = Habit(
          id: 'h-101',
          title: 'Morning Yoga',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime(2026, 9, 6),
        );
        await repository.createHabit(habit);

        final todayStr = DateTimeUtils.todayString();

        // First toggle: complete
        final markedDone = await repository.toggleCompletion(
          habitId: 'h-101',
          dateString: todayStr,
        );
        expect(markedDone, isTrue);

        var retrieved = await repository.getHabitById('h-101');
        expect(retrieved, isNotNull);
        expect(retrieved!.isCompletedToday, isTrue);
        expect(retrieved.currentStreak, equals(1));

        // Second toggle: uncheck
        final markedUndone = await repository.toggleCompletion(
          habitId: 'h-101',
          dateString: todayStr,
        );
        expect(markedUndone, isFalse);

        retrieved = await repository.getHabitById('h-101');
        expect(retrieved!.isCompletedToday, isFalse);
        expect(retrieved.currentStreak, equals(0));
      },
    );

    test('applyStreakFreeze preserves streak across missed days', () async {
      final habit = Habit(
        id: 'h-102',
        title: 'Read Book',
        frequency: const HabitFrequency(type: FrequencyType.daily),
        createdAt: DateTime(2026, 9, 1),
      );
      await repository.createHabit(habit);

      final today = DateTimeUtils.today();
      final todayStr = DateTimeUtils.toDateString(today);
      final yesterdayStr = DateTimeUtils.toDateString(
        today.subtract(const Duration(days: 1)),
      );
      final twoDaysAgoStr = DateTimeUtils.toDateString(
        today.subtract(const Duration(days: 2)),
      );

      // Completed 2 days ago and today, missed yesterday
      await repository.toggleCompletion(
        habitId: 'h-102',
        dateString: twoDaysAgoStr,
      );
      await repository.toggleCompletion(habitId: 'h-102', dateString: todayStr);

      // Without freeze, current streak is 1 (yesterday was missed)
      var check = await repository.getHabitById('h-102');
      expect(check!.currentStreak, equals(1));

      // Apply freeze on yesterday
      await repository.applyStreakFreeze(
        habitId: 'h-102',
        dateString: yesterdayStr,
        reason: 'Fever',
      );

      // Now streak bridges through freeze: 2 active days
      check = await repository.getHabitById('h-102');
      expect(check!.currentStreak, equals(2));
    });

    test('deleteHabit cascades and deletes all completion records', () async {
      final habit = Habit(
        id: 'h-103',
        title: 'Cardio',
        frequency: const HabitFrequency(type: FrequencyType.daily),
        createdAt: DateTime.now(),
      );
      await repository.createHabit(habit);
      await repository.toggleCompletion(
        habitId: 'h-103',
        dateString: '2026-09-06',
      );

      await repository.deleteHabit('h-103');

      final active = await repository.getActiveHabits();
      expect(active, isEmpty);

      final completions = await repository.getCompletedDates('h-103');
      expect(completions, isEmpty);
    });

    test(
      'Validation: Throws ValidationFailure on empty or whitespace habit title',
      () async {
        final emptyHabit = Habit(
          id: 'h-inv-1',
          title: '   ',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime.now(),
        );

        expect(
          () => repository.createHabit(emptyHabit),
          throwsA(isA<ValidationFailure>()),
        );
      },
    );

    test(
      'Validation: Throws ValidationFailure when title exceeds 100 chars',
      () async {
        final longTitleHabit = Habit(
          id: 'h-inv-2',
          title: 'A' * 101,
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime.now(),
        );

        expect(
          () => repository.createHabit(longTitleHabit),
          throwsA(isA<ValidationFailure>()),
        );
      },
    );

    test(
      'Validation: Throws ValidationFailure on invalid reminder time format',
      () async {
        final badTimeHabit = Habit(
          id: 'h-inv-3',
          title: 'Walk Dog',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime.now(),
          reminderTime: '25:99', // Invalid 24-hour time
        );

        expect(
          () => repository.createHabit(badTimeHabit),
          throwsA(isA<ValidationFailure>()),
        );
      },
    );

    test(
      'Validation: Throws ValidationFailure on malformed date string in toggleCompletion',
      () async {
        expect(
          () => repository.toggleCompletion(
            habitId: 'h-100',
            dateString: 'invalid-date',
          ),
          throwsA(isA<ValidationFailure>()),
        );
      },
    );

    test(
      'Batch fetching: Correctly groups completions and freezes for multiple habits',
      () async {
        final habitA = Habit(
          id: 'h-batch-a',
          title: 'Habit A',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime.now(),
        );
        final habitB = Habit(
          id: 'h-batch-b',
          title: 'Habit B',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime.now(),
        );

        await repository.createHabit(habitA);
        await repository.createHabit(habitB);

        await repository.toggleCompletion(
          habitId: 'h-batch-a',
          dateString: '2026-09-05',
        );
        await repository.toggleCompletion(
          habitId: 'h-batch-a',
          dateString: '2026-09-06',
        );
        await repository.toggleCompletion(
          habitId: 'h-batch-b',
          dateString: '2026-09-06',
        );

        final activeList = await repository.getActiveHabits();
        final itemA = activeList.firstWhere((h) => h.id == 'h-batch-a');
        final itemB = activeList.firstWhere((h) => h.id == 'h-batch-b');

        expect(itemA.currentStreak, equals(2));
        expect(itemB.currentStreak, equals(1));
      },
    );
  });
}
