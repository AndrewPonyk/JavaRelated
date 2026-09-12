import 'dart:convert';
import '../habits/domain/models/habit.dart';

/// Lightweight payload serialized for native home screen widgets
class HomeWidgetHabitItem {
  final String id;
  final String title;
  final int streak;
  final bool isCompleted;

  const HomeWidgetHabitItem({
    required this.id,
    required this.title,
    required this.streak,
    required this.isCompleted,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'streak': streak,
      'isCompleted': isCompleted,
    };
  }

  factory HomeWidgetHabitItem.fromHabit(Habit habit) {
    return HomeWidgetHabitItem(
      id: habit.id,
      title: habit.title,
      streak: habit.currentStreak,
      isCompleted: habit.isCompletedToday,
    );
  }
}

class HomeWidgetPayload {
  final String lastUpdated;
  final int completedCount;
  final int totalCount;
  final List<HomeWidgetHabitItem> habits;

  const HomeWidgetPayload({
    required this.lastUpdated,
    required this.completedCount,
    required this.totalCount,
    required this.habits,
  });

  Map<String, dynamic> toMap() {
    return {
      'lastUpdated': lastUpdated,
      'completedCount': completedCount,
      'totalCount': totalCount,
      'habits': habits.map((h) => h.toMap()).toList(),
    };
  }

  String toJson() => jsonEncode(toMap());
}
