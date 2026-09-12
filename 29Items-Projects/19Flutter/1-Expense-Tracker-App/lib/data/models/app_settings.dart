/// Hive typeId 5 — single-row settings object, stored under
/// `SettingsKeys.singleton` in the `app_settings` box.
library;

import '../../core/constants/app_constants.dart';

class AppSettings {
  AppSettings({
    this.baseCurrency = AppConstants.defaultCurrency,
    this.budgetAlertThreshold = AppConstants.defaultBudgetAlertThreshold,
    this.notificationsEnabled = true,
    this.monthlyRolloverReminder = true,
    this.themeModeIndex = 0,
    this.weekStartsOnMonday = true,
    this.schemaVersion = AppConstants.schemaVersion,
    this.categoriesSeeded = false,
    this.lastRecurringRunAt,
  });

  /// All aggregation and budget comparison happens in this currency.
  final String baseCurrency;

  /// 0.0–1.0.
  final double budgetAlertThreshold;

  final bool notificationsEnabled;
  final bool monthlyRolloverReminder;

  /// Index into [ThemeMode]: 0 system, 1 light, 2 dark. Stored as int so this
  /// model needs no Flutter import.
  final int themeModeIndex;

  /// Affects weekly chart bucketing — genuinely locale-dependent.
  final bool weekStartsOnMonday;

  /// Written by [HiveMigrator]; do not edit by hand.
  final int schemaVersion;

  /// Guards the idempotent built-in category seed on first run.
  final bool categoriesSeeded;

  /// Diagnostic only — when recurring materialisation last ran.
  final DateTime? lastRecurringRunAt;

  AppSettings copyWith({
    String? baseCurrency,
    double? budgetAlertThreshold,
    bool? notificationsEnabled,
    bool? monthlyRolloverReminder,
    int? themeModeIndex,
    bool? weekStartsOnMonday,
    int? schemaVersion,
    bool? categoriesSeeded,
    DateTime? lastRecurringRunAt,
  }) {
    return AppSettings(
      baseCurrency: baseCurrency ?? this.baseCurrency,
      budgetAlertThreshold: budgetAlertThreshold ?? this.budgetAlertThreshold,
      notificationsEnabled: notificationsEnabled ?? this.notificationsEnabled,
      monthlyRolloverReminder: monthlyRolloverReminder ?? this.monthlyRolloverReminder,
      themeModeIndex: themeModeIndex ?? this.themeModeIndex,
      weekStartsOnMonday: weekStartsOnMonday ?? this.weekStartsOnMonday,
      schemaVersion: schemaVersion ?? this.schemaVersion,
      categoriesSeeded: categoriesSeeded ?? this.categoriesSeeded,
      lastRecurringRunAt: lastRecurringRunAt ?? this.lastRecurringRunAt,
    );
  }

  @override
  String toString() =>
      'AppSettings(base=$baseCurrency, threshold=$budgetAlertThreshold, v=$schemaVersion)';
}
