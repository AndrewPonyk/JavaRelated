/// Calendar arithmetic. Deliberately avoids `Duration`-based date maths.
///
/// `DateTime.now().subtract(Duration(days: 30))` is NOT "last month", and
/// `Duration(days: 1)` is exactly 24h — which is wrong across a DST boundary.
/// Everything here constructs dates via `DateTime(y, m, d)` so month lengths and
/// DST transitions behave correctly. See docs/TECH-NOTES.md §3.6.
library;

/// Inclusive-start, exclusive-end half-open interval `[start, end)`.
class DateRange {
  const DateRange(this.start, this.end);

  final DateTime start;
  final DateTime end;

  /// Local-midnight-to-midnight range covering the whole month of [anchor].
  factory DateRange.month(DateTime anchor) => DateRange(
        DateTime(anchor.year, anchor.month),
        DateTime(anchor.year, anchor.month + 1),
      );

  /// Week containing [anchor]. [weekStartsOn] uses `DateTime.monday`..`sunday`.
  factory DateRange.week(DateTime anchor, {int weekStartsOn = DateTime.monday}) {
    final day = DateTime(anchor.year, anchor.month, anchor.day);
    final delta = (day.weekday - weekStartsOn + 7) % 7;
    final start = DateTime(day.year, day.month, day.day - delta);
    return DateRange(start, DateTime(start.year, start.month, start.day + 7));
  }

  factory DateRange.day(DateTime anchor) {
    final s = DateTime(anchor.year, anchor.month, anchor.day);
    return DateRange(s, DateTime(s.year, s.month, s.day + 1));
  }

  factory DateRange.year(DateTime anchor) =>
      DateRange(DateTime(anchor.year), DateTime(anchor.year + 1));

  bool contains(DateTime moment) =>
      !moment.isBefore(start) && moment.isBefore(end);

  /// Splits this range into consecutive weeks. A month typically yields 5
  /// partial buckets — the first and last are clipped to the month boundary.
  List<DateRange> splitIntoWeeks({int weekStartsOn = DateTime.monday}) {
    final buckets = <DateRange>[];
    var cursor = DateRange.week(start, weekStartsOn: weekStartsOn).start;
    while (cursor.isBefore(end)) {
      final next = DateTime(cursor.year, cursor.month, cursor.day + 7);
      buckets.add(DateRange(
        cursor.isBefore(start) ? start : cursor,
        next.isAfter(end) ? end : next,
      ));
      cursor = next;
    }
    return buckets;
  }

  /// Splits into individual days.
  List<DateRange> splitIntoDays() {
    final buckets = <DateRange>[];
    var cursor = DateTime(start.year, start.month, start.day);
    while (cursor.isBefore(end)) {
      final next = DateTime(cursor.year, cursor.month, cursor.day + 1);
      buckets.add(DateRange(cursor, next.isAfter(end) ? end : next));
      cursor = next;
    }
    return buckets;
  }

  @override
  bool operator ==(Object other) =>
      other is DateRange && other.start == start && other.end == end;

  @override
  int get hashCode => Object.hash(start, end);

  @override
  String toString() => 'DateRange(${start.toIso8601String()} .. ${end.toIso8601String()})';
}

/// Canonical first-of-month key used by budgets, so a budget for "March 2027"
/// has exactly one representation regardless of which date the user picked.
DateTime monthKey(DateTime anchor) => DateTime(anchor.year, anchor.month);

/// Local midnight — strips the time component while staying in local time.
DateTime dateOnly(DateTime moment) =>
    DateTime(moment.year, moment.month, moment.day);

/// Days in the month containing [anchor] (handles leap years).
int daysInMonth(DateTime anchor) =>
    DateTime(anchor.year, anchor.month + 1, 0).day;

/// Adds [months] calendar months, **clamping** the day to the target month's
/// length: Jan 31 + 1 month → Feb 28 (or 29 in a leap year).
///
/// This clamping decision is load-bearing for monthly recurring expenses. The
/// alternative (rolling over to Mar 3) produces duplicate or missing entries.
/// It is unit-tested in test/unit/recurring_service_test.dart.
DateTime addMonthsClamped(DateTime anchor, int months) {
  final targetYear = anchor.year + ((anchor.month - 1 + months) ~/ 12);
  final targetMonth = ((anchor.month - 1 + months) % 12) + 1;
  final lastDay = DateTime(targetYear, targetMonth + 1, 0).day;
  final day = anchor.day > lastDay ? lastDay : anchor.day;
  return DateTime(targetYear, targetMonth, day, anchor.hour, anchor.minute);
}
