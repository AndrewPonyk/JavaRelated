import 'package:flutter_test/flutter_test.dart';

import 'package:expense_tracker/core/utils/money.dart';

void main() {
  group('Money.parse / decimalString round trip', () {
    test('plain decimal', () {
      final m = Money.parse('12.50', 'USD');
      expect(m.minorUnits, 1250);
      expect(m.decimalString, '12.50');
      expect(m.formatted, 'USD 12.50');
    });

    test('integer input pads to full precision', () {
      expect(Money.parse('12', 'USD').decimalString, '12.00');
    });

    test('grouped thousands with comma separators', () {
      expect(Money.parse('1,234.56', 'USD').minorUnits, 123456);
    });

    test('comma as decimal separator', () {
      expect(Money.parse('12,50', 'EUR').minorUnits, 1250);
    });

    test('leading + is accepted', () {
      expect(Money.parse('+5.00', 'USD').minorUnits, 500);
    });

    test('rounds half-up on excess fractional digits', () {
      expect(Money.parse('1.005', 'USD').minorUnits, 101);
      expect(Money.parse('1.004', 'USD').minorUnits, 100);
    });

    test('throws MoneyFormatException on garbage input', () {
      expect(() => Money.parse('abc', 'USD'), throwsA(isA<MoneyFormatException>()));
      expect(() => Money.parse('', 'USD'), throwsA(isA<MoneyFormatException>()));
      expect(() => Money.parse('.', 'USD'), throwsA(isA<MoneyFormatException>()));
    });

    test('rejects a non-ISO-4217 currency code', () {
      expect(() => Money(100, 'US'), throwsArgumentError);
    });
  });

  group('negative amounts', () {
    test('Money.parse accepts a negative amount (refunds/adjustments)', () {
      final m = Money.parse('-4.20', 'USD');
      expect(m.minorUnits, -420);
      expect(m.isNegative, isTrue);
      expect(m.isPositive, isFalse);
      expect(m.decimalString, '-4.20');
    });

    test('isPositive is false for zero and negative amounts', () {
      expect(Money.zero('USD').isPositive, isFalse);
      expect(Money(-1, 'USD').isPositive, isFalse);
      expect(Money(1, 'USD').isPositive, isTrue);
    });
  });

  group('arithmetic precision (the whole reason Money is not a double)', () {
    test('0.1 + 0.2 == 0.3 exactly, unlike IEEE-754 doubles', () {
      final sum = Money.parse('0.10', 'USD') + Money.parse('0.20', 'USD');
      expect(sum, Money.parse('0.30', 'USD'));
      expect(sum.decimalString, '0.30');
    });

    test('unary minus and subtraction', () {
      final a = Money.parse('10.00', 'USD');
      final b = Money.parse('3.50', 'USD');
      expect((a - b).decimalString, '6.50');
      expect((-a).decimalString, '-10.00');
    });

    test('mismatched currencies throw StateError, not a silent wrong answer', () {
      final usd = Money.parse('10.00', 'USD');
      final eur = Money.parse('10.00', 'EUR');
      expect(() => usd + eur, throwsStateError);
      expect(() => usd.ratioTo(eur), throwsStateError);
      expect(() => usd < eur, throwsStateError);
    });

    test('Money.sum totals a list in one currency', () {
      final total = Money.sum(
        [Money.parse('1.00', 'USD'), Money.parse('2.50', 'USD'), Money.parse('0.25', 'USD')],
        'USD',
      );
      expect(total.decimalString, '3.75');
    });
  });

  group('scaled (FX conversion)', () {
    test('rounds half-away-from-zero', () {
      // 1 minor unit * 2.5 == 2.5 exactly representable in double -> rounds
      // away from zero to 3 (not the banker's-rounding 2). 1.005 is NOT used
      // here: it has no exact binary representation, so 100 * 1.005 evaluates
      // to 100.49999999999999 and rounds down to 100, not 101 — that would
      // be a floating-point-precision test bug, not a real assertion.
      expect(Money(1, 'USD').scaled(2.5).minorUnits, 3);
      expect(Money(-1, 'USD').scaled(2.5).minorUnits, -3);
    });

    test('handles negative factors and amounts symmetrically', () {
      expect(Money(100, 'USD').scaled(-1.5).minorUnits, -150);
      expect(Money(-100, 'USD').scaled(1.5).minorUnits, -150);
    });

    test('rejects a non-finite factor', () {
      expect(() => Money(100, 'USD').scaled(double.nan), throwsArgumentError);
      expect(() => Money(100, 'USD').scaled(double.infinity), throwsArgumentError);
    });
  });

  group('ratioTo', () {
    test('returns null instead of dividing by zero', () {
      expect(Money(100, 'USD').ratioTo(Money.zero('USD')), isNull);
    });

    test('computes a plain ratio otherwise', () {
      expect(Money(50, 'USD').ratioTo(Money(200, 'USD')), 0.25);
    });
  });

  group('non-2-decimal currencies (travellers use these)', () {
    test('JPY has zero decimal places', () {
      final m = Money.parse('1500', 'JPY');
      expect(m.exponent, 0);
      expect(m.minorUnits, 1500);
      expect(m.decimalString, '1500');
    });

    test('KWD/JOD have three decimal places and round-trip correctly', () {
      final kwd = Money.parse('12.345', 'KWD');
      expect(kwd.exponent, 3);
      expect(kwd.minorUnits, 12345);
      expect(kwd.decimalString, '12.345');
    });
  });

  group('comparisons and ordering', () {
    test('operators agree with compareTo', () {
      final a = Money(100, 'USD');
      final b = Money(200, 'USD');
      expect(a < b, isTrue);
      expect(b > a, isTrue);
      expect(a <= a, isTrue);
      expect(a.compareTo(b), lessThan(0));
    });

    test('equality compares both minorUnits and currencyCode', () {
      expect(Money(100, 'USD'), Money(100, 'USD'));
      expect(Money(100, 'USD'), isNot(Money(100, 'EUR')));
      expect(Money(100, 'USD'), isNot(Money(200, 'USD')));
    });
  });

  test('overflow-scale sanity: very large amounts still round-trip', () {
    final big = Money.parse('999999999.99', 'USD');
    expect(big.decimalString, '999999999.99');
  });
}
