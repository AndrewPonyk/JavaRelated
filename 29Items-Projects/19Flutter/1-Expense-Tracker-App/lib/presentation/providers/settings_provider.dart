/// Drives the settings screen: app preferences, categories, currency, live FX
/// refresh, notification permission, and CSV export/erase-all-data.
///
/// [CsvExportService] only builds the CSV string (pure Dart, unit-testable —
/// see its doc comment); writing the temp file and invoking the share sheet
/// need `path_provider`/`share_plus`, which are Flutter plugins, so that half
/// of the export flow lives here rather than in `domain/`.
///
/// [HiveService] is injected only for [eraseAllData]: a full wipe is a
/// cross-repository operation with no single natural owner, and
/// `HiveService.deleteAllData()` already exists as the intended entry point
/// for exactly that — every other method on this provider goes through a
/// repository or service, never a box, directly.
library;

import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/error/failures.dart';
import '../../core/utils/result.dart';
import '../../data/datasources/hive_service.dart';
import '../../data/models/app_settings.dart';
import '../../data/models/currency_rate.dart';
import '../../data/models/expense_category.dart';
import '../../data/repositories/currency_repository.dart';
import '../../data/repositories/expense_repository.dart';
import '../../data/repositories/settings_repository.dart';
import '../../domain/services/csv_export_service.dart';
import '../../domain/services/currency_service.dart';
import '../../domain/services/notification_service.dart';

class SettingsProvider extends ChangeNotifier {
  SettingsProvider(
    this._settings,
    this._expenses,
    this._currency,
    this._rates,
    this._notifications,
    this._csvExport,
    this._hive,
  ) {
    _checkRollover();
  }

  final SettingsRepository _settings;
  final ExpenseRepository _expenses;
  final CurrencyService _currency;
  final CurrencyRepository _rates;
  final NotificationService _notifications;
  final CsvExportService _csvExport;
  final HiveService _hive;

  AppSettings get settings => _settings.current();
  List<ExpenseCategory> get categories => _settings.categories();
  List<CurrencyRate> get cachedRates => _rates.all();

  void _checkRollover() {
    _notifications.maybeNotifyMonthlyRollover(
      enabled: settings.monthlyRolloverReminder && settings.notificationsEnabled,
    );
  }

  bool isCurrencyStale(String code) => _currency.isStale(code);

  Future<Failure?> save(AppSettings updated) async {
    final result = await _settings.save(updated);
    if (result case Err<AppSettings>(:final failure)) return failure;
    _checkRollover();
    notifyListeners();
    return null;
  }

  Future<Failure?> setBaseCurrency(String code) async {
    final result = await _settings.setBaseCurrency(code);
    if (result case Err<AppSettings>(:final failure)) return failure;
    notifyListeners();
    return null;
  }

  Future<Failure?> addCategory(String name) async {
    final result = await _settings.addCategory(name: name);
    if (result case Err<ExpenseCategory>(:final failure)) return failure;
    notifyListeners();
    return null;
  }

  Future<Failure?> deleteCategory(String id) async {
    final result = await _settings.deleteCategory(id);
    if (result case Err<void>(:final failure)) return failure;
    notifyListeners();
    return null;
  }

  /// Refreshes cached FX rates for every currency any expense currently uses.
  /// A no-op when [Env.fxEnabled] is false — see `CurrencyService.refreshRates`.
  Future<Failure?> refreshRates() async {
    final result = await _currency.refreshRates(_expenses.currenciesInUse());
    if (result case Err<int>(:final failure)) return failure;
    notifyListeners();
    return null;
  }

  Future<Failure?> setManualRate({required String code, required double rateToBase}) async {
    final result = await _rates.setManual(
      code: code,
      baseCode: settings.baseCurrency,
      rateToBase: rateToBase,
    );
    if (result case Err<CurrencyRate>(:final failure)) return failure;
    notifyListeners();
    return null;
  }

  Future<bool> requestNotificationPermission() => _notifications.requestPermission();

  /// Builds the CSV, writes it to a temp file, and opens the OS share sheet.
  /// Callers should check `AppConfig.exportSupported` first (disabled on Web).
  Future<Failure?> exportCsv() async {
    try {
      final csv = _csvExport.build(categories: _settings.categoryMap());
      final dir = await getTemporaryDirectory();
      final stamp = DateTime.now().toIso8601String().replaceAll(RegExp(r'[:.]'), '-');
      final file = File('${dir.path}/expenses_$stamp.csv');
      await file.writeAsString(csv);
      await Share.shareXFiles([XFile(file.path)]);
      return null;
    } catch (error, stack) {
      return ExportFailure('Could not export expenses', cause: error, stackTrace: stack);
    }
  }

  /// Destructive — the calling widget MUST confirm with the user before
  /// invoking this (see `core/README` on risky actions). Wipes every box.
  Future<void> eraseAllData() async {
    await _hive.deleteAllData();
    notifyListeners();
  }
}
