/// Application-wide constants
class AppConstants {
  static const String appName = 'Habit Streak Tracker';
  static const String appVersion = '1.0.0';

  // HomeWidget Constants
  static const String appGroupId = 'group.com.example.habitstreaktracker';
  static const String androidWidgetName = 'HabitAppWidgetProvider';
  static const String iosWidgetKind = 'HabitStreakWidget';
  static const String widgetDataKey = 'habit_widget_payload';

  // Notification Constants
  static const String reminderChannelId = 'habit_daily_reminders';
  static const String reminderChannelName = 'Habit Reminders';
  static const String reminderChannelDescription =
      'Customizable reminders to complete your daily habits';

  // Default Settings
  static const int defaultMaxFreezesPerMonth = 3;
}
