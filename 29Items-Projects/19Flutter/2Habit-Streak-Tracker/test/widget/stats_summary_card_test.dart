import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/features/analytics/domain/habit_stats.dart';
import 'package:habit_streak_tracker/features/analytics/presentation/widgets/stats_summary_card.dart';

void main() {
  testWidgets(
    'StatsSummaryCard renders streak, completions, and rate correctly',
    (tester) async {
      const stats = HabitStats(
        totalHabits: 5,
        totalCompletions: 42,
        activeStreakMax: 14,
        averageCompletionRate: 85.4,
        dailyHeatmapCounts: {'2026-09-06': 3},
      );

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(body: StatsSummaryCard(stats: stats)),
        ),
      );

      expect(find.text('14d'), findsOneWidget);
      expect(find.text('Best Streak'), findsOneWidget);
      expect(find.text('42'), findsOneWidget);
      expect(find.text('Check-ins'), findsOneWidget);
      expect(find.text('85%'), findsOneWidget);
      expect(find.text('30d Rate'), findsOneWidget);
      expect(find.text('5'), findsOneWidget);
      expect(find.text('Habits'), findsOneWidget);
    },
  );
}
