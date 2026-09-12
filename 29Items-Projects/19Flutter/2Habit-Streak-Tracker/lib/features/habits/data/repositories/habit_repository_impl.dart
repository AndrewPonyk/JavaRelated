import 'package:uuid/uuid.dart';
import '../../../../core/errors/exceptions.dart';
import '../../../../core/errors/failures.dart';
import '../../../../core/utils/date_time_utils.dart';
import '../../../../core/utils/streak_calculator.dart';
import '../../domain/models/habit.dart';
import '../../domain/models/habit_completion.dart';
import '../../domain/repositories/habit_repository.dart';
import '../datasources/habit_completion_local_datasource.dart';
import '../datasources/habit_local_datasource.dart';
import '../models/habit_model.dart';

/// Concrete repository coordinating habit persistence, input validation, and streak calculations
class HabitRepositoryImpl implements HabitRepository {
  final HabitLocalDatasource habitDatasource;
  final HabitCompletionLocalDatasource completionDatasource;
  final Uuid _uuid = const Uuid();

  // Input validation patterns
  static final RegExp _timeRegex = RegExp(r'^([01]\d|2[0-3]):([0-5]\d)$');
  static final RegExp _dateRegex = RegExp(r'^\d{4}-\d{2}-\d{2}$');

  HabitRepositoryImpl({
    required this.habitDatasource,
    required this.completionDatasource,
  });

  void _validateHabit(Habit habit) {
    final title = habit.title.trim();
    if (title.isEmpty) {
      throw const ValidationFailure('Habit title cannot be empty');
    }
    if (title.length > 100) {
      throw const ValidationFailure('Habit title cannot exceed 100 characters');
    }

    if (habit.description != null && habit.description!.length > 500) {
      throw const ValidationFailure(
        'Habit description cannot exceed 500 characters',
      );
    }

    if (habit.targetCount < 1 || habit.targetCount > 100) {
      throw const ValidationFailure('Target count must be between 1 and 100');
    }

    if (habit.reminderTime != null &&
        !_timeRegex.hasMatch(habit.reminderTime!)) {
      throw const ValidationFailure(
        'Reminder time must be in HH:mm 24-hour format',
      );
    }
  }

  void _validateDateString(String dateString) {
    if (!_dateRegex.hasMatch(dateString)) {
      throw ValidationFailure(
        'Invalid date format, expected YYYY-MM-DD: $dateString',
      );
    }
  }

  @override
  Future<List<Habit>> getActiveHabits() async {
    try {
      final models = await habitDatasource.getActiveHabits();
      if (models.isEmpty) return [];

      // Optimize: Batch fetch all completion and freeze dates in 2 queries (Fixes N+1 problem)
      final habitIds = models.map((m) => m.id).toList();
      final batchCompletions = await completionDatasource
          .getBatchCompletedDates(habitIds);
      final batchFreezes = await completionDatasource.getBatchFreezeDates(
        habitIds,
      );

      final todayStr = DateTimeUtils.todayString();
      final List<Habit> habits = [];

      for (final model in models) {
        final completedDates = batchCompletions[model.id] ?? const {};
        final freezeDates = batchFreezes[model.id] ?? const {};

        final habitEntity = model.toDomain();
        final currentStreak = StreakCalculator.calculateCurrentStreak(
          completedDates: completedDates,
          freezeDates: freezeDates,
          scheduledWeekdays: habitEntity.frequency.specificDays,
        );

        final bestStreak = StreakCalculator.calculateBestStreak(
          completedDates: completedDates,
          freezeDates: freezeDates,
          scheduledWeekdays: habitEntity.frequency.specificDays,
        );

        final isCompletedToday = completedDates.contains(todayStr);

        habits.add(
          habitEntity.copyWith(
            currentStreak: currentStreak,
            bestStreak: bestStreak,
            isCompletedToday: isCompletedToday,
          ),
        );
      }

      return habits;
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<Habit?> getHabitById(String id) async {
    try {
      final model = await habitDatasource.getHabitById(id);
      if (model == null) return null;

      final completedDates = await completionDatasource.getCompletedDates(id);
      final freezeDates = await completionDatasource.getFreezeDates(id);
      final habitEntity = model.toDomain();

      final currentStreak = StreakCalculator.calculateCurrentStreak(
        completedDates: completedDates,
        freezeDates: freezeDates,
        scheduledWeekdays: habitEntity.frequency.specificDays,
      );

      final bestStreak = StreakCalculator.calculateBestStreak(
        completedDates: completedDates,
        freezeDates: freezeDates,
        scheduledWeekdays: habitEntity.frequency.specificDays,
      );

      final isCompletedToday = completedDates.contains(
        DateTimeUtils.todayString(),
      );

      return habitEntity.copyWith(
        currentStreak: currentStreak,
        bestStreak: bestStreak,
        isCompletedToday: isCompletedToday,
      );
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<void> createHabit(Habit habit) async {
    _validateHabit(habit);
    try {
      final model = HabitModel.fromDomain(habit);
      await habitDatasource.insertHabit(model);
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<void> updateHabit(Habit habit) async {
    _validateHabit(habit);
    try {
      final model = HabitModel.fromDomain(habit);
      await habitDatasource.updateHabit(model);
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<void> archiveHabit(String id) async {
    try {
      await habitDatasource.archiveHabit(id);
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<void> deleteHabit(String id) async {
    try {
      await habitDatasource.deleteHabit(id);
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<bool> toggleCompletion({
    required String habitId,
    required String dateString,
  }) async {
    _validateDateString(dateString);
    try {
      final id = _uuid.v4();
      return await completionDatasource.toggleCompletion(
        id: id,
        habitId: habitId,
        dateString: dateString,
      );
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<Set<String>> getCompletedDates(String habitId) async {
    try {
      return await completionDatasource.getCompletedDates(habitId);
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<List<HabitCompletion>> getCompletionsForRange({
    required String startDate,
    required String endDate,
  }) async {
    _validateDateString(startDate);
    _validateDateString(endDate);
    try {
      final models = await completionDatasource.getCompletionsForRange(
        startDate: startDate,
        endDate: endDate,
      );
      return models.map((m) => m.toDomain()).toList();
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<void> applyStreakFreeze({
    required String habitId,
    required String dateString,
    String? reason,
  }) async {
    _validateDateString(dateString);
    try {
      final id = _uuid.v4();
      await completionDatasource.insertStreakFreeze(
        id: id,
        habitId: habitId,
        dateString: dateString,
        reason: reason,
      );
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }

  @override
  Future<Set<String>> getFreezeDates(String habitId) async {
    try {
      return await completionDatasource.getFreezeDates(habitId);
    } on AppDatabaseException catch (e) {
      throw DatabaseFailure(e.message);
    }
  }
}
