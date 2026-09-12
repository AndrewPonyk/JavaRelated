/// Builds the CSV export as a pure string. Pure business logic — no Flutter,
/// no Hive imports (see docs/PROJECT-PLAN.md §1.3(b)). Writing the string to a
/// file and invoking the share sheet are presentation-layer concerns (they
/// need `path_provider`/`share_plus`), kept out of this service on purpose so
/// it stays trivially unit-testable with no temp files.
library;

import 'package:csv/csv.dart';

import '../../core/utils/date_range.dart';
import '../../data/models/expense_category.dart';
import '../../data/repositories/expense_repository.dart';
import '../../data/repositories/settings_repository.dart';
import 'currency_service.dart';

/// Column order is dictated by `Expense.toCsvRow` — keep the two in sync.
const List<String> csvHeader = <String>[
  'date',
  'category',
  'amount',
  'currency',
  'rate_to_base',
  'base_currency',
  'converted_amount',
  'note',
  'source',
];

class CsvExportService {
  CsvExportService(this._expenses, this._settings, this._currency);

  final ExpenseRepository _expenses;
  final SettingsRepository _settings;
  final CurrencyService _currency;

  /// Builds RFC-4180 CSV text for every expense in [range] (or all expenses,
  /// if [range] is omitted). [categories] resolves ids to display names —
  /// pass `SettingsRepository.categoryMap()`.
  String build({DateRange? range, required Map<String, ExpenseCategory> categories}) {
    final base = _settings.current().baseCurrency;
    final expenses = range == null ? _expenses.all() : _expenses.byDateRange(range);

    final rows = <List<Object?>>[csvHeader];
    for (final expense in expenses) {
      final rateToBase = expense.currencyCode == base
          ? null
          : _currency.cachedRateToBase(expense.currencyCode);
      rows.add(expense.toCsvRow(
        categoryName: categories[expense.categoryId]?.name,
        rateToBase: rateToBase,
        baseCode: rateToBase == null ? null : base,
      ));
    }
    return const ListToCsvConverter().convert(rows);
  }
}
