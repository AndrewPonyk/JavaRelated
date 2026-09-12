# 0001 — Initial Schema

**Status:** shipped · **Migrates to schema version:** 1 · **Step:** `HiveMigrator._v1InitialSeed`

The initial schema. Seeds the three built-in categories (`AppConstants.builtInCategories`) into the
`categories` box and writes the `AppSettings` singleton into the `settings` box, guarded by
`AppSettings.categoriesSeeded` so re-running it is a no-op.

This document is the **source of truth** for typeIds and field indices — it must mirror
`lib/core/constants/hive_boxes.dart` and `lib/data/adapters/hive_adapters.dart` exactly. If those files
and this table ever disagree, the code is what actually runs, but the disagreement itself is a bug:
fix this file in the same commit as any adapter change.

## Box registry

| Box name (constant) | Hive box name | Stores |
|---|---|---|
| `HiveBoxes.expenses` | `expenses` | `Expense` |
| `HiveBoxes.categories` | `categories` | `ExpenseCategory` |
| `HiveBoxes.budgets` | `budgets` | `Budget` |
| `HiveBoxes.recurring` | `recurring_expenses` | `RecurringExpense` |
| `HiveBoxes.templates` | `quick_templates` | `QuickTemplate` |
| `HiveBoxes.settings` | `app_settings` | `AppSettings` (single entry, key `SettingsKeys.singleton`) |
| `HiveBoxes.rates` | `currency_rates` | `CurrencyRate` |

`HiveBoxes.eager = [settings, categories]` — opened before the first frame; the rest are opened by
`hive.ensureOpen(HiveBoxes.all)` (see `lib/main.dart`).

## typeId registry (`HiveTypeIds`)

| typeId | Class | Adapter |
|---|---|---|
| 0 | `Expense` | `ExpenseAdapter` |
| 1 | `ExpenseCategory` | `ExpenseCategoryAdapter` |
| 2 | `Budget` | `BudgetAdapter` |
| 3 | `RecurringExpense` | `RecurringExpenseAdapter` |
| 4 | `QuickTemplate` | `QuickTemplateAdapter` |
| 5 | `AppSettings` | `AppSettingsAdapter` |
| 6 | `CurrencyRate` | `CurrencyRateAdapter` |
| 7 | `RecurrenceFrequency` (enum, stored as index byte) | `RecurrenceFrequencyAdapter` |

`HiveTypeIds.nextFree = 8` — the next id to hand out. Bump it in the same commit as any new model.

## Field maps

Each adapter writes `writeByte(fieldCount)` then `(index, value)` pairs. `read` tolerates a missing
index (defaults) and an unknown index (ignored) — see `hive_adapters.dart` header rules.

### `Expense` — 8 fields (0–7)

| Index | Field | Type | Default on missing |
|---|---|---|---|
| 0 | `id` | `String` | — (required) |
| 1 | `amountMinor` | `int` | — (required) |
| 2 | `currencyCode` | `String` | `AppConstants.defaultCurrency` |
| 3 | `categoryId` | `String` | — (required) |
| 4 | `date` | `DateTime` | — (required) |
| 5 | `createdAt` | `DateTime` | falls back to `date` |
| 6 | `note` | `String?` | `null` |
| 7 | `recurringId` | `String?` | `null` |

### `ExpenseCategory` — 6 fields (0–5)

| Index | Field | Type | Default on missing |
|---|---|---|---|
| 0 | `id` | `String` | — (required) |
| 1 | `name` | `String` | — (required) |
| 2 | `iconCodePoint` | `int` | `0xe5d3` |
| 3 | `colorValue` | `int` | `0xFF9C755F` |
| 4 | `isBuiltIn` | `bool` | `false` |
| 5 | `sortOrder` | `int` | `0` |

### `Budget` — 6 fields (0–5)

| Index | Field | Type | Default on missing |
|---|---|---|---|
| 0 | `id` | `String` | — (required) |
| 1 | `month` | `DateTime` | — (required) |
| 2 | `limitMinor` | `int` | — (required) |
| 3 | `currencyCode` | `String` | `AppConstants.defaultCurrency` |
| 4 | `categoryId` | `String?` | `null` (whole-month budget) |
| 5 | `alertThreshold` | `double` | `AppConstants.defaultBudgetAlertThreshold` |

### `RecurringExpense` — 10 fields (0–9)

| Index | Field | Type | Default on missing |
|---|---|---|---|
| 0 | `id` | `String` | — (required) |
| 1 | `amountMinor` | `int` | — (required) |
| 2 | `currencyCode` | `String` | `AppConstants.defaultCurrency` |
| 3 | `categoryId` | `String` | — (required) |
| 4 | `frequency` | `RecurrenceFrequency` (typeId 7) | `RecurrenceFrequency.monthly` |
| 5 | `startDate` | `DateTime` | — (required) |
| 6 | `note` | `String?` | `null` |
| 7 | `endDate` | `DateTime?` | `null` (never expires) |
| 8 | `lastGeneratedDate` | `DateTime?` | `null` |
| 9 | `isActive` | `bool` | `true` |

### `QuickTemplate` — 8 fields (0–7)

| Index | Field | Type | Default on missing |
|---|---|---|---|
| 0 | `id` | `String` | — (required) |
| 1 | `label` | `String` | — (required) |
| 2 | `amountMinor` | `int` | — (required) |
| 3 | `currencyCode` | `String` | `AppConstants.defaultCurrency` |
| 4 | `categoryId` | `String` | — (required) |
| 5 | `iconCodePoint` | `int?` | `null` (falls back to category icon) |
| 6 | `useCount` | `int` | `0` |
| 7 | `lastUsedAt` | `DateTime?` | `null` |

### `AppSettings` — 9 fields (0–8), single entry keyed `SettingsKeys.singleton`

| Index | Field | Type | Default on missing |
|---|---|---|---|
| 0 | `baseCurrency` | `String` | `AppConstants.defaultCurrency` |
| 1 | `budgetAlertThreshold` | `double` | `AppConstants.defaultBudgetAlertThreshold` |
| 2 | `notificationsEnabled` | `bool` | `true` |
| 3 | `monthlyRolloverReminder` | `bool` | `true` |
| 4 | `themeModeIndex` | `int` | `0` (system) |
| 5 | `weekStartsOnMonday` | `bool` | `true` |
| 6 | `schemaVersion` | `int` | `1` |
| 7 | `categoriesSeeded` | `bool` | `false` |
| 8 | `lastRecurringRunAt` | `DateTime?` | `null` |

### `CurrencyRate` — 5 fields (0–4)

| Index | Field | Type | Default on missing |
|---|---|---|---|
| 0 | `code` | `String` | — (required) |
| 1 | `baseCode` | `String` | `AppConstants.defaultCurrency` |
| 2 | `rateToBase` | `double` | `1.0` |
| 3 | `updatedAt` | `DateTime` | epoch (forces a refresh) |
| 4 | `isManual` | `bool` | `false` |

### `RecurrenceFrequency` — enum-as-byte (typeId 7)

Stored as `obj.index` into `RecurrenceFrequency.values`. On read, an out-of-range index (a downgrade
reading a value added by a newer build) falls back to `RecurrenceFrequency.monthly` rather than
throwing. **Never reorder this enum** — reordering silently changes the meaning of every stored row.

## Seed data (this step)

`AppConstants.builtInCategories`: `cat_food` (Food), `cat_transport` (Transport), `cat_entertainment`
(Entertainment) — written with `isBuiltIn: true` and ascending `sortOrder`.
