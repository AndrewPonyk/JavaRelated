import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../domain/models/habit.dart';
import '../providers/habit_providers.dart';

/// Provider family for managing individual habit detail state
final habitDetailControllerProvider =
    AsyncNotifierProvider.family<HabitDetailController, Habit?, String>(() {
      return HabitDetailController();
    });

class HabitDetailController extends FamilyAsyncNotifier<Habit?, String> {
  @override
  Future<Habit?> build(String arg) async {
    final repository = ref.watch(habitRepositoryProvider);
    return await repository.getHabitById(arg);
  }

  /// Update habit attributes and synchronize platform reminders
  Future<void> updateHabit(Habit updatedHabit) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repository = ref.read(habitRepositoryProvider);
      await repository.updateHabit(updatedHabit);

      // Reschedule or cancel notification reminder
      final notifService = ref.read(notificationServiceProvider);
      if (updatedHabit.reminderTime != null) {
        await notifService.scheduleDailyHabitReminder(
          habitNotificationId: updatedHabit.id.hashCode,
          habitTitle: updatedHabit.title,
          reminderTime: updatedHabit.reminderTime!,
        );
      } else {
        await notifService.cancelReminder(updatedHabit.id.hashCode);
      }

      // Sync active habits to home widget
      final activeHabits = await repository.getActiveHabits();
      await ref
          .read(homeWidgetManagerProvider)
          .syncHabitsToWidget(activeHabits);

      // Invalidate list controller to trigger UI update across app
      ref.invalidate(habitListControllerProvider);

      return await repository.getHabitById(updatedHabit.id);
    });
  }

  /// Apply a streak freeze for a specific date
  Future<void> applyFreeze(String dateString, {String? reason}) async {
    final habitId = arg;
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repository = ref.read(habitRepositoryProvider);
      await repository.applyStreakFreeze(
        habitId: habitId,
        dateString: dateString,
        reason: reason,
      );

      ref.invalidate(habitListControllerProvider);
      return await repository.getHabitById(habitId);
    });
  }

  /// Delete habit permanently
  Future<void> deleteHabit() async {
    final habitId = arg;
    final repository = ref.read(habitRepositoryProvider);
    await repository.deleteHabit(habitId);

    // Cancel notification
    final notifService = ref.read(notificationServiceProvider);
    await notifService.cancelReminder(habitId.hashCode);

    // Refresh widget & list
    final activeHabits = await repository.getActiveHabits();
    await ref.read(homeWidgetManagerProvider).syncHabitsToWidget(activeHabits);
    ref.invalidate(habitListControllerProvider);
  }
}
