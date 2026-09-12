import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';

void main() {
  group('HabitFrequency Tests', () {
    test('isScheduledForWeekday accurately evaluates all frequency types', () {
      const daily = HabitFrequency(type: FrequencyType.daily);
      for (int i = 1; i <= 7; i++) {
        expect(daily.isScheduledForWeekday(i), isTrue);
      }

      const weekdays = HabitFrequency(type: FrequencyType.weekdaysOnly);
      expect(weekdays.isScheduledForWeekday(1), isTrue); // Mon
      expect(weekdays.isScheduledForWeekday(5), isTrue); // Fri
      expect(weekdays.isScheduledForWeekday(6), isFalse); // Sat
      expect(weekdays.isScheduledForWeekday(7), isFalse); // Sun

      const weekends = HabitFrequency(type: FrequencyType.weekendsOnly);
      expect(weekends.isScheduledForWeekday(1), isFalse);
      expect(weekends.isScheduledForWeekday(6), isTrue);
      expect(weekends.isScheduledForWeekday(7), isTrue);

      const specific = HabitFrequency(
        type: FrequencyType.specificDays,
        specificDays: [2, 4, 6],
      );
      expect(specific.isScheduledForWeekday(2), isTrue);
      expect(specific.isScheduledForWeekday(3), isFalse);
      expect(specific.isScheduledForWeekday(4), isTrue);
      expect(specific.isScheduledForWeekday(6), isTrue);
    });

    test('specificDaysString and parseSpecificDays handle edge cases', () {
      const freq = HabitFrequency(
        type: FrequencyType.specificDays,
        specificDays: [1, 3, 5],
      );
      expect(freq.specificDaysString, equals('1,3,5'));

      expect(HabitFrequency.parseSpecificDays('1,3,5'), equals([1, 3, 5]));
      expect(HabitFrequency.parseSpecificDays(null), isEmpty);
      expect(HabitFrequency.parseSpecificDays(''), isEmpty);
      expect(
        HabitFrequency.parseSpecificDays('1, invalid, 3'),
        equals([1, 1, 3]),
      );
    });
  });
}
