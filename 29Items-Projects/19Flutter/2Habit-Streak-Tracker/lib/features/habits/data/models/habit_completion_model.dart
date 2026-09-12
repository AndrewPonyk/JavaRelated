import '../../../../core/constants/db_constants.dart';
import '../../domain/models/habit_completion.dart';

/// SQLite Data Transfer Model for Habit Completion
class HabitCompletionModel {
  final String id;
  final String habitId;
  final String completedDate;
  final int count;
  final String createdAt;

  const HabitCompletionModel({
    required this.id,
    required this.habitId,
    required this.completedDate,
    required this.count,
    required this.createdAt,
  });

  factory HabitCompletionModel.fromMap(Map<String, dynamic> map) {
    return HabitCompletionModel(
      id: map[DbConstants.colCompletionId] as String,
      habitId: map[DbConstants.colCompletionHabitId] as String,
      completedDate: map[DbConstants.colCompletionDate] as String,
      count: map[DbConstants.colCompletionCount] as int? ?? 1,
      createdAt: map[DbConstants.colCompletionCreatedAt] as String,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      DbConstants.colCompletionId: id,
      DbConstants.colCompletionHabitId: habitId,
      DbConstants.colCompletionDate: completedDate,
      DbConstants.colCompletionCount: count,
      DbConstants.colCompletionCreatedAt: createdAt,
    };
  }

  HabitCompletion toDomain() {
    return HabitCompletion(
      id: id,
      habitId: habitId,
      completedDate: completedDate,
      count: count,
      createdAt: DateTime.parse(createdAt),
    );
  }
}
