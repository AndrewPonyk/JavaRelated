/// Hive typeId 3 (+ typeId 7 for [RecurrenceFrequency]).
///
/// A template that generates real [Expense] rows over time. The generation
/// itself lives in `domain/services/recurring_service.dart` — this file is data
/// only.
library;

import '../../core/utils/date_range.dart';
import '../../core/utils/money.dart';

/// WARNING: **never reorder these values.** Hive stores the enum *index*, so
/// reordering silently rewrites the meaning of every existing row
/// (docs/TECH-NOTES.md §3.6 "Hive"). Append new values at the end only.
enum RecurrenceFrequency {
  daily,
  weekly,
  biweekly,
  monthly,
  yearly;

  /// Advances [from] by exactly one step of this frequency.
  ///
  /// Monthly/yearly use clamped calendar arithmetic (Jan 31 + 1 month → Feb 28),
  /// not `Duration`, so DST and month lengths behave correctly.
  DateTime next(DateTime from) => switch (this) {
        RecurrenceFrequency.daily => DateTime(from.year, from.month, from.day + 1),
        RecurrenceFrequency.weekly => DateTime(from.year, from.month, from.day + 7),
        RecurrenceFrequency.biweekly => DateTime(from.year, from.month, from.day + 14),
        RecurrenceFrequency.monthly => addMonthsClamped(from, 1),
        RecurrenceFrequency.yearly => addMonthsClamped(from, 12),
      };

  String get label => switch (this) {
        RecurrenceFrequency.daily => 'Daily',
        RecurrenceFrequency.weekly => 'Weekly',
        RecurrenceFrequency.biweekly => 'Every 2 weeks',
        RecurrenceFrequency.monthly => 'Monthly',
        RecurrenceFrequency.yearly => 'Yearly',
      };
}

class RecurringExpense {
  RecurringExpense({
    required this.id,
    required this.amountMinor,
    required this.currencyCode,
    required this.categoryId,
    required this.frequency,
    required this.startDate,
    this.note,
    this.endDate,
    this.lastGeneratedDate,
    this.isActive = true,
  });

  final String id;
  final int amountMinor;
  final String currencyCode;
  final String categoryId;
  final RecurrenceFrequency frequency;

  /// First occurrence. The first generated expense lands ON this date.
  final DateTime startDate;

  final String? note;

  /// Inclusive last date; `null` → indefinite.
  final DateTime? endDate;

  /// **Watermark.** The date of the most recently generated instance. This is
  /// what makes materialisation idempotent — see ARCHITECTURE §2.3 "Recurring
  /// materialisation". `null` means nothing generated yet.
  final DateTime? lastGeneratedDate;

  /// Paused recurrences keep their watermark so resuming does not backfill.
  final bool isActive;

  Money get amount => Money(amountMinor, currencyCode);

  bool isExpired(DateTime now) => endDate != null && now.isAfter(endDate!);

  RecurringExpense copyWith({
    int? amountMinor,
    String? currencyCode,
    String? categoryId,
    RecurrenceFrequency? frequency,
    DateTime? startDate,
    String? note,
    DateTime? endDate,
    DateTime? lastGeneratedDate,
    bool? isActive,
  }) {
    return RecurringExpense(
      id: id,
      amountMinor: amountMinor ?? this.amountMinor,
      currencyCode: currencyCode ?? this.currencyCode,
      categoryId: categoryId ?? this.categoryId,
      frequency: frequency ?? this.frequency,
      startDate: startDate ?? this.startDate,
      note: note ?? this.note,
      endDate: endDate ?? this.endDate,
      lastGeneratedDate: lastGeneratedDate ?? this.lastGeneratedDate,
      isActive: isActive ?? this.isActive,
    );
  }

  @override
  String toString() =>
      'RecurringExpense($id, ${frequency.name}, watermark=$lastGeneratedDate)';
}
