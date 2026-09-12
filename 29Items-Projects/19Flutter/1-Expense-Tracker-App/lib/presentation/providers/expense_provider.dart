/// Drives the expenses list screen. Never touches Hive directly — everything
/// goes through [ExpenseRepository], which owns validation (PROJECT-PLAN
/// §1.3(b): providers call repositories/services, not boxes).
library;

import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/error/failures.dart';
import '../../core/utils/date_range.dart';
import '../../core/utils/result.dart';
import '../../data/models/expense.dart';
import '../../data/repositories/expense_repository.dart';
import '../../data/repositories/template_repository.dart';
import 'async_state.dart';

class ExpenseProvider extends ChangeNotifier {
  ExpenseProvider(this._expenses, this._templates) {
    load();
  }

  final ExpenseRepository _expenses;
  final TemplateRepository _templates;

  DateTime _month = DateTime.now();
  AsyncState<List<Expense>> _state = const AsyncLoading();

  DateTime get month => _month;
  AsyncState<List<Expense>> get state => _state;

  /// Reloads the current (or newly selected) month from the repository. Safe
  /// to call after any write — a single in-memory box read, not a query.
  void load({DateTime? month}) {
    if (month != null) _month = month;
    _state = AsyncData(_expenses.byMonth(_month));
    notifyListeners();
  }

  void goToPreviousMonth() => load(month: addMonthsClamped(_month, -1));

  void goToNextMonth() => load(month: addMonthsClamped(_month, 1));

  Future<Failure?> addExpense(ExpenseDraft draft) async {
    final result = await _expenses.create(draft);
    if (result case Err<Expense>(:final failure)) return failure;
    load();
    return null;
  }

  /// Convenience for the quick-add sheet: creates the expense, then bumps the
  /// template's usage stats. The template bump is fire-and-forget by design
  /// (`TemplateRepository.markUsed`) — it must never block logging the spend.
  Future<Failure?> addFromTemplate(ExpenseDraft draft, String templateId) async {
    final failure = await addExpense(draft);
    if (failure == null) unawaited(_templates.markUsed(templateId));
    return failure;
  }

  Future<Failure?> updateExpense(ExpenseDraft draft) async {
    final result = await _expenses.update(draft);
    if (result case Err<Expense>(:final failure)) return failure;
    load();
    return null;
  }

  Future<Failure?> deleteExpense(String id) async {
    final result = await _expenses.delete(id);
    if (result case Err<void>(:final failure)) return failure;
    load();
    return null;
  }
}
