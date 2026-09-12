/// Hive typeId 4 — one-tap template for a frequent purchase
/// ("Morning coffee, 3.50 EUR, Food").
library;

import '../../core/utils/money.dart';

class QuickTemplate {
  QuickTemplate({
    required this.id,
    required this.label,
    required this.amountMinor,
    required this.currencyCode,
    required this.categoryId,
    this.iconCodePoint,
    this.useCount = 0,
    this.lastUsedAt,
  });

  final String id;
  final String label;
  final int amountMinor;
  final String currencyCode;
  final String categoryId;
  final int? iconCodePoint;

  /// Drives most-used ordering in the quick-add sheet, so the sheet gets better
  /// the more it is used.
  final int useCount;

  final DateTime? lastUsedAt;

  Money get amount => Money(amountMinor, currencyCode);

  QuickTemplate markUsed(DateTime now) => copyWith(
        useCount: useCount + 1,
        lastUsedAt: now,
      );

  QuickTemplate copyWith({
    String? label,
    int? amountMinor,
    String? currencyCode,
    String? categoryId,
    int? iconCodePoint,
    int? useCount,
    DateTime? lastUsedAt,
  }) {
    return QuickTemplate(
      id: id,
      label: label ?? this.label,
      amountMinor: amountMinor ?? this.amountMinor,
      currencyCode: currencyCode ?? this.currencyCode,
      categoryId: categoryId ?? this.categoryId,
      iconCodePoint: iconCodePoint ?? this.iconCodePoint,
      useCount: useCount ?? this.useCount,
      lastUsedAt: lastUsedAt ?? this.lastUsedAt,
    );
  }

  @override
  String toString() => 'QuickTemplate($id, $label, used=$useCount)';
}
