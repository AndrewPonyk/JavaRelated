# Personal Expense Tracker

A local-first, privacy-focused personal expense tracker built with Flutter and Dart. Designed for Android and iOS, it runs completely offline with zero remote backend, network database, or user accounts.

---

## 🚀 Key Capabilities

1. **Log Daily Expenses**: Users can record purchases with an amount, currency code, category, calendar date, and an optional note. All monetary figures are stored in integer minor units to eliminate floating-point rounding errors.
2. **Edit and Delete Expenses**: Users can tap any expense to update its details or swipe to delete it from the month view. Deletions immediately update the list, budget progress, and analytics.
3. **Search and Filter Expenses**: Users can search transactions by note text, amount, or category name in real time. Horizontal filter chips allow instant filtering by individual spending categories.
4. **Quick-Add Templates**: Frequent purchases can be saved as templates to re-log common expenses with a single tap. Users can manage this list by swiping or tapping a trash icon to remove obsolete templates.
5. **Set Overall & Category Budgets**: Users can set monthly spending limits for the whole month or allocate specific limits to individual categories. The app tracks spent amounts, remaining funds, and percentage used in real time.
6. **Visual Budget Warnings & Alerts**: Progress bars indicate whether spending is safe, nearing the limit, or over budget using distinct text labels and icons for color-blind accessibility. The app sends local push notifications when a budget crosses its alert threshold (e.g., 80%) or is exceeded.
7. **Copy Budgets from Previous Month**: Users can duplicate all budget configurations from the previous month into the current month with one tap. This removes the need to manually re-enter recurring budget limits.
8. **Category Spending Breakdown**: An interactive pie chart breaks down spending by category in the user's base currency. Categories are automatically sorted by highest spending first.
9. **Weekly and Monthly Trend Analytics**: Users can toggle between a weekly bar chart for the current month and a 6-month historical spending trend. All historical figures are converted into the base currency for meaningful comparisons.
10. **Automate Recurring Bills & Subscriptions**: Users can set up recurring expenses with daily, weekly, biweekly, monthly, or yearly intervals. An automated engine catches up and logs any due expenses every time the app opens.
11. **Manage Recurring Rules**: Users can pause, resume, or delete recurring rules without losing already-generated historical expenses. Monthly recurrences safely clamp calendar dates (e.g., Jan 31 to Feb 28) to prevent date drift.
12. **Multi-Currency Logging**: Users can log expenses in different currencies while designating a single base currency for reports. A warning banner alerts users if any foreign exchange rates in use are older than 24 hours.
13. **Manual & Live Exchange Rates**: Users can refresh live exchange rates from a public API or manually enter custom conversion rates for offline use. Manual rates take priority over automated fetches to support custom traveler rates.
14. **Export Data to CSV**: Users can export their complete expense history into an RFC-4180-compliant CSV file. The file is shared directly through the operating system's native share sheet (email, messaging, files, etc.).
15. **Custom Category Management**: Users can create new spending categories and delete custom ones they no longer need. The app prevents deleting any category that is currently assigned to existing expenses.
16. **Theme & Preference Customization**: Users can choose between Light, Dark, or System Material 3 themes and set the first day of the week to Sunday or Monday. The default budget notification threshold slider can be adjusted between 50% and 100%.
17. **Monthly Rollover Reminders**: The app can deliver a local reminder notification at the start of each month prompting users to review and set up their budget.
18. **Complete Data Erase**: Users can permanently erase all stored expenses, budgets, categories, templates, and recurring rules. A confirmation dialog prevents accidental data wipes.
19. **100% Offline & Private Operation**: All data is stored locally in device-sandboxed Hive NoSQL storage with no user accounts, remote databases, or cloud sync. The application operates with full functionality in airplane mode.

---

## 🏛️ Architecture & Design

The application follows a strict **Layered Local-First Monolith** architecture:

```
presentation ──▶ domain ──▶ (plain data / core)
     │                          ▲
     └──────────▶ data ─────────┘
```

* **`presentation/`**: Flutter widgets, Material 3 theming, navigation, and `ChangeNotifier`-based Providers.
* **`domain/`**: Pure, framework-free Dart business logic (`BudgetService`, `AnalyticsService`, `RecurringService`, `CurrencyService`, `NotificationService`).
* **`data/`**: Schemaless Hive NoSQL boxes, hand-written `TypeAdapter`s, repositories, and schema version migrations (`HiveMigrator`).
* **`core/`**: Integer minor-unit `Money` representation, typed `Result<T>` / `Failure` hierarchy, logging with release-mode redaction, and theme definitions.

For in-depth architectural and technical design documentation, see:
* [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
* [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md)
* [docs/TECH-NOTES.md](docs/TECH-NOTES.md)

---

## 🛠️ Tech Stack & Key Libraries

* **Framework**: Flutter 3.35+ / Dart SDK ^3.9.2
* **State Management**: [`provider: ^6.1.2`](https://pub.dev/packages/provider)
* **Local Persistence**: [`hive: ^2.2.3`](https://pub.dev/packages/hive) & [`hive_flutter: ^1.1.0`](https://pub.dev/packages/hive_flutter)
* **Visualizations**: [`fl_chart: ^0.69.2`](https://pub.dev/packages/fl_chart)
* **Local Notifications**: [`flutter_local_notifications: ^18.0.1`](https://pub.dev/packages/flutter_local_notifications) & [`timezone: ^0.9.4`](https://pub.dev/packages/timezone)
* **Export & Sharing**: [`csv: ^6.0.0`](https://pub.dev/packages/csv) & [`share_plus: ^10.1.4`](https://pub.dev/packages/share_plus)
* **Identifiers & Network**: [`uuid: ^4.5.1`](https://pub.dev/packages/uuid) & [`http: ^1.2.2`](https://pub.dev/packages/http) (for FX rates)

---

## 🧪 Testing & Verification

The repository includes a comprehensive test suite covering mathematical accuracy, database operations, accessibility, and end-to-end user flows:

```bash
# Run the automated unit and widget test suite
flutter test

# Run strict static analysis
flutter analyze --fatal-infos --fatal-warnings

# Run integration tests (device/emulator)
flutter test integration_test/app_test.dart
```

All tests execute against real, temporary Hive disk storage (no database mocking).
