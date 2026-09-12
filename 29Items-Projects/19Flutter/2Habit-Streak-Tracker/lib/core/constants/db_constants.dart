/// Database schema and table string constants
class DbConstants {
  static const String databaseName = 'habit_streak_tracker.db';
  static const int databaseVersion = 2;

  // Table: habits
  static const String tableHabits = 'habits';
  static const String colHabitId = 'id';
  static const String colHabitTitle = 'title';
  static const String colHabitDescription = 'description';
  static const String colHabitFrequencyType = 'frequency_type';
  static const String colHabitSpecificDays =
      'specific_days'; // e.g. "1,2,3,4,5"
  static const String colHabitTargetCount = 'target_count';
  static const String colHabitReminderTime = 'reminder_time'; // e.g. "08:30"
  static const String colHabitColorValue = 'color_value';
  static const String colHabitCreatedAt = 'created_at';
  static const String colHabitIsArchived = 'is_archived';

  // Table: habit_completions
  static const String tableCompletions = 'habit_completions';
  static const String colCompletionId = 'id';
  static const String colCompletionHabitId = 'habit_id';
  static const String colCompletionDate = 'completed_date'; // "YYYY-MM-DD"
  static const String colCompletionCount = 'count';
  static const String colCompletionCreatedAt = 'created_at';

  // Table: streak_freezes
  static const String tableStreakFreezes = 'streak_freezes';
  static const String colFreezeId = 'id';
  static const String colFreezeHabitId = 'habit_id';
  static const String colFreezeDate = 'freeze_date'; // "YYYY-MM-DD"
  static const String colFreezeReason = 'reason';
  static const String colFreezeCreatedAt = 'created_at';
}
