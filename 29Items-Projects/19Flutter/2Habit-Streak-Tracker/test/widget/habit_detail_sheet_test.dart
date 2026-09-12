import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';
import 'package:habit_streak_tracker/features/habits/presentation/widgets/habit_detail_sheet.dart';

void main() {
  testWidgets('HabitDetailSheet renders title and freeze action', (
    tester,
  ) async {
    final habit = Habit(
      id: 'h-detail-1',
      title: 'Meditation',
      description: 'Morning peace',
      frequency: const HabitFrequency(type: FrequencyType.daily),
      createdAt: DateTime(2026, 9, 6),
      reminderTime: '08:00',
    );

    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          home: Scaffold(body: HabitDetailSheet(habit: habit)),
        ),
      ),
    );

    expect(find.text('Habit Details & Settings'), findsOneWidget);
    expect(find.text('Meditation'), findsOneWidget);
    expect(find.text('Morning peace'), findsOneWidget);
    expect(find.text('08:00'), findsOneWidget);
    expect(find.text('Missed a day? Freeze Streak'), findsOneWidget);
    expect(find.text('Freeze'), findsOneWidget);
    expect(find.text('Save Changes'), findsOneWidget);
  });
}
