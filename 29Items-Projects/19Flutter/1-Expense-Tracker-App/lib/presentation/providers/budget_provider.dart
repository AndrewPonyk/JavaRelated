/// Drives the budget screen: per-month progress bars, backed by
/// [BudgetService] (spend math) and [BudgetRepository] (persistence).
library;

import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/error/failures.dart';
import '../../core/utils/date_range.dart';
import '../../core/utils/result.dart';
import '../../data/models/budget.dart';
import '../../data/repositories/budget_repository.dart';
import '../../domain/services/budget_service.dart';
import '../../domain/services/notification_service.dart';
import 'async_state.dart';

class BudgetProvider extends ChangeNotifier {
  BudgetProvider(this._budgets, this._budgetService, this._notifications) {
    load();
  }

  final BudgetRepository _budgets;
  final BudgetService _budgetService;
  final NotificationService _notifications;

  DateTime _month = DateTime.now();
  AsyncState<List<BudgetProgress>> _state = const AsyncLoading();

  /// Budget ids already alerted on for the current month, so re-rendering the
  /// same over-budget state on every rebuild does not resend the notification.
  final Set<String> _alerted = {};

  DateTime get month => _month;
  AsyncState<List<BudgetProgress>> get state => _state;

  void load({DateTime? month}) {
    if (month != null && monthKey(month) != monthKey(_month)) _alerted.clear();
    if (month != null) _month = month;

    final progress = _budgetService.progressForMonth(_month);
    _state = AsyncData(progress);
    notifyListeners();
    _notifyNewlyOverThreshold(progress);
  }

  void goToPreviousMonth() => load(month: addMonthsClamped(_month, -1));

  void goToNextMonth() => load(month: addMonthsClamped(_month, 1));

  void _notifyNewlyOverThreshold(List<BudgetProgress> progress) {
    for (final p in progress) {
      if (p.status == BudgetStatus.safe) continue;
      if (!_alerted.add(p.budget.id)) continue;
      unawaited(_notifications.notifyBudgetThreshold(p));
    }
  }

  Future<Failure?> setBudget({
    required String limitInput,
    required String currencyCode,
    String? categoryId,
    double? alertThreshold,
  }) async {
    final result = await _budgets.upsert(
      month: _month,
      limitInput: limitInput,
      currencyCode: currencyCode,
      categoryId: categoryId,
      alertThreshold: alertThreshold,
    );
    if (result case Err<Budget>(:final failure)) return failure;
    load();
    return null;
  }

  Future<Failure?> deleteBudget({String? categoryId}) async {
    final result = await _budgets.delete(_month, categoryId);
    if (result case Err<void>(:final failure)) return failure;
    load();
    return null;
  }

  /// Copies last month's budgets into the current month — offered on the
  /// monthly-rollover notification tap.
  Future<Failure?> copyFromPreviousMonth() async {
    final result = await _budgets.copyMonth(
      from: addMonthsClamped(_month, -1),
      to: _month,
    );
    if (result case Err<int>(:final failure)) return failure;
    load();
    return null;
  }
}
