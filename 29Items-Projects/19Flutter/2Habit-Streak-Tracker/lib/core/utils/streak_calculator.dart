import 'date_time_utils.dart';

/// Pure algorithmic engine for calculating active streaks, best streaks, and freeze impacts
class StreakCalculator {
  /// Calculates current ongoing streak.
  ///
  /// [completedDates]: Set of 'YYYY-MM-DD' dates when the habit was completed.
  /// [freezeDates]: Set of 'YYYY-MM-DD' dates where a freeze was active.
  /// [scheduledWeekdays]: List of 1 (Mon) to 7 (Sun) when habit is scheduled. If empty, all days count.
  /// [referenceDate]: The anchor date (defaults to today).
  static int calculateCurrentStreak({
    required Set<String> completedDates,
    Set<String> freezeDates = const {},
    List<int> scheduledWeekdays = const [],
    DateTime? referenceDate,
  }) {
    final anchor = referenceDate ?? DateTimeUtils.today();
    final anchorNormalized = DateTimeUtils.normalizeDate(anchor);

    int streak = 0;
    DateTime currentCheck = anchorNormalized;

    final String anchorStr = DateTimeUtils.toDateString(anchorNormalized);
    final bool isScheduledToday = _isScheduledDay(
      anchorNormalized,
      scheduledWeekdays,
    );
    final bool completedToday = completedDates.contains(anchorStr);

    // If scheduled today and already completed, count today and check backwards from yesterday
    if (isScheduledToday && completedToday) {
      streak++;
      currentCheck = currentCheck.subtract(const Duration(days: 1));
    } else if (isScheduledToday && !completedToday) {
      // If not completed today yet, check if yesterday was completed or frozen (today is still pending)
      currentCheck = currentCheck.subtract(const Duration(days: 1));
    } else {
      // Today was not a scheduled day, start checking backwards from yesterday
      currentCheck = currentCheck.subtract(const Duration(days: 1));
    }

    // Traverse backwards day by day
    while (true) {
      final dateStr = DateTimeUtils.toDateString(currentCheck);
      final bool isScheduled = _isScheduledDay(currentCheck, scheduledWeekdays);

      if (!isScheduled) {
        // Not scheduled -> skip day without breaking or incrementing streak
        currentCheck = currentCheck.subtract(const Duration(days: 1));
        continue;
      }

      if (completedDates.contains(dateStr)) {
        streak++;
        currentCheck = currentCheck.subtract(const Duration(days: 1));
      } else if (freezeDates.contains(dateStr)) {
        // Freeze preserves streak without adding to streak count
        currentCheck = currentCheck.subtract(const Duration(days: 1));
      } else {
        // Day missed without freeze -> streak broken
        break;
      }
    }

    return streak;
  }

  /// Calculates the maximum (best) historical streak
  static int calculateBestStreak({
    required Set<String> completedDates,
    Set<String> freezeDates = const {},
    List<int> scheduledWeekdays = const [],
  }) {
    if (completedDates.isEmpty) return 0;

    // Convert string dates to sorted DateTime objects
    final sortedDates =
        completedDates.map((d) => DateTimeUtils.parseDateString(d)).toList()
          ..sort((a, b) => a.compareTo(b));

    int bestStreak = 0;
    int currentStreak = 0;
    DateTime? previousDate;

    final firstDate = sortedDates.first;
    final lastDate = sortedDates.last;

    DateTime cursor = firstDate;
    while (!cursor.isAfter(lastDate)) {
      final cursorStr = DateTimeUtils.toDateString(cursor);
      final isScheduled = _isScheduledDay(cursor, scheduledWeekdays);

      if (isScheduled) {
        if (completedDates.contains(cursorStr)) {
          currentStreak++;
          if (currentStreak > bestStreak) {
            bestStreak = currentStreak;
          }
        } else if (freezeDates.contains(cursorStr)) {
          // Freeze protects streak; does not increment nor reset
        } else {
          currentStreak = 0;
        }
      }

      cursor = cursor.add(const Duration(days: 1));
      previousDate = cursor;
    }

    // Suppress unused variable warning if any
    assert(previousDate != null);

    return bestStreak;
  }

  /// Calculates completion rate (percentage 0.0 - 100.0) within a past days window
  static double calculateCompletionRate({
    required Set<String> completedDates,
    required int windowDays,
    List<int> scheduledWeekdays = const [],
    DateTime? referenceDate,
  }) {
    if (windowDays <= 0) return 0.0;
    final anchor = referenceDate ?? DateTimeUtils.today();

    int totalScheduledDays = 0;
    int totalCompletedDays = 0;

    for (int i = 0; i < windowDays; i++) {
      final day = anchor.subtract(Duration(days: i));
      final dateStr = DateTimeUtils.toDateString(day);

      if (_isScheduledDay(day, scheduledWeekdays)) {
        totalScheduledDays++;
        if (completedDates.contains(dateStr)) {
          totalCompletedDays++;
        }
      }
    }

    if (totalScheduledDays == 0) return 0.0;
    return (totalCompletedDays / totalScheduledDays) * 100.0;
  }

  static bool _isScheduledDay(DateTime date, List<int> scheduledWeekdays) {
    if (scheduledWeekdays.isEmpty) return true; // Daily if unspecified
    return scheduledWeekdays.contains(date.weekday);
  }
}
