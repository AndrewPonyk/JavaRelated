/// Integer-based money. The single most important type in this app.
///
/// Money is NEVER a `double`. `0.1 + 0.2 != 0.3` in IEEE-754, and an expense
/// tracker whose totals drift by cents has failed at its only job. All amounts
/// are stored and computed as **minor units** (cents, pence, fils) and divided
/// only at the moment of display.
///
/// See docs/TECH-NOTES.md §3.6 "Money".
library;

/// ISO-4217 exponents for currencies that are NOT 2-decimal.
///
/// Hardcoding `* 100` breaks for travellers — precisely this app's audience.
const Map<String, int> _currencyExponents = <String, int>{
  // Zero-decimal
  'JPY': 0, 'KRW': 0, 'VND': 0, 'CLP': 0, 'ISK': 0, 'UGX': 0, 'RWF': 0,
  'PYG': 0, 'XAF': 0, 'XOF': 0, 'XPF': 0, 'KMF': 0, 'DJF': 0, 'GNF': 0,
  // Three-decimal
  'KWD': 3, 'BHD': 3, 'JOD': 3, 'OMR': 3, 'TND': 3, 'LYD': 3, 'IQD': 3,
};

/// Number of decimal places for [currencyCode]; defaults to 2.
int currencyExponent(String currencyCode) =>
    _currencyExponents[currencyCode.toUpperCase()] ?? 2;

/// Thrown when a monetary string cannot be interpreted. Category A failure —
/// callers convert this into a `ValidationFailure`, they do not let it escape.
class MoneyFormatException implements Exception {
  const MoneyFormatException(this.input);
  final String input;
  @override
  String toString() => 'MoneyFormatException: cannot parse "$input" as money';
}

/// An immutable monetary amount in a single currency.
class Money implements Comparable<Money> {
  /// Amount in minor units. May be negative only for refunds/adjustments.
  final int minorUnits;

  /// Upper-case ISO-4217 code, e.g. `USD`.
  final String currencyCode;

  const Money._(this.minorUnits, this.currencyCode);

  factory Money(int minorUnits, String currencyCode) {
    final code = currencyCode.toUpperCase();
    if (code.length != 3) {
      throw ArgumentError.value(currencyCode, 'currencyCode', 'must be ISO-4217');
    }
    return Money._(minorUnits, code);
  }

  factory Money.zero(String currencyCode) => Money(0, currencyCode);

  /// Parses human input: `"12.50"`, `"1,234.56"`, `"12"`, `" 3,5 "` (comma
  /// decimal separator), `"-4.20"`.
  ///
  /// Throws [MoneyFormatException] on anything else. Deliberately strict:
  /// silently coercing bad input into 0 is how users lose expenses.
  factory Money.parse(String input, String currencyCode) {
    final exponent = currencyExponent(currencyCode);
    var s = input.trim();
    if (s.isEmpty) throw MoneyFormatException(input);

    var negative = false;
    if (s.startsWith('-')) {
      negative = true;
      s = s.substring(1).trim();
    } else if (s.startsWith('+')) {
      s = s.substring(1).trim();
    }

    // Strip grouping separators, then normalise a comma decimal mark.
    s = s.replaceAll(' ', '').replaceAll(' ', '');
    final lastComma = s.lastIndexOf(',');
    final lastDot = s.lastIndexOf('.');
    if (lastComma >= 0 && lastDot >= 0) {
      // Both present: the rightmost one is the decimal separator.
      s = lastComma > lastDot
          ? s.replaceAll('.', '').replaceFirst(',', '.')
          : s.replaceAll(',', '');
    } else if (lastComma >= 0) {
      // Only commas: treat as decimal mark if it looks like one.
      final tail = s.length - lastComma - 1;
      s = (tail > 0 && tail <= 3 && s.indexOf(',') == lastComma)
          ? s.replaceFirst(',', '.')
          : s.replaceAll(',', '');
    }

    if (!RegExp(r'^\d*\.?\d*$').hasMatch(s) || s == '.' || s.isEmpty) {
      throw MoneyFormatException(input);
    }

    final parts = s.split('.');
    final whole = parts[0].isEmpty ? '0' : parts[0];
    var frac = parts.length > 1 ? parts[1] : '';
    if (frac.length > exponent) {
      // Round half-up rather than truncate.
      final keep = frac.substring(0, exponent);
      final nextDigit = int.parse(frac[exponent]);
      var minor = int.parse('$whole${keep.padRight(exponent, '0')}');
      if (nextDigit >= 5) minor += 1;
      return Money(negative ? -minor : minor, currencyCode);
    }
    frac = frac.padRight(exponent, '0');
    final minor = int.parse('$whole$frac');
    return Money(negative ? -minor : minor, currencyCode);
  }

