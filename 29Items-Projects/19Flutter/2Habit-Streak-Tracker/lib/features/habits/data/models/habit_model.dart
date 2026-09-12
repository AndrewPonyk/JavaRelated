import '../../../../core/constants/db_constants.dart';
import '../../domain/models/habit.dart';
import '../../domain/models/habit_frequency.dart';

/// SQLite Data Transfer Model for Habit
class HabitModel {
  final String id;
  final String title;
  final String? description;
  final String frequencyType;
  final String? specificDays;
  final int targetCount;
  final String? reminderTime;
  final int colorValue;
  final String createdAt;
  final int isArchived;

  const HabitModel({
    required this.id,
    required this.title,
    this.description,
    required this.frequencyType,
    this.specificDays,
    required this.targetCount,
    this.reminderTime,
    required this.colorValue,
    required this.createdAt,
    required this.isArchived,
  });

  /// Convert SQLite Map to HabitModel
  factory HabitModel.fromMap(Map<String, dynamic> map) {
    return HabitModel(
      id: map[DbConstants.colHabitId] as String,
      title: map[DbConstants.colHabitTitle] as String,
      description: map[DbConstants.colHabitDescription] as String?,
      frequencyType: map[DbConstants.colHabitFrequencyType] as String,
      specificDays: map[DbConstants.colHabitSpecificDays] as String?,
      targetCount: map[DbConstants.colHabitTargetCount] as int? ?? 1,
      reminderTime: map[DbConstants.colHabitReminderTime] as String?,
      colorValue: map[DbConstants.colHabitColorValue] as int? ?? 0xFF6366F1,
      createdAt: map[DbConstants.colHabitCreatedAt] as String,
      isArchived: map[DbConstants.colHabitIsArchived] as int? ?? 0,
    );
  }

  /// Convert to SQLite Map
  Map<String, dynamic> toMap() {
    return {
      DbConstants.colHabitId: id,
      DbConstants.colHabitTitle: title,
      DbConstants.colHabitDescription: description,
      DbConstants.colHabitFrequencyType: frequencyType,
      DbConstants.colHabitSpecificDays: specificDays,
      DbConstants.colHabitTargetCount: targetCount,
      DbConstants.colHabitReminderTime: reminderTime,
      DbConstants.colHabitColorValue: colorValue,
      DbConstants.colHabitCreatedAt: createdAt,
      DbConstants.colHabitIsArchived: isArchived,
    };
  }

  /// Convert from Domain Entity
  factory HabitModel.fromDomain(Habit habit) {
    return HabitModel(
      id: habit.id,
      title: habit.title,
      description: habit.description,
      frequencyType: habit.frequency.type.name,
      specificDays: habit.frequency.specificDaysString,
      targetCount: habit.targetCount,
      reminderTime: habit.reminderTime,
      colorValue: habit.colorValue,
      createdAt: habit.createdAt.toIso8601String(),
      isArchived: habit.isArchived ? 1 : 0,
    );
  }

  /// Convert to Domain Entity
  Habit toDomain({
    int currentStreak = 0,
    int bestStreak = 0,
    bool isCompletedToday = false,
  }) {
    final freqType = FrequencyType.values.firstWhere(
      (e) => e.name == frequencyType,
      orElse: () => FrequencyType.daily,
    );

    return Habit(
      id: id,
      title: title,
      description: description,
      frequency: HabitFrequency(
        type: freqType,
        specificDays: HabitFrequency.parseSpecificDays(specificDays),
      ),
      targetCount: targetCount,
      reminderTime: reminderTime,
      colorValue: colorValue,
      createdAt: DateTime.parse(createdAt),
      isArchived: isArchived == 1,
      currentStreak: currentStreak,
      bestStreak: bestStreak,
      isCompletedToday: isCompletedToday,
    );
  }
}
