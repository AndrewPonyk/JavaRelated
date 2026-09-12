/// Cached FX rates. Validation boundary for [CurrencyRate] the same way
/// [ExpenseRepository] is for expenses — `domain/services/currency_service.dart`
/// owns the actual fetch/staleness policy, this class only stores and validates.
library;

import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:hive_flutter/hive_flutter.dart';

import '../../core/error/failures.dart';
import '../../core/utils/result.dart';
import '../datasources/hive_service.dart';
import '../models/currency_rate.dart';

class CurrencyRepository {
  CurrencyRepository(this._hive);

  final HiveService _hive;

  Box<CurrencyRate> get _box => _hive.rates;

  ValueListenable<Box<CurrencyRate>> listenable() => _box.listenable();

  CurrencyRate? getByCode(String code) => _box.get(code.toUpperCase());

  List<CurrencyRate> all() => _box.values.toList(growable: false);

  /// Rates are keyed by their own code — one row per currency, always relative
  /// to whatever [CurrencyRate.baseCode] was at write time. Callers must clear
  /// the box (see [SettingsRepository.setBaseCurrency]) before writing rates
  /// against a new base.
  Future<Result<CurrencyRate>> upsert(CurrencyRate rate) async {
    if (!rate.isValid) {
      return const Err(ValidationFailure('Exchange rate must be a positive, finite number'));
    }
    if (rate.code.length != 3 || rate.baseCode.length != 3) {
      return const Err(ValidationFailure('Invalid currency code'));
    }
    try {
      await _box.put(rate.code.toUpperCase(), rate);
      return Ok(rate);
    } catch (error, stack) {
      return Err(StorageFailure('Could not save exchange rate', cause: error, stackTrace: stack));
    }
  }

  /// Batch write from a fetch — one `putAll` rather than N `put`s.
  Future<Result<int>> upsertAll(List<CurrencyRate> rates) async {
    final invalid = rates.where((r) => !r.isValid);
    if (invalid.isNotEmpty) {
      return const Err(ValidationFailure('Exchange rate must be a positive, finite number'));
    }
    if (rates.isEmpty) return const Ok(0);
    try {
      await _box.putAll({for (final r in rates) r.code.toUpperCase(): r});
      return Ok(rates.length);
    } catch (error, stack) {
      return Err(StorageFailure('Could not save exchange rates', cause: error, stackTrace: stack));
    }
  }

  /// Manually entered rate — always wins over a live fetch until the user clears
  /// it, since a traveller often knows the till rate better than an API.
  Future<Result<CurrencyRate>> setManual({
    required String code,
    required String baseCode,
    required double rateToBase,
  }) {
    return upsert(CurrencyRate(
      code: code.toUpperCase(),
      baseCode: baseCode.toUpperCase(),
      rateToBase: rateToBase,
      updatedAt: DateTime.now(),
      isManual: true,
    ));
  }

  Future<Result<void>> delete(String code) async {
    final key = code.toUpperCase();
    if (!_box.containsKey(key)) {
      return Err(NotFoundFailure('No cached rate for that currency', id: key));
    }
    try {
      await _box.delete(key);
      return okVoid;
    } catch (error, stack) {
      return Err(StorageFailure('Could not delete rate', cause: error, stackTrace: stack));
    }
  }
}
