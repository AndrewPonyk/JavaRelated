/// Hive typeId 2 — a monthly spending limit.
///
/// A budget with `categoryId == null` is the **overall** monthly budget;
/// otherwise it caps a single category. Both can coexist for the same month.
library;

import '../../core/utils/date_range.dart';
import '../../core/utils/money.dart';

class Budget {
  Budget({
    required this.id,
    required DateTime month,
    required this.limitMinor,
    required this.currencyCode,
    this.categoryId,
    this.alertThreshold = 0.8,
  }) : month = monthKey(month);

  final String id;

  /// Always normalised to the first of the month at local midnight, so
  /// "March 2027" has exactly one representation regardless of the date picked.
  final DateTime month;

  final int limitMinor;

  /// Budgets are always denominated in the user's base currency; expenses in
  /// other currencies are converted before comparison.
  final String currencyCode;

  /// `null` → overall budget for the month.
  final String? categoryId;

  /// Fraction (0.0–1.0) at which a notification fires. Per-budget so a user can
  /// be strict about one category and relaxed about another.
  final double alertThreshold;

  Money get limit => Money(limitMinor, currencyCode);

  bool get isOverall => categoryId == null;

  /// Deterministic key so a month+category pair cannot get two budget rows.
  static String keyFor(DateTime month, String? categoryId) {
    final m = monthKey(month);
    final ym = '${m.year}-${m.month.toString().padLeft(2, '0')}';
    return 'budget_${ym}_${categoryId ?? 'overall'}';
  }

  Budget copyWith({int? limitMinor, double? alertThreshold, String? currencyCode}) {
    return Budget(
      id: id,
      month: month,
      limitMinor: limitMinor ?? this.limitMinor,
      currencyCode: currencyCode ?? this.currencyCode,
      categoryId: categoryId,
      alertThreshold: alertThreshold ?? this.alertThreshold,
    );
  }

  @override
  String toString() =>
      'Budget($id, ${month.year}-${month.month}, $limitMinor $currencyCode, cat=$categoryId)';
}
