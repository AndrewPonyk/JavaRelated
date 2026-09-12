/// Aggregate statistics for user habit routines
class HabitStats {
  final int totalHabits;
  final int totalCompletions;
  final int activeStreakMax;
  final double averageCompletionRate; // Percentage 0.0 - 100.0
  final Map<String, int> dailyHeatmapCounts; // "YYYY-MM-DD" -> count

  const HabitStats({
    required this.totalHabits,
    required this.totalCompletions,
    required this.activeStreakMax,
    required this.averageCompletionRate,
    required this.dailyHeatmapCounts,
  });

  factory HabitStats.empty() {
    return const HabitStats(
      totalHabits: 0,
      totalCompletions: 0,
      activeStreakMax: 0,
      averageCompletionRate: 0.0,
      dailyHeatmapCounts: {},
    );
  }
}
