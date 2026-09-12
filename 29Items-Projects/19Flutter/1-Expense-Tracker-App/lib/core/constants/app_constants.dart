/// App-wide constants and tunables that are not build-time configuration.
/// Build-time config lives in `core/config/env.dart`.
library;

abstract final class AppConstants {
  static const String appName = 'Expense Tracker';

  /// Current Hive schema version. Bump when adding a migration step and add the
  /// matching `migrations/000N_*.md` document.
  static const int schemaVersion = 1;

  static const String defaultCurrency = 'USD';

  /// Fraction of a budget at which the user is warned (0.0–1.0).
  static const double defaultBudgetAlertThreshold = 0.8;

  /// Safety valve for recurring catch-up: if a single recurrence would generate
  /// more than this many instances in one pass, clamp and log a warning rather
  /// than inserting thousands of rows. 400 ≈ daily for 13 months.
  static const int maxRecurringCatchUp = 400;

  /// FX fetch timeout. The UI must never block on the network.
  static const Duration fxTimeout = Duration(seconds: 5);

  /// Age at which cached FX rates are considered stale and a banner is shown.
  static const Duration fxStaleAfter = Duration(hours: 24);

  /// Record count above which month-partitioned reads kick in
  /// (ARCHITECTURE §2.4, axis 1).
  static const int partitionThreshold = 20000;

  static const int notificationIdBudgetAlert = 1001;
  static const int notificationIdMonthlyRollover = 1002;

  /// Seeded on first run. Ids are stable so budgets/templates can reference them.
  static const List<({String id, String name, int icon, int color})> builtInCategories = [
    (id: 'cat_food', name: 'Food', icon: 0xe56c, color: 0xFF4E79A7),
    (id: 'cat_transport', name: 'Transport', icon: 0xe1d5, color: 0xFFF28E2B),
    (id: 'cat_entertainment', name: 'Entertainment', icon: 0xe01d, color: 0xFF59A14F),
    (id: 'cat_bills', name: 'Bills', icon: 0xe0b0, color: 0xFFE15759),
    (id: 'cat_health', name: 'Health', icon: 0xe1c6, color: 0xFF76B7B2),
    (id: 'cat_other', name: 'Other', icon: 0xe5d3, color: 0xFF9C755F),
  ];
}
