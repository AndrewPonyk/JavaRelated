import 'package:flutter_test/flutter_test.dart';

import 'package:expense_tracker/data/models/budget.dart';
import 'package:expense_tracker/data/repositories/budget_repository.dart';
import 'package:expense_tracker/data/repositories/currency_repository.dart';
import 'package:expense_tracker/data/repositories/expense_repository.dart';
import 'package:expense_tracker/data/repositories/settings_repository.dart';
import 'package:expense_tracker/domain/services/budget_service.dart';
import 'package:expense_tracker/domain/services/currency_service.dart';

import '../helpers/hive_test_helper.dart';

void main() {
  late HiveTestHarness harness;
  late BudgetRepository budgets;
  late ExpenseRepository expenses;
  late BudgetService service;
  late String categoryId;

  final month = DateTime(2026, 3);

  setUp(() async {
    harness = await HiveTestHarness.open();
    budgets = BudgetRepository(harness.hive);
    expenses = ExpenseRepository(harness.hive);
    final settings = SettingsRepository(harness.hive);
    final currency = CurrencyService(CurrencyRepository(harness.hive), settings);
    service = BudgetService(budgets, expenses, currency);

    final category = await settings.addCategory(name: 'Food');
    categoryId = category.valueOrNull!.id;
  });

  tearDown(() => harness.dispose());

  Future<void> addExpense(String amount, DateTime date) async {
    await expenses.create(ExpenseDraft(
      amountInput: amount,
      currencyCode: 'USD',
      categoryId: categoryId,
      date: date,
    ));
  }

  Future<Budget> upsertBudget({String? categoryId, double? alertThreshold}) async {
    final result = await budgets.upsert(
      month: month,
      limitInput: '100.00',
      currencyCode: 'USD',
      categoryId: categoryId,
      alertThreshold: alertThreshold,
    );
    return result.valueOrNull!;
  }

  test('no budget set for the month yields an empty progress list', () {
    expect(service.progressForMonth(month), isEmpty);
  });

  test('0% spent is safe', () async {
    final budget = await upsertBudget();
    final progress = service.progressFor(budget);
    expect(progress.ratio, 0.0);
    expect(progress.status, BudgetStatus.safe);
    expect(progress.spent.isZero, isTrue);
  });

  test('spend exactly at the alert threshold is warning, not over', () async {
    final budget = await upsertBudget(alertThreshold: 0.8);
    await addExpense('80.00', DateTime(2026, 3, 10));

    final progress = service.progressFor(budget);
    expect(progress.ratio, closeTo(0.8, 1e-9));
    expect(progress.status, BudgetStatus.warning);
  });

  test('spend exactly at 100% is over, not warning', () async {
    final budget = await upsertBudget();
    await addExpense('100.00', DateTime(2026, 3, 10));

    final progress = service.progressFor(budget);
    expect(progress.ratio, closeTo(1.0, 1e-9));
    expect(progress.status, BudgetStatus.over);
  });

  test('spend beyond the limit is over, and remaining goes negative', () async {
    final budget = await upsertBudget();
    await addExpense('60.00', DateTime(2026, 3, 5));
    await addExpense('75.00', DateTime(2026, 3, 20));

    final progress = service.progressFor(budget);
    expect(progress.status, BudgetStatus.over);
    expect(progress.spent.decimalString, '135.00');
    expect(progress.remaining.isNegative, isTrue);
  });

  test('expenses outside the budget month are excluded', () async {
    final budget = await upsertBudget();
    await addExpense('50.00', DateTime(2026, 2, 28));
    await addExpense('50.00', DateTime(2026, 4, 1));

    final progress = service.progressFor(budget);
    expect(progress.spent.isZero, isTrue);
    expect(progress.status, BudgetStatus.safe);
  });

  test('progressForMonth reflects a category budget scoped to its own category', () async {
    final budget = await upsertBudget(categoryId: categoryId);
    await addExpense('20.00', DateTime(2026, 3, 15));

    final list = service.progressForMonth(month);
    expect(list, hasLength(1));
    expect(list.single.budget.id, budget.id);
    expect(list.single.spent.decimalString, '20.00');
  });
}
