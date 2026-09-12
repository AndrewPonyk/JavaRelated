import 'package:flutter_test/flutter_test.dart';

import 'package:expense_tracker/core/utils/date_range.dart';
import 'package:expense_tracker/data/repositories/currency_repository.dart';
import 'package:expense_tracker/data/repositories/expense_repository.dart';
import 'package:expense_tracker/data/repositories/settings_repository.dart';
import 'package:expense_tracker/domain/services/analytics_service.dart';
import 'package:expense_tracker/domain/services/currency_service.dart';

import '../helpers/hive_test_helper.dart';

void main() {
  late HiveTestHarness harness;
  late ExpenseRepository expenses;
  late AnalyticsService service;
  late String foodId;
  late String transportId;

  setUp(() async {
    harness = await HiveTestHarness.open();
    expenses = ExpenseRepository(harness.hive);
    final settings = SettingsRepository(harness.hive);
    final currency = CurrencyService(CurrencyRepository(harness.hive), settings);
    service = AnalyticsService(expenses, settings, currency);

    foodId = (await settings.addCategory(name: 'Food')).valueOrNull!.id;
    transportId = (await settings.addCategory(name: 'Transport')).valueOrNull!.id;
  });

  tearDown(() => harness.dispose());

  Future<void> addExpense(String amount, String categoryId, DateTime date) async {
    await expenses.create(ExpenseDraft(
      amountInput: amount,
      currencyCode: 'USD',
      categoryId: categoryId,
      date: date,
    ));
  }

  group('byCategory', () {
    test('an empty box yields an empty breakdown', () {
      expect(service.byCategory(DateRange.month(DateTime(2026, 3))), isEmpty);
    });

    test('a single expense produces a single total', () async {
      await addExpense('10.00', foodId, DateTime(2026, 3, 5));
      final totals = service.byCategory(DateRange.month(DateTime(2026, 3)));
      expect(totals, hasLength(1));
      expect(totals.single.categoryId, foodId);
      expect(totals.single.total.decimalString, '10.00');
      expect(totals.single.count, 1);
    });

    test('sorted highest total first', () async {
      await addExpense('5.00', foodId, DateTime(2026, 3, 5));
      await addExpense('50.00', transportId, DateTime(2026, 3, 6));
      final totals = service.byCategory(DateRange.month(DateTime(2026, 3)));
      expect(totals.map((t) => t.categoryId), [transportId, foodId]);
    });
  });

  group('byWeek / week boundaries', () {
    test('weekStartsOn.monday buckets a Sunday with the previous week', () {
      // 2026-03-01 is a Sunday.
      final week = DateRange.week(DateTime(2026, 3, 1));
      expect(week.start.weekday, DateTime.monday);
      expect(week.contains(DateTime(2026, 3, 1)), isTrue);
    });

    test('weekStartsOn.sunday buckets the same Sunday as the start of its own week', () {
      final week = DateRange.week(DateTime(2026, 3, 1), weekStartsOn: DateTime.sunday);
      expect(week.start, DateTime(2026, 3, 1));
    });

    test('a month splits into weeks clipped to the month boundary', () {
      final month = DateRange.month(DateTime(2026, 3));
      final weeks = month.splitIntoWeeks();
      expect(weeks.first.start, month.start);
      expect(weeks.last.end, month.end);
      for (final week in weeks) {
        expect(week.start.isBefore(week.end), isTrue);
      }
    });

    test('byWeek totals match manual bucketing for a 5-partial-week month', () async {
      await addExpense('1.00', foodId, DateTime(2026, 3, 1));
      await addExpense('2.00', foodId, DateTime(2026, 3, 31));
      final month = DateRange.month(DateTime(2026, 3));
      final buckets = service.byWeek(month);
      final total = buckets.fold(0, (sum, b) => sum + b.total.minorUnits);
      expect(total, 300);
      expect(buckets.length, greaterThanOrEqualTo(4));
    });
  });

  group('byDay', () {
    test('one bucket per day of the range, totals sum to the whole', () async {
      await addExpense('3.00', foodId, DateTime(2026, 3, 10));
      final range = DateRange.day(DateTime(2026, 3, 10));
      final buckets = service.byDay(range);
      expect(buckets, hasLength(1));
      expect(buckets.single.total.decimalString, '3.00');
    });
  });

  group('byMonth', () {
    test('returns exact requested count of month buckets with correct totals', () async {
      await addExpense('15.00', foodId, DateTime(2026, 1, 10));
      await addExpense('25.00', transportId, DateTime(2026, 2, 10));
      await addExpense('35.00', foodId, DateTime(2026, 3, 10));

      final buckets = service.byMonth(anchorMonth: DateTime(2026, 3), count: 3);
      expect(buckets, hasLength(3));
      expect(buckets[0].total.decimalString, '15.00');
      expect(buckets[1].total.decimalString, '25.00');
      expect(buckets[2].total.decimalString, '35.00');
    });
  });

  group('totalFor', () {
    test('sums every expense in the range regardless of category', () async {
      await addExpense('10.00', foodId, DateTime(2026, 3, 2));
      await addExpense('20.00', transportId, DateTime(2026, 3, 3));
      final total = service.totalFor(DateRange.month(DateTime(2026, 3)));
      expect(total.decimalString, '30.00');
    });
  });

  group('calendar-arithmetic correctness (addMonthsClamped)', () {
    test('Jan 31 + 1 month clamps to the last day of February', () {
      expect(addMonthsClamped(DateTime(2026, 1, 31), 1), DateTime(2026, 2, 28));
    });

    test('Jan 31 + 1 month clamps to Feb 29 in a leap year', () {
      expect(addMonthsClamped(DateTime(2028, 1, 31), 1), DateTime(2028, 2, 29));
    });
  });
}
