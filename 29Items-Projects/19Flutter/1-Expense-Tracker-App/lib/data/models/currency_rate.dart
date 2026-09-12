/// Hive typeId 6 — cached FX rate, keyed by currency code.
///
/// The app is fully usable offline with stale (or manually entered) rates; a
/// staleness banner is shown rather than blocking the UI. ARCHITECTURE §2.5.
library;

class CurrencyRate {
  CurrencyRate({
    required this.code,
    required this.baseCode,
    required this.rateToBase,
    required this.updatedAt,
    this.isManual = false,
  });

  /// ISO-4217 of the foreign currency.
  final String code;

  /// The base this rate converts *to*. If the user changes base currency, the
  /// whole cache is invalidated — a rate is only meaningful against its base.
  final String baseCode;

  /// Multiplier: `amount_in_code * rateToBase == amount_in_baseCode`.
  ///
  /// Must be finite and > 0. `CurrencyService` rejects anything else — an API
  /// returning 0 would silently zero out every converted total.
  final double rateToBase;

  final DateTime updatedAt;

  /// User-entered rate. Never overwritten by a background refresh — an explicit
  /// choice beats an automatic one.
  final bool isManual;

  bool isStale(DateTime now, Duration maxAge) =>
      now.difference(updatedAt) > maxAge;

  bool get isValid => rateToBase.isFinite && rateToBase > 0;

  CurrencyRate copyWith({double? rateToBase, DateTime? updatedAt, bool? isManual}) {
    return CurrencyRate(
      code: code,
      baseCode: baseCode,
      rateToBase: rateToBase ?? this.rateToBase,
      updatedAt: updatedAt ?? this.updatedAt,
      isManual: isManual ?? this.isManual,
    );
  }

  @override
  String toString() => 'CurrencyRate($code→$baseCode @ $rateToBase, $updatedAt)';
}
