import 'habit_frequency.dart';

/// Core domain entity representing a user habit
class Habit {
  final String id;
  final String title;
  final String? description;
  final HabitFrequency frequency;
  final int targetCount;
  final String? reminderTime; // e.g. "08:30"
  final int colorValue;
  final DateTime createdAt;
  final bool isArchived;

  // Derived / Calculated presentation fields
  final int currentStreak;
  final int bestStreak;
  final bool isCompletedToday;

  const Habit({
    required this.id,
    required this.title,
    this.description,
    required this.frequency,
    this.targetCount = 1,
    this.reminderTime,
    this.colorValue = 0xFF6366F1,
    required this.createdAt,
    this.isArchived = false,
    this.currentStreak = 0,
    this.bestStreak = 0,
    this.isCompletedToday = false,
  });

  Habit copyWith({
    String? id,
    String? title,
    String? description,
    HabitFrequency? frequency,
    int? targetCount,
    String? reminderTime,
    int? colorValue,
    DateTime? createdAt,
    bool? isArchived,
    int? currentStreak,
    int? bestStreak,
    bool? isCompletedToday,
  }) {
    return Habit(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      frequency: frequency ?? this.frequency,
      targetCount: targetCount ?? this.targetCount,
      reminderTime: reminderTime ?? this.reminderTime,
      colorValue: colorValue ?? this.colorValue,
      createdAt: createdAt ?? this.createdAt,
      isArchived: isArchived ?? this.isArchived,
      currentStreak: currentStreak ?? this.currentStreak,
      bestStreak: bestStreak ?? this.bestStreak,
      isCompletedToday: isCompletedToday ?? this.isCompletedToday,
    );
  }
}
