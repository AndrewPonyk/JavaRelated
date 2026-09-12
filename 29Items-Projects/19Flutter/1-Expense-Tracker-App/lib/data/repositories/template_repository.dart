/// Quick-add templates for frequent purchases.
library;

import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:hive_flutter/hive_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../core/error/failures.dart';
import '../../core/utils/money.dart';
import '../../core/utils/result.dart';
import '../datasources/hive_service.dart';
import '../models/expense.dart';
import '../models/quick_template.dart';

class TemplateRepository {
  TemplateRepository(this._hive, {Uuid? uuid}) : _uuid = uuid ?? const Uuid();

  final HiveService _hive;
  final Uuid _uuid;

  Box<QuickTemplate> get _box => _hive.templates;

  ValueListenable<Box<QuickTemplate>> listenable() => _box.listenable();

  /// Most-used first, then most-recent. The sheet gets better the more it is used.
  List<QuickTemplate> all() {
    final items = _box.values.toList()
      ..sort((a, b) {
        final byCount = b.useCount.compareTo(a.useCount);
        if (byCount != 0) return byCount;
        final aUsed = a.lastUsedAt ?? DateTime.fromMillisecondsSinceEpoch(0);
        final bUsed = b.lastUsedAt ?? DateTime.fromMillisecondsSinceEpoch(0);
        return bUsed.compareTo(aUsed);
      });
    return items;
  }

  QuickTemplate? getById(String id) => _box.get(id);

  Future<Result<QuickTemplate>> create({
    required String label,
    required String amountInput,
    required String currencyCode,
    required String categoryId,
    int? iconCodePoint,
  }) async {
    final trimmed = label.trim();
    if (trimmed.isEmpty) {
      return const Err(ValidationFailure('Give the template a name', field: 'label'));
    }
    if (trimmed.length > 40) {
      return const Err(ValidationFailure('Name is too long (max 40)', field: 'label'));
    }

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

    final template = QuickTemplate(
      id: _uuid.v4(),
      label: trimmed,
      amountMinor: amount.minorUnits,
      currencyCode: amount.currencyCode,
      categoryId: categoryId,
      iconCodePoint: iconCodePoint,
    );

    try {
      await _box.put(template.id, template);
      return Ok(template);
    } catch (error, stack) {
      return Err(StorageFailure('Could not save template', cause: error, stackTrace: stack));
    }
  }

  /// Creates a template from an expense the user already logged — the common
  /// path ("log this again next time with one tap").
  Future<Result<QuickTemplate>> createFromExpense(Expense expense, String label) {
    return create(
      label: label,
      amountInput: expense.amount.decimalString,
      currencyCode: expense.currencyCode,
      categoryId: expense.categoryId,
    );
  }

  /// Bumps usage stats. Deliberately fire-and-forget from the UI's perspective:
  /// a failure here must never block logging the expense itself.
  Future<void> markUsed(String id) async {
    final existing = _box.get(id);
    if (existing == null) return;
    await _box.put(id, existing.markUsed(DateTime.now()));
  }

  Future<Result<void>> delete(String id) async {
    if (!_box.containsKey(id)) {
      return Err(NotFoundFailure('Template not found', id: id));
    }
    try {
      await _box.delete(id);
      return okVoid;
    } catch (error, stack) {
      return Err(StorageFailure('Could not delete template', cause: error, stackTrace: stack));
    }
  }
}
