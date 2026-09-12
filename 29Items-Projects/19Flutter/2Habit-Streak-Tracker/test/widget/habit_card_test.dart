import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';
import 'package:habit_streak_tracker/features/habits/presentation/widgets/habit_card.dart';
import 'package:habit_streak_tracker/features/habits/presentation/widgets/habit_quick_check_in.dart';

void main() {
  testWidgets('HabitCard renders habit info and triggers callback on tap', (
    tester,
  ) async {
    bool toggled = false;

    final habit = Habit(
      id: 'test-1',
      title: 'Read 20 Pages',
      frequency: const HabitFrequency(type: FrequencyType.daily),
      createdAt: DateTime(2026, 9, 6),
      currentStreak: 5,
      isCompletedToday: false,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HabitCard(
            habit: habit,
            onToggle: () {
              toggled = true;
            },
          ),
        ),
      ),
    );

    // Verify Title and Streak are rendered
    expect(find.text('Read 20 Pages'), findsOneWidget);
    expect(find.text('5'), findsOneWidget);
    expect(find.text('Every day'), findsOneWidget);

    // Tap quick check-in
    await tester.tap(find.byType(HabitQuickCheckIn));
    await tester.pumpAndSettle();

    expect(toggled, isTrue);
  });
}
