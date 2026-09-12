/// Hive typeId 0 — see `core/constants/hive_boxes.dart` (frozen registry) and
/// `migrations/0001_initial_schema.md` (field index map).
///
/// No `@HiveType` annotation because adapters are hand-written in
/// `data/adapters/hive_adapters.dart` — PROJECT-PLAN §1.3(a) explains why.
library;

import '../../core/utils/money.dart';

class Expense {
  Expense({
    required this.id,
    required this.amountMinor,
    required this.currencyCode,
    required this.categoryId,
    required this.date,
    required this.createdAt,
    this.note,
    this.recurringId,
  });

  /// UUID v4. Also the Hive key, so lookups are O(1) and stable across edits.
  final String id;

  /// Minor units (cents). NEVER a double — docs/TECH-NOTES.md §3.6.
  final int amountMinor;

  /// ISO-4217 of the amount **as entered**. Kept forever so a historical record
  /// stays truthful even after FX rates move.
  final String currencyCode;

  final String categoryId;

  /// Local date the spend happened (day semantics; time component unused).
  final DateTime date;

  final DateTime createdAt;

  final String? note;

  /// Set when this row was materialised from a [RecurringExpense]. Lets the user
  /// see "this came from a subscription" and lets us delete generated rows if a
  /// recurrence is removed.
  final String? recurringId;

  Money get amount => Money(amountMinor, currencyCode);

  bool get isGenerated => recurringId != null;

  Expense copyWith({
    int? amountMinor,
    String? currencyCode,
    String? categoryId,
    DateTime? date,
    String? note,
    String? recurringId,
  }) {
    return Expense(
      id: id,
      amountMinor: amountMinor ?? this.amountMinor,
      currencyCode: currencyCode ?? this.currencyCode,
      categoryId: categoryId ?? this.categoryId,
      date: date ?? this.date,
      createdAt: createdAt,
      note: note ?? this.note,
      recurringId: recurringId ?? this.recurringId,
    );
  }

  /// Row for CSV export. Header lives in `CsvExportService` so the two cannot
  /// drift apart silently.
  List<Object?> toCsvRow({String? categoryName, double? rateToBase, String? baseCode}) {
    return <Object?>[
      date.toIso8601String().substring(0, 10),
      categoryName ?? categoryId,
      amount.decimalString,
      currencyCode,
      rateToBase?.toString() ?? '',
      baseCode ?? '',
      rateToBase == null ? '' : amount.scaled(rateToBase).decimalString,
      note ?? '',
      isGenerated ? 'recurring' : 'manual',
    ];
  }

  @override
  String toString() => 'Expense($id, $amountMinor $currencyCode, $categoryId)';
}
