/// Budget-vs-spend computation. Pure business logic — no Flutter, no Hive
/// imports (see docs/PROJECT-PLAN.md §1.3(b)).
library;

import '../../core/utils/date_range.dart';
import '../../core/utils/money.dart';
import '../../core/utils/result.dart';
import '../../data/models/budget.dart';
import '../../data/repositories/budget_repository.dart';
import '../../data/repositories/expense_repository.dart';
import 'currency_service.dart';

enum BudgetStatus { safe, warning, over }

/// A budget's spend, converted into the budget's own currency, as of now.
class BudgetProgress {
  const BudgetProgress({
    required this.budget,
    required this.spent,
    required this.ratio,
    required this.status,
  });

  final Budget budget;
  final Money spent;

  /// `spent / limit`, unclamped — can exceed 1.0 when over budget.
  final double ratio;
  final BudgetStatus status;

  Money get remaining => budget.limit - spent;
}

class BudgetService {
  BudgetService(this._budgets, this._expenses, this._currency);

  final BudgetRepository _budgets;
  final ExpenseRepository _expenses;
  final CurrencyService _currency;

  /// Expenses in a foreign currency with no cached rate are skipped rather
  /// than failing the whole computation — one missing rate should not blank
  /// out an otherwise-correct progress bar (see ARCHITECTURE §2.6).
  BudgetProgress progressFor(Budget budget) {
    final range = DateRange.month(budget.month);
    final matching = budget.isOverall
        ? _expenses.byDateRange(range)
        : _expenses.byCategory(budget.categoryId!, within: range);

    var spent = Money.zero(budget.currencyCode);
    for (final expense in matching) {
      final converted = _currency.convert(expense.amount, budget.currencyCode);
      if (converted is Ok<Money>) spent = spent + converted.value;
    }

    final ratio = budget.limit.isZero ? 0.0 : spent.asDouble / budget.limit.asDouble;
    final status = ratio >= 1.0
        ? BudgetStatus.over
        : ratio >= budget.alertThreshold
            ? BudgetStatus.warning
            : BudgetStatus.safe;

    return BudgetProgress(budget: budget, spent: spent, ratio: ratio, status: status);
  }

  List<BudgetProgress> progressForMonth(DateTime month) =>
      _budgets.allFor(month).map(progressFor).toList(growable: false);
}
