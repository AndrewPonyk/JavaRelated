import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/core/utils/date_time_utils.dart';

void main() {
  group('DateTimeUtils Tests', () {
    test('toDateString formats accurately', () {
      final dt = DateTime(2026, 9, 6);
      expect(DateTimeUtils.toDateString(dt), equals('2026-09-06'));
    });

    test('parseDateString parses valid string and throws on malformed', () {
      final parsed = DateTimeUtils.parseDateString('2026-09-06');
      expect(parsed.year, equals(2026));
      expect(parsed.month, equals(9));
      expect(parsed.day, equals(6));

      expect(
        () => DateTimeUtils.parseDateString('invalid-date-string'),
        throwsFormatException,
      );
    });

    test('normalizeDate strips time components', () {
      final dt = DateTime(2026, 9, 6, 15, 30, 45, 123);
      final normalized = DateTimeUtils.normalizeDate(dt);
      expect(normalized.hour, equals(0));
      expect(normalized.minute, equals(0));
      expect(normalized.second, equals(0));
      expect(normalized.millisecond, equals(0));
    });

    test('isSameDay correctly identifies identical and distinct days', () {
      final a = DateTime(2026, 9, 6, 8, 0);
      final b = DateTime(2026, 9, 6, 22, 30);
      final c = DateTime(2026, 9, 7, 8, 0);

      expect(DateTimeUtils.isSameDay(a, b), isTrue);
      expect(DateTimeUtils.isSameDay(a, c), isFalse);
    });

    test('parseTimeString handles valid, null, and malformed strings', () {
      expect(DateTimeUtils.parseTimeString('08:30'), equals((8, 30)));
      expect(DateTimeUtils.parseTimeString('23:59'), equals((23, 59)));
      expect(DateTimeUtils.parseTimeString(null), isNull);
      expect(DateTimeUtils.parseTimeString('invalid'), isNull);
      expect(DateTimeUtils.parseTimeString('aa:bb'), isNull);
    });
  });
}
