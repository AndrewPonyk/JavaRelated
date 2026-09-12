import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/core/utils/date_time_utils.dart';
import 'package:habit_streak_tracker/features/analytics/presentation/controllers/stats_controller.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_completion.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';
import 'package:habit_streak_tracker/features/habits/domain/repositories/habit_repository.dart';
import 'package:habit_streak_tracker/features/habits/presentation/providers/habit_providers.dart';
import 'package:mocktail/mocktail.dart';

class MockHabitRepository extends Mock implements HabitRepository {}

void main() {
  group('StatsController Tests', () {
    late MockHabitRepository mockRepo;

    setUp(() {
      mockRepo = MockHabitRepository();
    });

    test('build returns empty stats when no active habits exist', () async {
      when(() => mockRepo.getActiveHabits()).thenAnswer((_) async => []);

      final container = ProviderContainer(
        overrides: [habitRepositoryProvider.overrideWithValue(mockRepo)],
      );
      addTearDown(container.dispose);

      final stats = await container.read(statsControllerProvider.future);
      expect(stats.totalHabits, equals(0));
      expect(stats.totalCompletions, equals(0));
      expect(stats.activeStreakMax, equals(0));
      expect(stats.averageCompletionRate, equals(0.0));
      expect(stats.dailyHeatmapCounts, isEmpty);
    });

    test(
      'build computes heatmap counts, rate, and max streak for active habits',
      () async {
        final todayStr = DateTimeUtils.todayString();
        final habit1 = Habit(
          id: 'h-s-1',
          title: 'Exercise',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime.now(),
          currentStreak: 5,
        );
        final habit2 = Habit(
          id: 'h-s-2',
          title: 'Read',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime.now(),
          currentStreak: 12,
        );

        final completions = [
          HabitCompletion(
            id: 'c1',
            habitId: 'h-s-1',
            completedDate: todayStr,
            createdAt: DateTime.now(),
          ),
          HabitCompletion(
            id: 'c2',
            habitId: 'h-s-2',
            completedDate: todayStr,
            createdAt: DateTime.now(),
          ),
        ];

        when(
          () => mockRepo.getActiveHabits(),
        ).thenAnswer((_) async => [habit1, habit2]);
        when(
          () => mockRepo.getCompletionsForRange(
            startDate: any(named: 'startDate'),
            endDate: any(named: 'endDate'),
          ),
        ).thenAnswer((_) async => completions);

        when(
          () => mockRepo.getCompletedDates('h-s-1'),
        ).thenAnswer((_) async => {todayStr});
        when(
          () => mockRepo.getCompletedDates('h-s-2'),
        ).thenAnswer((_) async => {todayStr});

        final container = ProviderContainer(
          overrides: [habitRepositoryProvider.overrideWithValue(mockRepo)],
        );
        addTearDown(container.dispose);

        final stats = await container.read(statsControllerProvider.future);
        expect(stats.totalHabits, equals(2));
        expect(stats.totalCompletions, equals(2));
        expect(stats.activeStreakMax, equals(12));
        expect(stats.dailyHeatmapCounts[todayStr], equals(2));
        expect(stats.averageCompletionRate, isPositive);
      },
    );
  });
}
