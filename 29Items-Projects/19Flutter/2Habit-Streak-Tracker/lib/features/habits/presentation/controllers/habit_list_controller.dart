import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/utils/date_time_utils.dart';
import '../../domain/models/habit.dart';
import '../providers/habit_providers.dart';

/// AsyncNotifier managing reactive habit list state, optimistic toggling, and background sync
class HabitListController extends AsyncNotifier<List<Habit>> {
  @override
  Future<List<Habit>> build() async {
    final repository = ref.watch(habitRepositoryProvider);
    final habits = await repository.getActiveHabits();
    // Sync initial state with home widget
    await ref.read(homeWidgetManagerProvider).syncHabitsToWidget(habits);
    return habits;
  }

  /// Toggle habit completion with optimistic UI update and failure rollback
  Future<void> toggleHabitCompletion(String habitId) async {
    final previousState = state;
    final currentList = state.value ?? [];
    final todayStr = DateTimeUtils.todayString();

    // 1. Compute Optimistic State
    final updatedList = currentList.map((habit) {
      if (habit.id == habitId) {
        final newCompleted = !habit.isCompletedToday;
        final newStreak = newCompleted
            ? habit.currentStreak + 1
            : (habit.currentStreak > 0 ? habit.currentStreak - 1 : 0);
        final newBest = newStreak > habit.bestStreak
            ? newStreak
            : habit.bestStreak;

        return habit.copyWith(
          isCompletedToday: newCompleted,
          currentStreak: newStreak,
          bestStreak: newBest,
        );
      }
      return habit;
    }).toList();

    // Emit optimistic state immediately
    state = AsyncData(updatedList);

    try {
      final repository = ref.read(habitRepositoryProvider);
      await repository.toggleCompletion(habitId: habitId, dateString: todayStr);

      // Refresh real values from repository to ensure consistency
      final refreshed = await repository.getActiveHabits();
      state = AsyncData(refreshed);

      // Sync with Native Home Screen Widget
      await ref.read(homeWidgetManagerProvider).syncHabitsToWidget(refreshed);
    } catch (error, stack) {
      // Rollback to previous state on error
      state = previousState;
      state = AsyncError(error, stack);
    }
  }

  /// Add a new habit and schedule reminders
  Future<void> addHabit(Habit habit) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repository = ref.read(habitRepositoryProvider);
      await repository.createHabit(habit);

      // Schedule notification reminder if configured
      if (habit.reminderTime != null) {
        final notifService = ref.read(notificationServiceProvider);
        await notifService.scheduleDailyHabitReminder(
          habitNotificationId: habit.id.hashCode,
          habitTitle: habit.title,
          reminderTime: habit.reminderTime!,
        );
      }

      final habits = await repository.getActiveHabits();
      await ref.read(homeWidgetManagerProvider).syncHabitsToWidget(habits);
      return habits;
    });
  }

  /// Archive a habit
  Future<void> archiveHabit(String habitId) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repository = ref.read(habitRepositoryProvider);
      await repository.archiveHabit(habitId);

      // Cancel reminder
      final notifService = ref.read(notificationServiceProvider);
      await notifService.cancelReminder(habitId.hashCode);

      final habits = await repository.getActiveHabits();
      await ref.read(homeWidgetManagerProvider).syncHabitsToWidget(habits);
      return habits;
    });
  }

  /// Apply a streak freeze for a habit
  Future<void> applyStreakFreeze(
    String habitId,
    String dateString, {
    String? reason,
  }) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repository = ref.read(habitRepositoryProvider);
      await repository.applyStreakFreeze(
        habitId: habitId,
        dateString: dateString,
        reason: reason,
      );

      final habits = await repository.getActiveHabits();
      return habits;
    });
  }
}
