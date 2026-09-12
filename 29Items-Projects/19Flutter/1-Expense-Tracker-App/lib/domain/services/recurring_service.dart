/// Turns due [RecurringExpense] templates into real [Expense] rows. Pure
/// business logic — no Flutter, no Hive imports (see docs/PROJECT-PLAN.md
/// §1.3(b)).
///
/// Idempotency: generated expense ids are deterministic
/// (`'${recurring.id}_${yyyy-mm-dd}'`), so re-running this after a crash
/// simply overwrites the same rows rather than duplicating them — the real
/// safety net is inserting expenses *before* advancing the watermark (see
/// [RecurringRepository.updateWatermark]).
library;

import '../../core/constants/app_constants.dart';
import '../../core/utils/date_range.dart';
import '../../core/utils/result.dart';
import '../../data/models/expense.dart';
import '../../data/models/recurring_expense.dart';
import '../../data/repositories/expense_repository.dart';
import '../../data/repositories/recurring_repository.dart';

class RecurringService {
  RecurringService(this._recurring, this._expenses);

  final RecurringRepository _recurring;
  final ExpenseRepository _expenses;

  /// Materialises every occurrence due for every active recurrence, up to
  /// [asOf] (defaults to now). Returns the number of expenses generated.
  Future<Result<int>> materialiseDue({DateTime? asOf}) async {
    final now = dateOnly(asOf ?? DateTime.now());
    var generated = 0;

    for (final recurring in _recurring.active()) {
      if (recurring.isExpired(now)) continue;

      final due = _occurrencesDue(recurring, now);
      if (due.isEmpty) continue;

      final expenses = [
        for (final date in due)
          Expense(
            id: '${recurring.id}_${_dateKey(date)}',
            amountMinor: recurring.amountMinor,
            currencyCode: recurring.currencyCode,
            categoryId: recurring.categoryId,
            date: date,
            createdAt: DateTime.now(),
            note: recurring.note,
            recurringId: recurring.id,
          ),
      ];

      final inserted = await _expenses.createMany(expenses);
      if (inserted case Err<int>(:final failure)) return Err(failure);

      // Only advance the watermark once the expenses it covers are safely on
      // disk — reversing this order would silently drop occurrences on a crash.
      await _recurring.updateWatermark(recurring.id, due.last);
      generated += expenses.length;
    }

    return Ok(generated);
  }

  /// Every occurrence date strictly after the watermark (or the start date, if
  /// nothing has been generated yet) up to and including [now]. Bounded by
  /// [AppConstants.maxRecurringCatchUp] so a template left dormant for years
  /// can't flood the box in one pass.
  List<DateTime> _occurrencesDue(RecurringExpense recurring, DateTime now) {
    var next = recurring.lastGeneratedDate == null
        ? recurring.startDate
        : recurring.frequency.next(recurring.lastGeneratedDate!);

    final due = <DateTime>[];
    while (!next.isAfter(now) &&
        (recurring.endDate == null || !next.isAfter(recurring.endDate!)) &&
        due.length < AppConstants.maxRecurringCatchUp) {
      due.add(next);
      next = recurring.frequency.next(next);
    }
    return due;
  }

  String _dateKey(DateTime date) => date.toIso8601String().substring(0, 10);
}
