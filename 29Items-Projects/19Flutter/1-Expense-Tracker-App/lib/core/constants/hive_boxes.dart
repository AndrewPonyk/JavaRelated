/// **FROZEN REGISTRY — APPEND ONLY.**
///
/// Hive `typeId`s and box names are permanent parts of the on-disk format.
/// Reusing a typeId for a different class silently deserialises garbage into the
/// wrong type; renaming a box orphans the user's data. Neither failure throws —
/// they just produce wrong numbers, which in an expense tracker is worse.
///
/// Rules:
///  1. Never change an existing value here.
///  2. Never reuse a retired typeId. Add the next free number instead.
///  3. Every change here needs a matching `migrations/000N_*.md` entry.
///
/// Mirrors: migrations/0001_initial_schema.md
library;

/// Hive box names.
abstract final class HiveBoxes {
  static const String expenses = 'expenses';
  static const String categories = 'categories';
  static const String budgets = 'budgets';
  static const String recurring = 'recurring_expenses';
  static const String templates = 'quick_templates';
  static const String settings = 'app_settings';
  static const String rates = 'currency_rates';

  /// Boxes opened eagerly before the first frame. Keep this list minimal —
  /// cold-start budget is 400ms (ARCHITECTURE §2.4).
  static const List<String> eager = <String>[settings, categories];

  static const List<String> all = <String>[
    expenses,
    categories,
    budgets,
    recurring,
    templates,
    settings,
    rates,
  ];
}

/// Hive adapter type ids. APPEND ONLY — see file header.
abstract final class HiveTypeIds {
  static const int expense = 0;
  static const int expenseCategory = 1;
  static const int budget = 2;
  static const int recurringExpense = 3;
  static const int quickTemplate = 4;
  static const int appSettings = 5;
  static const int currencyRate = 6;
  static const int recurrenceFrequency = 7;

  /// Next id to use when adding a model. Bump it in the same commit.
  static const int nextFree = 8;
}

/// Keys inside the single-entry `settings` box.
abstract final class SettingsKeys {
  static const String singleton = 'current';
  static const String schemaVersion = 'schema_version';
}
