# Habit Streak Tracker

[![Flutter CI Pipeline](https://github.com/example/habit-streak-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/example/habit-streak-tracker/actions/workflows/ci.yml)
[![Firebase Distribution](https://github.com/example/habit-streak-tracker/actions/workflows/deploy-firebase.yml/badge.svg)](https://github.com/example/habit-streak-tracker/actions/workflows/deploy-firebase.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A high-performance, offline-first mobile application designed to cultivate daily routines with streak tracking, flexible scheduling, native home screen widgets, calendar heatmap visualizations, and streak freeze protections.

---

## Key Features

- **Relational Habit Tracking (SQLite):** Embedded SQLite database with foreign-key integrity, versioned schema migrations, and high-speed composite indexing.
- **Reactive State Management (Riverpod):** Unidirectional data flow with optimistic UI updates for instant check-in response and rollback handling.
- **Glanceable Home Screen Widgets:** Native Android RemoteViews / Jetpack Glance and iOS 14+ WidgetKit support powered by `home_widget`.
- **Scheduled Local Notifications:** Customizable daily reminders running on native platform alarm managers without requiring backend services.
- **Resilient Streak Engine:** Algorithmic engine supporting daily routines, specific days of the week, weekend skips, and streak freezes for sick/travel days.
- **Activity Heatmap & Statistics:** GitHub-style contribution heatmap and rolling completion statistics.

---

## Application Capabilities

### 1. Habit Management & Customization
1. **Create New Habits**: Add habits with customized titles, input validation (non-empty, max 100 characters), and automatic whitespace trimming.
2. **Color Categorization**: Assign custom accent colors to each habit (Indigo, Emerald, Orange, Pink, Cyan, Purple) for visual identification across cards and charts.
3. **Flexible Frequency Scheduling**: Configure habit recurrence to match real-life routines:
   - **Daily**: Every day of the week.
   - **Weekly**: Periodic targets.
   - **Specific Days**: Custom weekday schedules (e.g., weekdays only; weekends are automatically skipped and will not break streaks).
4. **Custom Daily Reminders**: Select specific times (e.g., `08:00 AM`) with an interactive time picker to receive daily reminder alerts.
5. **Archive Habits**: Deactivate completed or paused habits without deleting their historical records, while automatically descheduling notifications.
6. **Permanent Habit Deletion**: Delete habits with automated cascading cleanup that purges associated completions and freeze records from the database.

### 2. Daily Tracking & Routine Execution
7. **One-Tap Check-In / Undo**: Toggle habit completion for today with instantaneous optimistic UI updates and auto-rollback on database failure.
8. **Real-Time Streak Counting**: Automatically computes current streaks dynamically from SQLite completion logs whenever a habit is checked or unchecked.
9. **Streak Freeze Protection**: Apply a "Streak Freeze" to protect your streak on sick days, travel, or rest days without breaking continuous streak counts.
10. **Pull-to-Refresh**: Refresh all habit lists and statistical aggregations with a single swipe.

### 3. Analytics & Visual Insights
11. **Performance Summary Dashboard**:
    - **Total Habits**: Current count of active routines.
    - **Total Completions**: All-time check-in count across all routines.
    - **Best Active Streak**: Longest continuous streak among active habits.
    - **30-Day Completion Rate**: Rolling percentage of scheduled days successfully completed.
12. **Interactive Calendar Heatmap**: A GitHub-style 90-day activity matrix displaying daily completion intensity with color-coded buckets (empty, light, medium, intense).
13. **Heatmap Date Inspection**: Tap any cell in the 90-day calendar to inspect the exact date and number of habits completed on that day.
14. **Habit Detail Sheet**: Tap any habit card to open a modal inspector showing:
    - Current active streak vs. all-time personal best streak.
    - Habit creation timestamp and frequency details.
    - Direct action button to freeze today's streak.

### 4. Background Services & OS Integrations
15. **Scheduled Local Notifications**: Automatically schedules daily repeating notifications based on each habit's reminder time via platform alarm services (`flutter_local_notifications`).
16. **Notification Lifecycle Sync**: Automatically cancels or reschedules alarms whenever a habit is edited, archived, or deleted.
17. **Home Screen Widget Synchronization**: Packages and synchronizes today's habit states, completion counts, and streak numbers to native home screen widgets on Android (Glance/RemoteViews) and iOS (WidgetKit).
18. **Offline-First Persistence**: Operates 100% offline using an embedded SQLite database with foreign-key enforcement, composite indexes, and versioned migration support (`v1` base tables, `v2` freeze tables).
19. **Cross-Platform Compatibility**: Runs on **Android**, **iOS**, and **Windows/Linux Desktop** (utilizing native SQLite FFI with safe no-op guards for mobile-only background bridges).

---

## Tech Stack & Architecture

- **Language:** Dart 3.9+ / Flutter 3.35+
- **State Management:** Riverpod 2.6 (`AsyncNotifierProvider`)
- **Persistence:** SQLite (`sqflite` with `sqflite_common_ffi` for headless testing)
- **Notifications:** `flutter_local_notifications` + `timezone`
- **Widgets:** `home_widget` (App Groups / SharedPreferences bridge)
- **Architecture Pattern:** Feature-First Clean Layered Architecture
- **CI/CD & Delivery:** GitHub Actions + Firebase App Distribution

```text
lib/
├── core/
│   ├── constants/           # Global keys, DB schema constants
│   ├── database/            # SQLite connection lifecycle & versioned migrations
│   ├── errors/              # Typed Failures and Exceptions
│   ├── services/            # Local Notifications & HomeWidget bridges
│   ├── theme/               # Material 3 tokens, colors, & heatmap palettes
│   └── utils/               # DateTimeUtils & pure StreakCalculator algorithm
├── features/
│   ├── habits/              # Habit entities, SQLite DAOs, Riverpod controllers & UI
│   ├── analytics/           # Statistics controller, summary cards & calendar heatmap
│   └── home_widget/         # JSON serialization & home widget sync manager
├── app.dart                 # MaterialApp root with reactive screens
└── main.dart                # Entrypoint & background widget callbacks
```

---

## Getting Started

### Prerequisites
- [Flutter SDK](https://docs.flutter.dev/get-started/install) (3.35.0 or higher)
- [Android Studio](https://developer.android.com/studio) or [Xcode](https://developer.apple.com/xcode/)
- Java 17+

### 1. Clone & Install Dependencies
```bash
git clone https://github.com/example/habit-streak-tracker.git
cd 2Habit-Streak-Tracker
flutter pub get
```

### 2. Environment Configuration
Copy `.env.example` to set up your local configuration:
```bash
cp .env.example .env
```

### 3. Run Locally
Run on your connected Android or iOS device:
```bash
flutter run
```

---

## Automated Testing & Code Quality

The project enforces strict code quality and comprehensive test coverage across business logic, database migrations, and UI components.

### Run All Tests
```bash
flutter test
```

### Run Static Analysis
```bash
flutter analyze
```

### Format Code
```bash
dart format .
```

### Test Suite Breakdown
| Test File | Target Area | Description |
| :--- | :--- | :--- |
| `streak_calculator_test.dart` | Algorithmic Engine | Verifies ongoing streaks, freezes, leap years, year boundaries, and rates |
| `database_migration_test.dart` | SQLite Schema | Tests `V1Schema` and `V2AddFreeze` with `sqflite_common_ffi` |
| `habit_repository_test.dart` | Data Persistence | Tests CRUD, input validation, batch queries (fixes N+1), and SQLite cascade deletes |
| `habit_detail_controller_test.dart` | State Management | Tests detail controller updates, reminder rescheduling, and freeze actions |
| `habit_list_controller_test.dart` | State Management | Verifies optimistic UI updates and error rollback transitions |
| `habit_model_test.dart` | Serialization | Verifies SQLite Map serialization and immutability |
| `habit_card_test.dart` | UI Component | Validates quick check-in tap callback and optimistic state |
| `stats_summary_card_test.dart` | UI Component | Verifies summary metrics rendering |
| `habit_detail_sheet_test.dart` | Modal UI | Tests habit editing and streak freeze modal actions |
| `widget_test.dart` | Smoke Testing | Tests full app initialization with Riverpod |

---

## Native Home Screen Widgets

### Android
- Defined in `android/app/src/main/res/xml/habit_widget_info.xml`.
- Layout defined in `android/app/src/main/res/layout/habit_widget_layout.xml`.
- Kotlin provider: `android/app/src/main/kotlin/com/example/habit_streak_tracker/HabitAppWidgetProvider.kt`.
- Data is updated via `HomeWidget.saveWidgetData` and `HomeWidget.updateWidget`.

### iOS WidgetKit
- Extension defined in `ios/HabitStreakWidget/HabitStreakWidget.swift`.
- Uses App Group `group.com.example.habitstreaktracker` to read shared user defaults.

---

## CI/CD Pipeline

The project includes pre-configured GitHub Actions workflows in `.github/workflows/`:
1. **`ci.yml`**: Automatically triggered on pull requests to `main` and `develop`. Executes `dart format`, `flutter analyze`, and `flutter test`.
2. **`deploy-firebase.yml`**: Automatically triggered on version tags (`v*.*.*`). Builds signed Android release artifacts and deploys them to Firebase App Distribution with release notes.

---

## Troubleshooting & FAQ

### 1. SQLite Errors in Unit Tests (`MissingPluginException`)
- **Cause:** Standard `sqflite` requires native platform channels which are unavailable during CLI unit test execution.
- **Fix:** Unit tests automatically initialize `sqflite_common_ffi`:
  ```dart
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;
  ```

### 2. Notifications Not Showing on Android 13+
- **Cause:** Android 13 (API 33) introduces runtime `POST_NOTIFICATIONS` permission.
- **Fix:** The app calls `NotificationService.instance.requestPermissions()` at startup in `main.dart`. Ensure the permission is allowed in system settings.

### 3. Home Screen Widget Not Updating on iOS
- **Cause:** App Groups must be enabled in both the main runner target and the Widget extension target.
- **Fix:** Verify in Xcode that **Signing & Capabilities** includes the same App Group identifier configured in `AppConstants.appGroupId` (`group.com.example.habitstreaktracker`).

### 4. Timezone Caching
- **Cause:** Scheduled local notifications require timezone location lookup.
- **Fix:** Handled automatically in `NotificationService.initialize()` using `tz.initializeTimeZones()`.

---

## Documentation Links

- [Project Plan & Roadmap](docs/PROJECT-PLAN.md)
- [Architecture & Sequence Diagrams](docs/ARCHITECTURE.md)
- [Technical Notes & Engineering Playbook](docs/TECH-NOTES.md)
