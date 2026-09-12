import 'package:flutter_test/flutter_test.dart';

import 'package:expense_tracker/core/constants/app_constants.dart';
import 'package:expense_tracker/data/models/recurring_expense.dart';
import 'package:expense_tracker/data/repositories/expense_repository.dart';
import 'package:expense_tracker/data/repositories/recurring_repository.dart';
import 'package:expense_tracker/data/repositories/settings_repository.dart';
import 'package:expense_tracker/domain/services/recurring_service.dart';

import '../helpers/hive_test_helper.dart';

void main() {
  late HiveTestHarness harness;
  late RecurringRepository recurring;
  late ExpenseRepository expenses;
  late RecurringService service;
  late String categoryId;

  setUp(() async {
    harness = await HiveTestHarness.open();
    recurring = RecurringRepository(harness.hive);
    expenses = ExpenseRepository(harness.hive);
    service = RecurringService(recurring, expenses);

    final settings = SettingsRepository(harness.hive);
    categoryId = (await settings.addCategory(name: 'Food')).valueOrNull!.id;
  });

  tearDown(() => harness.dispose());

  test('a single daily recurrence generates exactly one expense per elapsed day', () async {
    await recurring.create(
      amountInput: '5.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.daily,
      startDate: DateTime(2026, 3, 1),
    );

    final result = await service.materialiseDue(asOf: DateTime(2026, 3, 3));
    expect(result.valueOrNull, 3);
    expect(expenses.all(), hasLength(3));
  });

  test('running materialiseDue twice is idempotent — no duplicate expenses', () async {
    await recurring.create(
      amountInput: '5.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.daily,
      startDate: DateTime(2026, 3, 1),
    );

    await service.materialiseDue(asOf: DateTime(2026, 3, 3));
    final second = await service.materialiseDue(asOf: DateTime(2026, 3, 3));

    expect(second.valueOrNull, 0);
    expect(expenses.all(), hasLength(3));
  });

  test('a second pass only generates the newly-elapsed occurrences', () async {
    await recurring.create(
      amountInput: '5.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.daily,
      startDate: DateTime(2026, 3, 1),
    );

    await service.materialiseDue(asOf: DateTime(2026, 3, 3));
    final second = await service.materialiseDue(asOf: DateTime(2026, 3, 5));

    expect(second.valueOrNull, 2);
    expect(expenses.all(), hasLength(5));
  });

  test('catch-up is bounded by AppConstants.maxRecurringCatchUp', () async {
    await recurring.create(
      amountInput: '1.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.daily,
      startDate: DateTime(2000, 1, 1),
    );

    final result = await service.materialiseDue(asOf: DateTime(2026, 1, 1));
    expect(result.valueOrNull, AppConstants.maxRecurringCatchUp);
    expect(expenses.all(), hasLength(AppConstants.maxRecurringCatchUp));
  });

  test('occurrences past endDate are never generated', () async {
    await recurring.create(
      amountInput: '5.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.daily,
      startDate: DateTime(2026, 3, 1),
      endDate: DateTime(2026, 3, 2),
    );

    // asOf sits exactly on endDate, so the recurrence is not yet expired and
    // both in-range occurrences are generated.
    final firstPass = await service.materialiseDue(asOf: DateTime(2026, 3, 2));
    expect(firstPass.valueOrNull, 2);
    expect(expenses.all(), hasLength(2));

    // A later asOf pushes `now` past endDate, so RecurringService.isExpired
    // short-circuits the whole template — no further occurrences (there are
    // none left to generate anyway, since Mar 2 was the last one in range).
    final secondPass = await service.materialiseDue(asOf: DateTime(2026, 3, 10));
    expect(secondPass.valueOrNull, 0);
    expect(expenses.all(), hasLength(2));
  });

  test('an inactive recurrence generates nothing', () async {
    final created = await recurring.create(
      amountInput: '5.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.daily,
      startDate: DateTime(2026, 3, 1),
    );
    await recurring.setActive(created.valueOrNull!.id, false);

    final result = await service.materialiseDue(asOf: DateTime(2026, 3, 5));
    expect(result.valueOrNull, 0);
    expect(expenses.all(), isEmpty);
  });

  test('monthly recurrence clamps Jan 31 -> Feb 28 -> Mar 28, not Mar 3', () async {
    await recurring.create(
      amountInput: '20.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.monthly,
      startDate: DateTime(2026, 1, 31),
    );

    final result = await service.materialiseDue(asOf: DateTime(2026, 3, 31));
    expect(result.valueOrNull, 3);
    final dates = expenses.all().map((e) => e.date).toList()..sort();
    expect(dates, [DateTime(2026, 1, 31), DateTime(2026, 2, 28), DateTime(2026, 3, 28)]);
  });

  test('a yearly recurrence starting Feb 29 clamps to the 28th every year after, '
      'even when a later year is itself a leap year', () async {
    // addMonthsClamped clamps the *day of the previous occurrence*, not the
    // original start date — so once 2024-02-29 clamps down to 2025-02-28, every
    // later occurrence stays on the 28th, including 2028 (a leap year). This is
    // the day-drift consequence of clamped calendar arithmetic documented in
    // date_range.dart, not a bug in this test.
    await recurring.create(
      amountInput: '100.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.yearly,
      startDate: DateTime(2024, 2, 29),
    );

    final result = await service.materialiseDue(asOf: DateTime(2028, 3, 1));
    expect(result.valueOrNull, 5);
    final dates = expenses.all().map((e) => e.date).toList()..sort();
    expect(dates.first, DateTime(2024, 2, 29));
    expect(dates.last, DateTime(2028, 2, 28));
  });
}
