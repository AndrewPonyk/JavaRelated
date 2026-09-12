/// Budget CRUD + validation. Budgets are keyed deterministically
/// ([Budget.keyFor]) so a month+category pair can never acquire two rows.
library;

import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:hive_flutter/hive_flutter.dart';

import '../../core/error/failures.dart';
import '../../core/utils/date_range.dart';
import '../../core/utils/money.dart';
import '../../core/utils/result.dart';
import '../datasources/hive_service.dart';
import '../models/budget.dart';

class BudgetRepository {
  BudgetRepository(this._hive);

  final HiveService _hive;

  Box<Budget> get _box => _hive.budgets;

  ValueListenable<Box<Budget>> listenable() => _box.listenable();

  /// Overall budget for [month], or `null` if none set.
  Budget? overallFor(DateTime month) => _box.get(Budget.keyFor(month, null));

  Budget? forCategory(DateTime month, String categoryId) =>
      _box.get(Budget.keyFor(month, categoryId));

  List<Budget> allFor(DateTime month) {
    final key = monthKey(month);
    return _box.values.where((b) => b.month == key).toList(growable: false);
  }

  List<Budget> all() => _box.values.toList(growable: false);

  /// Creates or replaces the budget for [month] + [categoryId].
  ///
  /// [limitInput] is raw text; parsing and validation happen here, never in the
  /// widget.
  Future<Result<Budget>> upsert({
    required DateTime month,
    required String limitInput,
    required String currencyCode,
    String? categoryId,
    double? alertThreshold,
  }) async {
    final Money limit;
    try {
      limit = Money.parse(limitInput, currencyCode);
    } on MoneyFormatException {
      return const Err(ValidationFailure('Enter a valid budget amount', field: 'limit'));
    } on ArgumentError {
      return const Err(ValidationFailure('Unknown currency code', field: 'currency'));
    }

    // A zero budget is meaningless and would make every ratio a division by zero.
    // BudgetService also guards, but rejecting it here means the bad state never
    // exists at all.
    if (!limit.isPositive) {
      return const Err(ValidationFailure(
        'Budget must be greater than zero',
        field: 'limit',
      ));
    }

    if (categoryId != null && !_hive.categories.containsKey(categoryId)) {
      return const Err(ValidationFailure('Unknown category', field: 'category'));
    }

    final threshold = alertThreshold ?? 0.8;
    if (threshold <= 0 || threshold > 1) {
      return const Err(ValidationFailure(
        'Alert threshold must be between 0 and 100%',
        field: 'alertThreshold',
      ));
    }

    final key = Budget.keyFor(month, categoryId);
    final budget = Budget(
      id: key,
      month: month,
      limitMinor: limit.minorUnits,
      currencyCode: limit.currencyCode,
      categoryId: categoryId,
      alertThreshold: threshold,
    );

    try {
      await _box.put(key, budget);
      return Ok(budget);
    } catch (error, stack) {
      return Err(StorageFailure('Could not save budget', cause: error, stackTrace: stack));
    }
  }

  Future<Result<void>> delete(DateTime month, String? categoryId) async {
    final key = Budget.keyFor(month, categoryId);
    if (!_box.containsKey(key)) {
      return Err(NotFoundFailure('No budget set for that month', id: key));
    }
    try {
      await _box.delete(key);
      return okVoid;
    } catch (error, stack) {
      return Err(StorageFailure('Could not delete budget', cause: error, stackTrace: stack));
    }
  }

  /// Copies every budget from [from] into [to] — used by the monthly rollover so
  /// the user does not re-enter the same limits every month.
  ///
  /// Does not overwrite a budget that already exists in [to]: an explicit choice
  /// for the new month always wins over a copied default.
  Future<Result<int>> copyMonth({required DateTime from, required DateTime to}) async {
    final source = allFor(from);
    if (source.isEmpty) return const Ok(0);

    final entries = <String, Budget>{};
    for (final b in source) {
      final key = Budget.keyFor(to, b.categoryId);
      if (_box.containsKey(key)) continue;
      entries[key] = Budget(
        id: key,
        month: to,
        limitMinor: b.limitMinor,
        currencyCode: b.currencyCode,
        categoryId: b.categoryId,
        alertThreshold: b.alertThreshold,
      );
    }
    if (entries.isEmpty) return const Ok(0);
    try {
      await _box.putAll(entries);
      return Ok(entries.length);
    } catch (error, stack) {
      return Err(StorageFailure('Could not roll budgets forward',
          cause: error, stackTrace: stack));
    }
  }
}
