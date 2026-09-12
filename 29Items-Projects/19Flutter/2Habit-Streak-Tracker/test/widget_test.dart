import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/app.dart';
import 'package:habit_streak_tracker/features/analytics/domain/habit_stats.dart';
import 'package:habit_streak_tracker/features/analytics/presentation/controllers/stats_controller.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';
import 'package:habit_streak_tracker/features/habits/presentation/controllers/habit_list_controller.dart';
import 'package:habit_streak_tracker/features/habits/presentation/providers/habit_providers.dart';

class FakeHabitListController extends HabitListController {
  @override
  Future<List<Habit>> build() async {
    return [
      Habit(
        id: 'smoke-1',
        title: 'Morning Run',
        frequency: const HabitFrequency(type: FrequencyType.daily),
        createdAt: DateTime(2026, 9, 6),
        currentStreak: 2,
        isCompletedToday: false,
      ),
    ];
  }
}

class FakeStatsController extends StatsController {
  @override
  Future<HabitStats> build() async {
    return const HabitStats(
      totalHabits: 1,
      totalCompletions: 2,
      activeStreakMax: 2,
      averageCompletionRate: 100.0,
      dailyHeatmapCounts: {},
    );
  }
}

void main() {
  testWidgets('HabitTrackerApp loads smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          habitListControllerProvider.overrideWith(
            () => FakeHabitListController(),
          ),
          statsControllerProvider.overrideWith(() => FakeStatsController()),
        ],
        child: const HabitTrackerApp(),
      ),
    );

    await tester.pumpAndSettle();

    // Verify header and habit card appear
    expect(find.text('Habit Streaks'), findsOneWidget);
    expect(find.text('Morning Run'), findsOneWidget);
    expect(find.text('New Habit'), findsOneWidget);
  });
}
