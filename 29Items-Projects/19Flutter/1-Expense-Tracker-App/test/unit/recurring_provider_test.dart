import 'package:flutter_test/flutter_test.dart';

import 'package:expense_tracker/data/models/recurring_expense.dart';
import 'package:expense_tracker/data/repositories/expense_repository.dart';
import 'package:expense_tracker/data/repositories/recurring_repository.dart';
import 'package:expense_tracker/data/repositories/settings_repository.dart';
import 'package:expense_tracker/domain/services/recurring_service.dart';
import 'package:expense_tracker/presentation/providers/async_state.dart';
import 'package:expense_tracker/presentation/providers/recurring_provider.dart';

import '../helpers/hive_test_helper.dart';

void main() {
  late HiveTestHarness harness;
  late RecurringRepository recurringRepo;
  late ExpenseRepository expenseRepo;
  late RecurringService service;
  late RecurringProvider provider;
  late String categoryId;

  setUp(() async {
    harness = await HiveTestHarness.open();
    recurringRepo = RecurringRepository(harness.hive);
    expenseRepo = ExpenseRepository(harness.hive);
    service = RecurringService(recurringRepo, expenseRepo);
    provider = RecurringProvider(recurringRepo, service);

    final settings = SettingsRepository(harness.hive);
    categoryId = (await settings.addCategory(name: 'Subscriptions')).valueOrNull!.id;
  });

  tearDown(() => harness.dispose());

  test('initial state is data with empty list when no recurring items exist', () {
    expect(provider.state, isA<AsyncData<List<RecurringExpense>>>());
    expect((provider.state as AsyncData<List<RecurringExpense>>).value, isEmpty);
  });

  test('addRecurring creates rule, updates provider state, and materialises expenses', () async {
    final failure = await provider.addRecurring(
      amountInput: '9.99',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.monthly,
      startDate: DateTime.now().subtract(const Duration(days: 1)),
      note: 'Streaming Service',
    );

    expect(failure, isNull);
    expect(provider.state, isA<AsyncData<List<RecurringExpense>>>());
    final list = (provider.state as AsyncData<List<RecurringExpense>>).value;
    expect(list, hasLength(1));
    expect(list.first.note, 'Streaming Service');
    expect(list.first.amount.decimalString, '9.99');

    // Due expense was materialized
    expect(expenseRepo.all(), isNotEmpty);
  });

  test('toggleActive pauses and resumes recurring expense', () async {
    await provider.addRecurring(
      amountInput: '15.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.monthly,
      startDate: DateTime.now(),
      note: 'Gym',
    );

    final item = (provider.state as AsyncData<List<RecurringExpense>>).value.first;
    expect(item.isActive, isTrue);

    await provider.toggleActive(item.id, false);
    final paused = (provider.state as AsyncData<List<RecurringExpense>>).value.first;
    expect(paused.isActive, isFalse);

    await provider.toggleActive(item.id, true);
    final resumed = (provider.state as AsyncData<List<RecurringExpense>>).value.first;
    expect(resumed.isActive, isTrue);
  });

  test('delete removes recurring expense from state', () async {
    await provider.addRecurring(
      amountInput: '5.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      frequency: RecurrenceFrequency.weekly,
      startDate: DateTime.now(),
      note: 'Weekly club',
    );

    final item = (provider.state as AsyncData<List<RecurringExpense>>).value.first;
    await provider.delete(item.id);

    final remaining = (provider.state as AsyncData<List<RecurringExpense>>).value;
    expect(remaining, isEmpty);
  });
}