  int get exponent => currencyExponent(currencyCode);

  bool get isZero => minorUnits == 0;
  bool get isNegative => minorUnits < 0;
  bool get isPositive => minorUnits > 0;

  /// Decimal representation for display/export, e.g. `1250` USD → `"12.50"`.
  String get decimalString {
    final e = exponent;
    final sign = isNegative ? '-' : '';
    final abs = minorUnits.abs();
    if (e == 0) return '$sign$abs';
    final divisor = _pow10(e);
    final whole = abs ~/ divisor;
    final frac = (abs % divisor).toString().padLeft(e, '0');
    return '$sign$whole.$frac';
  }

  /// `"USD 12.50"`. For locale-aware output use `intl`'s `NumberFormat` in the
  /// presentation layer — `core` stays locale-agnostic on purpose.
  String get formatted => '$currencyCode $decimalString';

  /// Lossy — for charts and FX only. NEVER feed this back into a Money.
  double get asDouble => minorUnits / _pow10(exponent);

  Money operator +(Money other) {
    _assertSameCurrency(other);
    return Money(minorUnits + other.minorUnits, currencyCode);
  }

  Money operator -(Money other) {
    _assertSameCurrency(other);
    return Money(minorUnits - other.minorUnits, currencyCode);
  }

  Money operator -() => Money(-minorUnits, currencyCode);

  /// Scales by [factor], rounding half-away-from-zero. Used by FX conversion.
  Money scaled(double factor) {
    if (!factor.isFinite) {
      throw ArgumentError.value(factor, 'factor', 'must be finite');
    }
    final raw = minorUnits * factor;
    return Money(raw.abs().round() * (raw.isNegative ? -1 : 1), currencyCode);
  }

  /// Ratio against [other] (0.0–∞). Returns `null` when [other] is zero —
  /// callers must handle "no budget set" rather than divide by zero.
  double? ratioTo(Money other) {
    _assertSameCurrency(other);
    if (other.minorUnits == 0) return null;
    return minorUnits / other.minorUnits;
  }

  bool operator <(Money o) => _cmp(o) < 0;
  bool operator <=(Money o) => _cmp(o) <= 0;
  bool operator >(Money o) => _cmp(o) > 0;
  bool operator >=(Money o) => _cmp(o) >= 0;

  int _cmp(Money other) {
    _assertSameCurrency(other);
    return minorUnits.compareTo(other.minorUnits);
  }

  void _assertSameCurrency(Money other) {
    if (other.currencyCode != currencyCode) {
      // Programmer error (ARCHITECTURE §2.6 category B): summing mixed
      // currencies is meaningless. Convert first via CurrencyService.
      throw StateError(
        'Currency mismatch: $currencyCode vs ${other.currencyCode}. '
        'Convert to a common base with CurrencyService before arithmetic.',
      );
    }
  }

  static int _pow10(int e) {
    var r = 1;
    for (var i = 0; i < e; i++) {
      r *= 10;
    }
    return r;
  }

  /// Sums [items], all of which must share [currencyCode].
  static Money sum(Iterable<Money> items, String currencyCode) {
    var total = Money.zero(currencyCode);
    for (final m in items) {
      total += m;
    }
    return total;
  }

  @override
  int compareTo(Money other) => _cmp(other);

  @override
  bool operator ==(Object other) =>
      other is Money &&
      other.minorUnits == minorUnits &&
      other.currencyCode == currencyCode;

  @override
  int get hashCode => Object.hash(minorUnits, currencyCode);

  @override
  String toString() => 'Money($decimalString $currencyCode)';
}
