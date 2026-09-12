/// Drives the analytics screen: category breakdown (pie) and weekly totals
/// (bar), backed entirely by [AnalyticsService]. Read-only — there is nothing
/// here to validate or persist.
library;

import 'package:flutter/foundation.dart';

import '../../core/utils/date_range.dart';
import '../../core/utils/money.dart';
import '../../data/repositories/settings_repository.dart';
import '../../domain/services/analytics_service.dart';
import 'async_state.dart';

class AnalyticsProvider extends ChangeNotifier {
  AnalyticsProvider(this._analytics, this._settings) {
    load();
  }

  final AnalyticsService _analytics;
  final SettingsRepository _settings;

  DateTime _month = DateTime.now();
  AsyncState<List<CategoryTotal>> _byCategory = const AsyncLoading();
  AsyncState<List<BucketTotal>> _byWeek = const AsyncLoading();
  AsyncState<List<BucketTotal>> _byMonth = const AsyncLoading();
  AsyncState<Money> _total = const AsyncLoading();

  DateTime get month => _month;
  DateRange get range => DateRange.month(_month);
  AsyncState<List<CategoryTotal>> get byCategory => _byCategory;
  AsyncState<List<BucketTotal>> get byWeek => _byWeek;
  AsyncState<List<BucketTotal>> get byMonth => _byMonth;
  AsyncState<Money> get total => _total;

  void load({DateTime? month}) {
    if (month != null) _month = month;
    final currentRange = range;
    final weekStartsOn = _settings.current().weekStartsOnMonday
        ? DateTime.monday
        : DateTime.sunday;

    _byCategory = AsyncData(_analytics.byCategory(currentRange));
    _byWeek = AsyncData(_analytics.byWeek(currentRange, weekStartsOn: weekStartsOn));
    _byMonth = AsyncData(_analytics.byMonth(anchorMonth: _month, count: 6));
    _total = AsyncData(_analytics.totalFor(currentRange));
    notifyListeners();
  }

  void goToPreviousMonth() => load(month: addMonthsClamped(_month, -1));

  void goToNextMonth() => load(month: addMonthsClamped(_month, 1));
}
