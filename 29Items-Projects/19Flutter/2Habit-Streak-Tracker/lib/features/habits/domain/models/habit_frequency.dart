/// Habit scheduling frequency configuration
enum FrequencyType { daily, weekdaysOnly, weekendsOnly, specificDays }

class HabitFrequency {
  final FrequencyType type;

  /// List of ISO weekday numbers: 1 (Monday) through 7 (Sunday)
  final List<int> specificDays;

  const HabitFrequency({required this.type, this.specificDays = const []});

  /// True if the habit is scheduled for the given weekday (1..7)
  bool isScheduledForWeekday(int weekday) {
    switch (type) {
      case FrequencyType.daily:
        return true;
      case FrequencyType.weekdaysOnly:
        return weekday >= 1 && weekday <= 5;
      case FrequencyType.weekendsOnly:
        return weekday == 6 || weekday == 7;
      case FrequencyType.specificDays:
        return specificDays.contains(weekday);
    }
  }

  /// Serialize specific days to comma-delimited string for SQLite
  String get specificDaysString => specificDays.join(',');

  /// Parse from string stored in DB
  static List<int> parseSpecificDays(String? str) {
    if (str == null || str.isEmpty) return const [];
    return str.split(',').map((e) => int.tryParse(e.trim()) ?? 1).toList();
  }
}
