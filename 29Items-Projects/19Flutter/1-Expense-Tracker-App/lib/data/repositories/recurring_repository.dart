/// Recurring-expense templates. The generation logic lives in
/// `domain/services/recurring_service.dart`; this class only stores and
/// validates.
library;

import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:hive_flutter/hive_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../core/error/failures.dart';
import '../../core/utils/date_range.dart';
import '../../core/utils/money.dart';
import '../../core/utils/result.dart';
import '../datasources/hive_service.dart';
import '../models/recurring_expense.dart';

class RecurringRepository {
  RecurringRepository(this._hive, {Uuid? uuid}) : _uuid = uuid ?? const Uuid();

  final HiveService _hive;
  final Uuid _uuid;

  Box<RecurringExpense> get _box => _hive.recurring;

  ValueListenable<Box<RecurringExpense>> listenable() => _box.listenable();

  List<RecurringExpense> all() => _box.values.toList(growable: false);

  List<RecurringExpense> active() =>
      _box.values.where((r) => r.isActive).toList(growable: false);

  RecurringExpense? getById(String id) => _box.get(id);

  Future<Result<RecurringExpense>> create({
    required String amountInput,
    required String currencyCode,
    required String categoryId,
    required RecurrenceFrequency frequency,
    required DateTime startDate,
    String? note,
    DateTime? endDate,
  }) async {
    final Money amount;
    try {
      amount = Money.parse(amountInput, currencyCode);
    } on MoneyFormatException {
      return const Err(ValidationFailure('Enter a valid amount', field: 'amount'));
    } on ArgumentError {
      return const Err(ValidationFailure('Unknown currency code', field: 'currency'));
    }

    if (!amount.isPositive) {
      return const Err(ValidationFailure('Amount must be greater than zero', field: 'amount'));
    }
    if (!_hive.categories.containsKey(categoryId)) {
      return const Err(ValidationFailure('Choose a category', field: 'category'));
    }
    if (endDate != null && endDate.isBefore(startDate)) {
      return const Err(ValidationFailure(
        'End date must be after the start date',
        field: 'endDate',
      ));
    }

    final recurring = RecurringExpense(
      id: _uuid.v4(),
      amountMinor: amount.minorUnits,
      currencyCode: amount.currencyCode,
      categoryId: categoryId,
      frequency: frequency,
      startDate: dateOnly(startDate),
      note: note?.trim().isEmpty ?? true ? null : note!.trim(),
      endDate: endDate == null ? null : dateOnly(endDate),
    );

    try {
      await _box.put(recurring.id, recurring);
      return Ok(recurring);
    } catch (error, stack) {
      return Err(StorageFailure('Could not save recurring expense',
          cause: error, stackTrace: stack));
    }
  }

  /// Advances the idempotency watermark after a materialisation pass.
  ///
  /// Called by [RecurringService] only. Writing this before the generated
  /// expenses are persisted would lose them permanently, so the service always
  /// inserts first, then advances.
  Future<Result<void>> updateWatermark(String id, DateTime lastGenerated) async {
    final existing = _box.get(id);
    if (existing == null) return Err(NotFoundFailure('Recurring expense not found', id: id));
    try {
      await _box.put(id, existing.copyWith(lastGeneratedDate: lastGenerated));
      return okVoid;
    } catch (error, stack) {
      return Err(StorageFailure('Could not update recurrence',
          cause: error, stackTrace: stack));
    }
  }

  Future<Result<void>> setActive(String id, bool isActive) async {
    final existing = _box.get(id);
    if (existing == null) return Err(NotFoundFailure('Recurring expense not found', id: id));
    try {
      await _box.put(id, existing.copyWith(isActive: isActive));
      return okVoid;
    } catch (error, stack) {
      return Err(StorageFailure('Could not update recurrence',
          cause: error, stackTrace: stack));
    }
  }

  Future<Result<void>> delete(String id) async {
    if (!_box.containsKey(id)) {
      return Err(NotFoundFailure('Recurring expense not found', id: id));
    }
    try {
      await _box.delete(id);
      return okVoid;
    } catch (error, stack) {
      return Err(StorageFailure('Could not delete recurrence',
          cause: error, stackTrace: stack));
    }
  }
}
