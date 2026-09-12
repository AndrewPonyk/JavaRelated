import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/utils/date_time_utils.dart';
import '../../../habits/presentation/providers/habit_providers.dart';
import '../../domain/habit_stats.dart';

/// Controller providing calculated statistics and calendar heatmap dataset
final statsControllerProvider =
    AsyncNotifierProvider<StatsController, HabitStats>(() {
      return StatsController();
    });

class StatsController extends AsyncNotifier<HabitStats> {
  @override
  Future<HabitStats> build() async {
    final habitRepo = ref.watch(habitRepositoryProvider);
    final habits = await habitRepo.getActiveHabits();

    if (habits.isEmpty) {
      return HabitStats.empty();
    }

    final today = DateTimeUtils.today();
    final startDate = today.subtract(const Duration(days: 90));

    final completions = await habitRepo.getCompletionsForRange(
      startDate: DateTimeUtils.toDateString(startDate),
      endDate: DateTimeUtils.toDateString(today),
    );

    final Map<String, int> heatmap = {};
    for (final c in completions) {
      heatmap[c.completedDate] = (heatmap[c.completedDate] ?? 0) + 1;
    }

    int totalCompletions = completions.length;
    int maxStreak = 0;
    for (final h in habits) {
      if (h.currentStreak > maxStreak) {
        maxStreak = h.currentStreak;
      }
    }

    // Average 30-day completion rate across all active habits
    double totalRate = 0.0;
    for (final h in habits) {
      final completedDates = await habitRepo.getCompletedDates(h.id);
      final rate = _calculateHabitRate(
        completedDates: completedDates,
        scheduledWeekdays: h.frequency.specificDays,
        days: 30,
        today: today,
      );
      totalRate += rate;
    }

    final avgRate = habits.isEmpty ? 0.0 : totalRate / habits.length;

    return HabitStats(
      totalHabits: habits.length,
      totalCompletions: totalCompletions,
      activeStreakMax: maxStreak,
      averageCompletionRate: avgRate,
      dailyHeatmapCounts: heatmap,
    );
  }

  double _calculateHabitRate({
    required Set<String> completedDates,
    required List<int> scheduledWeekdays,
    required int days,
    required DateTime today,
  }) {
    int scheduled = 0;
    int completed = 0;

    for (int i = 0; i < days; i++) {
      final day = today.subtract(Duration(days: i));
      final isScheduled =
          scheduledWeekdays.isEmpty || scheduledWeekdays.contains(day.weekday);

      if (isScheduled) {
        scheduled++;
        if (completedDates.contains(DateTimeUtils.toDateString(day))) {
          completed++;
        }
      }
    }

    if (scheduled == 0) return 0.0;
    return (completed / scheduled) * 100.0;
  }
}
