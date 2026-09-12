/// Single-row settings access. Also owns the category list, since categories are
/// configuration rather than transactional data.
library;

import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:hive_flutter/hive_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../core/constants/hive_boxes.dart';
import '../../core/error/failures.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/result.dart';
import '../datasources/hive_service.dart';
import '../models/app_settings.dart';
import '../models/expense_category.dart';

class SettingsRepository {
  SettingsRepository(this._hive, {Uuid? uuid}) : _uuid = uuid ?? const Uuid();

  final HiveService _hive;
  final Uuid _uuid;

  Box<AppSettings> get _box => _hive.settings;
  Box<ExpenseCategory> get _categories => _hive.categories;

  ValueListenable<Box<AppSettings>> listenable() => _box.listenable();
  ValueListenable<Box<ExpenseCategory>> categoriesListenable() =>
      _categories.listenable();

  /// Never returns null — an absent row means "defaults", not an error.
  AppSettings current() => _box.get(SettingsKeys.singleton) ?? AppSettings();

  Future<Result<AppSettings>> save(AppSettings settings) async {
    if (settings.budgetAlertThreshold <= 0 || settings.budgetAlertThreshold > 1) {
      return const Err(ValidationFailure(
        'Alert threshold must be between 0 and 100%',
        field: 'budgetAlertThreshold',
      ));
    }
    if (settings.baseCurrency.length != 3) {
      return const Err(ValidationFailure('Invalid currency code', field: 'baseCurrency'));
    }
    try {
      await _box.put(SettingsKeys.singleton, settings);
      return Ok(settings);
    } catch (error, stack) {
      return Err(StorageFailure('Could not save settings', cause: error, stackTrace: stack));
    }
  }

  /// Changing base currency invalidates every cached rate — a rate is only
  /// meaningful relative to its base (see [CurrencyRate.baseCode]).
  Future<Result<AppSettings>> setBaseCurrency(String code) async {
    final upper = code.toUpperCase();
    if (upper.length != 3) {
      return const Err(ValidationFailure('Invalid currency code', field: 'baseCurrency'));
    }
    // Every cached rate is expressed relative to the *old* base, so all of them
    // become meaningless the moment the base changes. Clearing is cheaper and
    // safer than trying to re-derive them by division.
    await _hive.rates.clear();
    return save(current().copyWith(baseCurrency: upper));
  }

  // -------------------------------------------------------------------------
  // Categories
  // -------------------------------------------------------------------------

  List<ExpenseCategory> categories() {
    final items = _categories.values.toList()
      ..sort((a, b) {
        final byOrder = a.sortOrder.compareTo(b.sortOrder);
        return byOrder != 0 ? byOrder : a.name.compareTo(b.name);
      });
    return items;
  }

  ExpenseCategory? categoryById(String id) => _categories.get(id);

  Map<String, ExpenseCategory> categoryMap() => {
        for (final c in _categories.values) c.id: c,
      };

  Future<Result<ExpenseCategory>> addCategory({
    required String name,
    int? iconCodePoint,
    int? colorValue,
  }) async {
    final trimmed = name.trim();
    if (trimmed.isEmpty) {
      return const Err(ValidationFailure('Category needs a name', field: 'name'));
    }
    final duplicate = _categories.values.any(
      (c) => c.name.toLowerCase() == trimmed.toLowerCase(),
    );
    if (duplicate) {
      return const Err(ValidationFailure('A category with that name already exists',
          field: 'name'));
    }

    final id = 'cat_${_uuid.v4()}';
    final category = ExpenseCategory(
      id: id,
      name: trimmed,
      iconCodePoint: iconCodePoint ?? 0xe5d3,
      // ignore: deprecated_member_use — Color.value is the documented ARGB int.
      colorValue: colorValue ?? AppColors.forCategoryId(id).toARGB32(),
      sortOrder: _categories.length,
    );
    try {
      await _categories.put(id, category);
      return Ok(category);
    } catch (error, stack) {
      return Err(StorageFailure('Could not save category', cause: error, stackTrace: stack));
    }
  }

  /// Built-in categories cannot be deleted — existing expenses and budgets
  /// reference their ids, and a dangling reference would hide those expenses from
  /// every breakdown.
  Future<Result<void>> deleteCategory(String id) async {
    final category = _categories.get(id);
    if (category == null) {
      return Err(NotFoundFailure('Category not found', id: id));
    }
    if (category.isBuiltIn) {
      return const Err(ValidationFailure('Built-in categories cannot be deleted'));
    }
    final inUse = _hive.expenses.values.any((e) => e.categoryId == id);
    if (inUse) {
      return const Err(ValidationFailure(
        'This category still has expenses. Move or delete them first.',
      ));
    }
    try {
      await _categories.delete(id);
      return okVoid;
    } catch (error, stack) {
      return Err(StorageFailure('Could not delete category', cause: error, stackTrace: stack));
    }
  }
}
