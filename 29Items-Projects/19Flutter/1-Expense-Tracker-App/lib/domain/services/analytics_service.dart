/// Category/day/week spending aggregation, in the user's base currency. Pure
/// business logic — no Flutter, no Hive imports (see docs/PROJECT-PLAN.md
/// §1.3(b)). Feeds `CategoryPieChart` and `WeeklyBarChart`.
library;

import '../../core/utils/date_range.dart';
import '../../core/utils/money.dart';
import '../../core/utils/result.dart';
import '../../data/repositories/expense_repository.dart';
import '../../data/repositories/settings_repository.dart';
import 'currency_service.dart';

class CategoryTotal {
  const CategoryTotal({required this.categoryId, required this.total, required this.count});

  final String categoryId;
  final Money total;
  final int count;
}

class BucketTotal {
  const BucketTotal({required this.range, required this.total});

  final DateRange range;
  final Money total;
}

class AnalyticsService {
  AnalyticsService(this._expenses, this._settings, this._currency);

  final ExpenseRepository _expenses;
  final SettingsRepository _settings;
  final CurrencyService _currency;

  String get _base => _settings.current().baseCurrency;

  /// Highest total first. An expense whose currency has no cached rate against
  /// the base is skipped from the total but never lost from the underlying
  /// data — it simply won't appear in this particular breakdown until a rate
  /// is available (see ARCHITECTURE §2.6).
  List<CategoryTotal> byCategory(DateRange range) {
    final byId = <String, List<Money>>{};
    for (final expense in _expenses.byDateRange(range)) {
      final converted = _currency.convert(expense.amount, _base);
      if (converted is Ok<Money>) {
        byId.putIfAbsent(expense.categoryId, () => <Money>[]).add(converted.value);
      }
    }
    final totals = byId.entries
        .map((entry) => CategoryTotal(
              categoryId: entry.key,
              total: Money.sum(entry.value, _base),
              count: entry.value.length,
            ))
        .toList()
      ..sort((a, b) => b.total.compareTo(a.total));
    return totals;
  }

  List<BucketTotal> byDay(DateRange range) => _bucket(range.splitIntoDays());

  List<BucketTotal> byWeek(DateRange range, {int weekStartsOn = DateTime.monday}) =>
      _bucket(range.splitIntoWeeks(weekStartsOn: weekStartsOn));

  List<BucketTotal> byMonth({required DateTime anchorMonth, int count = 6}) {
    final buckets = <DateRange>[];
    for (var i = count - 1; i >= 0; i--) {
      final month = addMonthsClamped(monthKey(anchorMonth), -i);
      buckets.add(DateRange.month(month));
    }
    return _bucket(buckets);
  }

  Money totalFor(DateRange range) {
    final converted = _expenses
        .byDateRange(range)
        .map((e) => _currency.convert(e.amount, _base))
        .whereType<Ok<Money>>()
        .map((ok) => ok.value);
    return Money.sum(converted, _base);
  }

  List<BucketTotal> _bucket(List<DateRange> buckets) =>
      buckets.map((range) => BucketTotal(range: range, total: totalFor(range))).toList(growable: false);
}
