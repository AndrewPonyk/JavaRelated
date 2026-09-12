/// Multi-currency conversion. Pure business logic — no Flutter, no Hive
/// imports (see docs/PROJECT-PLAN.md §1.3(b)). All rate storage/validation is
/// delegated to [CurrencyRepository]; this class only decides *when* a rate
/// is usable and how to fetch fresh ones.
library;

import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/config/env.dart';
import '../../core/constants/app_constants.dart';
import '../../core/error/failures.dart';
import '../../core/utils/money.dart';
import '../../core/utils/result.dart';
import '../../data/models/currency_rate.dart';
import '../../data/repositories/currency_repository.dart';
import '../../data/repositories/settings_repository.dart';

class CurrencyService {
  CurrencyService(this._rates, this._settings, {http.Client? client})
      : _client = client ?? http.Client();

  final CurrencyRepository _rates;
  final SettingsRepository _settings;
  final http.Client _client;

  /// Converts [amount] into [targetCode] using the last cached rate. No
  /// network call here — this is a synchronous, UI-safe read. Same-currency
  /// conversion always succeeds even with an empty cache.
  Result<Money> convert(Money amount, String targetCode) {
    final target = targetCode.toUpperCase();
    if (amount.currencyCode == target) return Ok(amount);

    final rate = _rates.getByCode(amount.currencyCode);
    if (rate == null || rate.baseCode != target) {
      return Err(NotFoundFailure(
        'No exchange rate cached for ${amount.currencyCode} -> $target',
        id: amount.currencyCode,
      ));
    }
    return Ok(amount.scaled(rate.rateToBase));
  }

  /// The multiplier last used for `code` -> the current base currency, or
  /// `null` if nothing is cached. Exposed for CSV export, which needs to show
  /// the rate itself, not just the converted total — and `Money.ratioTo`
  /// can't be reused for this since it asserts matching currencies.
  double? cachedRateToBase(String code) {
    final rate = _rates.getByCode(code);
    if (rate == null || rate.baseCode != _settings.current().baseCurrency) {
      return null;
    }
    return rate.rateToBase;
  }

  bool isStale(String code, {Duration maxAge = AppConstants.fxStaleAfter}) {
    final rate = _rates.getByCode(code);
    if (rate == null) return true;
    return rate.isStale(DateTime.now(), maxAge);
  }

  /// Fetches fresh rates for [currencyCodes] against the base currency and
  /// caches them. A no-op (returns `Ok(0)`) when [Env.fxEnabled] is false —
  /// live rates are an optional, best-effort feature per
  /// docs/ARCHITECTURE.md §2.6, never a hard dependency for logging expenses.
  ///
  /// SECURITY: [Env.fxApiKey] ships inside the compiled binary and is
  /// trivially extractable by anyone with the APK/IPA. Only ever configure a
  /// rate-limit-scoped key here — never a billable or privileged secret. See
  /// `.env.example`.
  Future<Result<int>> refreshRates(Set<String> currencyCodes) async {
    if (!Env.fxEnabled) return const Ok(0);

    final base = _settings.current().baseCurrency;
    // A manually-entered rate is a deliberate user override (e.g. a traveller
    // who knows the till rate) — a live fetch must never clobber it.
    final manualOverrides = {
      for (final rate in _rates.all())
        if (rate.isManual) rate.code,
    };
    final toFetch = currencyCodes
        .map((c) => c.toUpperCase())
        .where((c) => c != base && !manualOverrides.contains(c))
        .toSet();
    if (toFetch.isEmpty) return const Ok(0);

    try {
      // TODO: replace with the real FX provider's request shape once chosen —
      // this stub assumes `{ "rates": { "EUR": 0.92, ... } }` relative to `base`.
      final uri = Uri.parse('${Env.fxApiBaseUrl}/latest').replace(queryParameters: {
        'base': base,
        'symbols': toFetch.join(','),
        if (Env.fxApiKey.isNotEmpty) 'access_key': Env.fxApiKey,
      });
      final response = await _client.get(uri).timeout(AppConstants.fxTimeout);
      if (response.statusCode != 200) {
        return Err(NetworkFailure('FX provider returned ${response.statusCode}'));
      }

      final body = jsonDecode(response.body) as Map<String, dynamic>;
      final rawRates = (body['rates'] as Map?) ?? const {};
      final now = DateTime.now();
      final fresh = <CurrencyRate>[
        for (final code in toFetch)
          if (rawRates[code] is num)
            CurrencyRate(
              code: code,
              baseCode: base,
              rateToBase: 1 / (rawRates[code] as num).toDouble(),
              updatedAt: now,
            ),
      ];
      final saved = await _rates.upsertAll(fresh);
      return saved;
    } on Exception catch (error, stack) {
      final isTimeout = error is TimeoutException;
      return Err(NetworkFailure(
        'Could not refresh exchange rates',
        isTimeout: isTimeout,
        cause: error,
        stackTrace: stack,
      ));
    }
  }
}
