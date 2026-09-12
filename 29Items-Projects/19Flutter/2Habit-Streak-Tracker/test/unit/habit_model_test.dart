import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/features/habits/data/models/habit_model.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';

void main() {
  group('HabitModel & Domain Entity Tests', () {
    test('HabitModel serializes to and from Map correctly', () {
      final now = DateTime(2026, 9, 6, 12, 0, 0);
      final habit = Habit(
        id: 'habit-123',
        title: 'Morning Meditation',
        description: '15 minutes mindfulness',
        frequency: const HabitFrequency(
          type: FrequencyType.specificDays,
          specificDays: [1, 3, 5],
        ),
        targetCount: 1,
        reminderTime: '07:30',
        colorValue: 0xFF10B981,
        createdAt: now,
      );

      final model = HabitModel.fromDomain(habit);
      final map = model.toMap();

      expect(map['id'], equals('habit-123'));
      expect(map['title'], equals('Morning Meditation'));
      expect(map['frequency_type'], equals('specificDays'));
      expect(map['specific_days'], equals('1,3,5'));
      expect(map['reminder_time'], equals('07:30'));

      final restoredModel = HabitModel.fromMap(map);
      final restoredDomain = restoredModel.toDomain();

      expect(restoredDomain.id, equals(habit.id));
      expect(restoredDomain.title, equals(habit.title));
      expect(restoredDomain.frequency.type, equals(FrequencyType.specificDays));
      expect(restoredDomain.frequency.specificDays, equals([1, 3, 5]));
    });

    test('copyWith properly updates selective attributes', () {
      final habit = Habit(
        id: 'h1',
        title: 'Workout',
        frequency: const HabitFrequency(type: FrequencyType.daily),
        createdAt: DateTime.now(),
        currentStreak: 2,
        isCompletedToday: false,
      );

      final updated = habit.copyWith(currentStreak: 3, isCompletedToday: true);

      expect(updated.id, equals('h1'));
      expect(updated.currentStreak, equals(3));
      expect(updated.isCompletedToday, isTrue);
    });
  });
}
