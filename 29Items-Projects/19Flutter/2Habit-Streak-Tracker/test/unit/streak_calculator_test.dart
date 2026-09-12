import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/core/utils/streak_calculator.dart';

void main() {
  group('StreakCalculator Tests', () {
    final anchorToday = DateTime(2026, 9, 6); // Sunday

    test('Consecutive daily completions calculate correct ongoing streak', () {
      final completedDates = {
        '2026-09-06', // Today
        '2026-09-05', // Yesterday
        '2026-09-04', // 2 days ago
        '2026-09-03', // 3 days ago
      };

      final streak = StreakCalculator.calculateCurrentStreak(
        completedDates: completedDates,
        referenceDate: anchorToday,
      );

      expect(streak, equals(4));
    });

    test(
      'Missed yesterday without freeze breaks streak to 0 or 1 if done today',
      () {
        final completedDates = {
          '2026-09-06', // Today done
          // '2026-09-05' missed!
          '2026-09-04',
          '2026-09-03',
        };

        final streak = StreakCalculator.calculateCurrentStreak(
          completedDates: completedDates,
          referenceDate: anchorToday,
        );

        expect(streak, equals(1)); // Only today
      },
    );

    test('Freeze on missed day protects previous streak', () {
      final completedDates = {
        '2026-09-06', // Today
        // '2026-09-05' missed, but protected by freeze!
        '2026-09-04',
        '2026-09-03',
      };
      final freezeDates = {'2026-09-05'};

      final streak = StreakCalculator.calculateCurrentStreak(
        completedDates: completedDates,
        freezeDates: freezeDates,
        referenceDate: anchorToday,
      );

      // 3 completed days protected across the freeze
      expect(streak, equals(3));
    });

    test('Today not completed yet does not break yesterday streak', () {
      final completedDates = {
        // '2026-09-06' not done yet today
        '2026-09-05',
        '2026-09-04',
        '2026-09-03',
      };

      final streak = StreakCalculator.calculateCurrentStreak(
        completedDates: completedDates,
        referenceDate: anchorToday,
      );

      expect(streak, equals(3));
    });

    test('Weekdays-only habit skips weekends without breaking streak', () {
      // 2026-09-07 is Monday, 2026-09-08 is Tuesday
      // 2026-09-05 is Saturday, 2026-09-06 is Sunday
      // 2026-09-04 is Friday
      final mondayAnchor = DateTime(2026, 9, 7);
      final completedDates = {
        '2026-09-07', // Monday
        // Weekend 05, 06 unscheduled
        '2026-09-04', // Friday
        '2026-09-03', // Thursday
      };

      final streak = StreakCalculator.calculateCurrentStreak(
        completedDates: completedDates,
        scheduledWeekdays: [1, 2, 3, 4, 5], // Mon-Fri
        referenceDate: mondayAnchor,
      );

      expect(streak, equals(3));
    });

    test('Best streak calculation detects highest historical run', () {
      final completedDates = {
        '2026-08-01',
        '2026-08-02',
        '2026-08-03',
        '2026-08-04',
        '2026-08-05', // 5-day streak
        // gap
        '2026-08-10',
        '2026-08-11', // 2-day streak
      };

      final best = StreakCalculator.calculateBestStreak(
        completedDates: completedDates,
      );

      expect(best, equals(5));
    });

    test('Completion rate computes accurately over time window', () {
      final completedDates = {
        '2026-09-06',
        '2026-09-05',
        '2026-09-04',
        '2026-09-03',
        '2026-09-02',
      };

      final rate = StreakCalculator.calculateCompletionRate(
        completedDates: completedDates,
        windowDays: 10,
        referenceDate: anchorToday,
      );

      // 5 out of 10 days = 50%
      expect(rate, equals(50.0));
    });

    test(
      'Edge Case: Leap year date transition calculates contiguous streak',
      () {
        // 2024 is a leap year (Feb 28, Feb 29, March 1)
        final march1 = DateTime(2024, 3, 1);
        final completedDates = {'2024-03-01', '2024-02-29', '2024-02-28'};

        final streak = StreakCalculator.calculateCurrentStreak(
          completedDates: completedDates,
          referenceDate: march1,
        );

        expect(streak, equals(3));
      },
    );

    test('Edge Case: New Year boundary crossover preserves streak', () {
      final jan2 = DateTime(2025, 1, 2);
      final completedDates = {
        '2025-01-02',
        '2025-01-01',
        '2024-12-31',
        '2024-12-30',
      };

      final streak = StreakCalculator.calculateCurrentStreak(
        completedDates: completedDates,
        referenceDate: jan2,
      );

      expect(streak, equals(4));
    });

    test('Edge Case: Multiple consecutive streak freezes protect streak', () {
      final friday = DateTime(2026, 9, 11);
      final completedDates = {
        '2026-09-11', // Friday done
        // Tue, Wed, Thu sick days
        '2026-09-07', // Monday done
      };
      final freezeDates = {
        '2026-09-10', // Thursday freeze
        '2026-09-09', // Wednesday freeze
        '2026-09-08', // Tuesday freeze
      };

      final streak = StreakCalculator.calculateCurrentStreak(
        completedDates: completedDates,
        freezeDates: freezeDates,
        referenceDate: friday,
      );

      // Monday and Friday (2 completed days) bridged across 3 consecutive freezes
      expect(streak, equals(2));
    });

    test(
      'Edge Case: Empty history returns 0 for streak and 0.0% completion rate',
      () {
        final currentStreak = StreakCalculator.calculateCurrentStreak(
          completedDates: {},
          referenceDate: anchorToday,
        );
        final bestStreak = StreakCalculator.calculateBestStreak(
          completedDates: {},
        );
        final rate = StreakCalculator.calculateCompletionRate(
          completedDates: {},
          windowDays: 30,
          referenceDate: anchorToday,
        );

        expect(currentStreak, equals(0));
        expect(bestStreak, equals(0));
        expect(rate, equals(0.0));
      },
    );
  });
}
