import '../models/habit.dart';
import '../models/habit_completion.dart';

/// Abstract repository contract for habit data operations
abstract class HabitRepository {
  /// Fetch all active (non-archived) habits with computed streak info
  Future<List<Habit>> getActiveHabits();

  /// Fetch a single habit by id with history
  Future<Habit?> getHabitById(String id);

  /// Create a new habit
  Future<void> createHabit(Habit habit);

  /// Update existing habit settings
  Future<void> updateHabit(Habit habit);

  /// Soft-delete / archive habit
  Future<void> archiveHabit(String id);

  /// Hard delete habit and cascade completions
  Future<void> deleteHabit(String id);

  /// Toggle habit completion for a specific date ('YYYY-MM-DD').
  /// Returns true if marked complete, false if unchecked.
  Future<bool> toggleCompletion({
    required String habitId,
    required String dateString,
  });

  /// Get all completed date strings ('YYYY-MM-DD') for a habit
  Future<Set<String>> getCompletedDates(String habitId);

  /// Get all completion records across all habits for a date range (for heatmaps)
  Future<List<HabitCompletion>> getCompletionsForRange({
    required String startDate,
    required String endDate,
  });

  /// Apply a streak freeze for a habit on a missed date
  Future<void> applyStreakFreeze({
    required String habitId,
    required String dateString,
    String? reason,
  });

  /// Get all freeze date strings ('YYYY-MM-DD') for a habit
  Future<Set<String>> getFreezeDates(String habitId);
}
