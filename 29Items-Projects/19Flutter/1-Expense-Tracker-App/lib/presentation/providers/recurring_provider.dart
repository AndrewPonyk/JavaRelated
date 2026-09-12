/// Drives the recurring expenses screen. Interacts only with [RecurringRepository]
/// and [RecurringService] (ARCHITECTURE §2.1).
library;

import 'package:flutter/foundation.dart';

import '../../core/error/failures.dart';
import '../../core/utils/result.dart';
import '../../data/models/recurring_expense.dart';
import '../../data/repositories/recurring_repository.dart';
import '../../domain/services/recurring_service.dart';
import 'async_state.dart';

class RecurringProvider extends ChangeNotifier {
  RecurringProvider(this._recurring, this._service) {
    load();
  }

  final RecurringRepository _recurring;
  final RecurringService _service;

  AsyncState<List<RecurringExpense>> _state = const AsyncLoading();

  AsyncState<List<RecurringExpense>> get state => _state;

  void load() {
    _state = AsyncData(_recurring.all());
    notifyListeners();
  }

  Future<Failure?> addRecurring({
    required String amountInput,
    required String currencyCode,
    required String categoryId,
    required RecurrenceFrequency frequency,
    required DateTime startDate,
    String? note,
    DateTime? endDate,
  }) async {
    final result = await _recurring.create(
      amountInput: amountInput,
      currencyCode: currencyCode,
      categoryId: categoryId,
      frequency: frequency,
      startDate: startDate,
      note: note,
      endDate: endDate,
    );
    if (result case Err<RecurringExpense>(:final failure)) return failure;

    // Catch up any expenses that are already due as of today.
    await _service.materialiseDue();
    load();
    return null;
  }

  Future<Failure?> toggleActive(String id, bool isActive) async {
    final result = await _recurring.setActive(id, isActive);
    if (result case Err<void>(:final failure)) return failure;
    if (isActive) {
      await _service.materialiseDue();
    }
    load();
    return null;
  }

  Future<Failure?> delete(String id) async {
    final result = await _recurring.delete(id);
    if (result case Err<void>(:final failure)) return failure;
    load();
    return null;
  }
}
