import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/features/habits/data/models/habit_completion_model.dart';

void main() {
  group('HabitCompletionModel Tests', () {
    test('serializes and deserializes from SQLite map correctly', () {
      final now = DateTime(2026, 9, 6, 10, 0, 0);
      const model = HabitCompletionModel(
        id: 'c-1',
        habitId: 'h-1',
        completedDate: '2026-09-06',
        count: 2,
        createdAt: '2026-09-06T10:00:00.000',
      );

      final map = model.toMap();
      expect(map['id'], equals('c-1'));
      expect(map['habit_id'], equals('h-1'));
      expect(map['completed_date'], equals('2026-09-06'));
      expect(map['count'], equals(2));

      final fromMap = HabitCompletionModel.fromMap(map);
      expect(fromMap.id, equals(model.id));
      expect(fromMap.habitId, equals(model.habitId));
      expect(fromMap.completedDate, equals(model.completedDate));
      expect(fromMap.count, equals(model.count));

      final domain = fromMap.toDomain();
      expect(domain.id, equals(model.id));
      expect(domain.habitId, equals(model.habitId));
      expect(domain.completedDate, equals('2026-09-06'));
      expect(domain.count, equals(2));
      expect(domain.createdAt.year, equals(now.year));
    });
  });
}
