# Habit Streak Tracker - Project Plan & Structure

## 1. Executive Summary & Overview
- **Project Name:** Habit Streak Tracker
- **Target Platforms:** iOS & Android (Mobile-first, optimized for widget interactions)
- **Primary Tech Stack:** Flutter, Dart, Riverpod (State Management), SQLite (`sqflite`), `flutter_local_notifications`, `home_widget`
- **Distribution & CI/CD:** Firebase App Distribution, GitHub Actions
- **Core Value Proposition:** A clean, distraction-free habit streak tracking app focused on frictionless daily check-ins, glanceable home screen widgets, customizable localized reminders, streak freezing mechanisms for resilience, and rich calendar heatmap visual analytics.

---

## 1.1 Project File Structure (Code + CI + Tools)

The project follows a **Feature-First Layered Architecture** with strict adherence to Clean Architecture separation of concerns:
- **Presentation Layer:** Widgets, UI state, controllers/notifiers (Riverpod).
- **Domain Layer:** Pure Dart business models, failure definitions, repository contracts.
- **Data Layer:** Concrete implementations of repositories, local datasources, SQLite DAOs, and data transfer models.
- **Core Layer:** Cross-cutting concerns such as database initialization, migrations, notification services, theme definitions, and date/streak calculation algorithms.

```text
habit_streak_tracker/
├── .github/                                  # CI/CD and automation
│   ├── workflows/
│   │   ├── ci.yml                            # Formatting, static analysis, unit & widget tests
│   │   └── deploy-firebase.yml               # Automated Android/iOS build & Firebase distribution
│   └── pull_request_template.md              # Standardized PR checklist
├── android/                                  # Android native host project
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml           # Widget permissions & notification receiver configs
│   │   │   ├── res/xml/habit_widget_info.xml # Android Glance/RemoteViews app widget definition
│   │   │   ├── res/layout/habit_widget_layout.xml # AppWidget layout XML
│   │   │   └── kotlin/.../HabitAppWidgetProvider.kt # HomeWidget receiver & update provider
│   │   └── build.gradle.kts
├── ios/                                      # iOS native host project
│   ├── Runner/
│   │   ├── AppDelegate.swift                 # Notification categories & background channels
│   │   └── Info.plist                        # Background modes, App Group configuration
│   └── HabitStreakWidget/                    # WidgetKit extension for iOS 14+
│       ├── HabitStreakWidget.swift           # WidgetKit timeline provider & UI
│       └── Info.plist
├── assets/                                   # Static application assets
│   ├── icons/                                # Custom habit icons & notification icons
│   └── fonts/                                # Custom typography
├── docs/                                     # Architectural & technical documentation
│   ├── PROJECT-PLAN.md                       # Deliverable 1: Directory layout & implementation plan
│   ├── ARCHITECTURE.md                       # Deliverable 2: Architecture, Mermaid diagrams, flows
│   └── TECH-NOTES.md                         # Deliverable 3: CI/CD, testing, environment, pitfalls
├── lib/                                      # Flutter Dart source code
│   ├── core/                                 # Cross-cutting platform & utility logic
│   │   ├── constants/
│   │   │   ├── app_constants.dart            # Global keys, notification channels, widget keys
│   │   │   └── db_constants.dart             # Table names, column strings, migration constants
│   │   ├── database/
│   │   │   ├── app_database.dart             # SQLite database open/init, singleton provider
│   │   │   └── migrations/
│   │   │       ├── migration.dart            # Base migration interface
│   │   │       ├── v1_schema.dart            # Initial habits & completions schema
│   │   │       └── v2_add_freeze.dart        # Migration adding streak_freezes table
│   │   ├── errors/
│   │   │   ├── exceptions.dart               # Infrastructure-level exceptions
│   │   │   └── failures.dart                 # Domain-level failure representations
│   │   ├── services/
│   │   │   ├── notification_service.dart     # flutter_local_notifications wrapper
│   │   │   └── home_widget_service.dart      # home_widget data sync & update trigger
│   │   ├── theme/
│   │   │   ├── app_colors.dart               # Curated modern color palette & heatmap gradients
│   │   │   └── app_theme.dart                # Material 3 light/dark theme data
│   │   └── utils/
│   │       ├── date_time_utils.dart          # UTC/Local normalizer & day-boundary helpers
│   │       └── streak_calculator.dart        # Pure algorithmic engine for current & max streaks
│   ├── features/                             # Feature-first modular modules
│   │   ├── habits/                           # Habit definition, management & check-in
│   │   │   ├── data/
│   │   │   │   ├── datasources/
│   │   │   │   │   ├── habit_local_datasource.dart
│   │   │   │   │   └── habit_completion_local_datasource.dart
│   │   │   │   ├── models/
│   │   │   │   │   ├── habit_model.dart      # DB serialization / JSON mapping
│   │   │   │   │   └── habit_completion_model.dart
│   │   │   │   └── repositories/
│   │   │   │       └── habit_repository_impl.dart
│   │   │   ├── domain/
│   │   │   │   ├── models/
│   │   │   │   │   ├── habit.dart            # Core entity with frequency & reminder rules
│   │   │   │   │   ├── habit_completion.dart # Date-stamped completion record
│   │   │   │   │   ├── habit_frequency.dart  # Daily, Weekly, SpecificDays enum & logic
│   │   │   │   │   └── streak_freeze.dart    # Freeze allowance entity for sick/rest days
│   │   │   │   └── repositories/
│   │   │   │       └── habit_repository.dart # Abstract contract for data operations
│   │   │   └── presentation/
│   │   │       ├── controllers/
│   │   │       │   ├── habit_list_controller.dart # AsyncNotifier for reactive habit list
│   │   │       │   └── habit_detail_controller.dart # FamilyAsyncNotifier for habit detail/edit
│   │   │       ├── providers/
│   │   │       │   └── habit_providers.dart       # Riverpod provider dependency wiring
│   │   │       └── widgets/
│   │   │           ├── habit_card.dart            # Interactive card with swipe/tap check-in
│   │   │           ├── habit_detail_sheet.dart    # Modal sheet for editing habit & streak freezes
│   │   │           ├── streak_badge.dart          # Flame icon with streak count
│   │   │           └── habit_quick_check_in.dart  # One-tap check-in toggle button
│   │   ├── analytics/                        # Historical trends, heatmap & statistics
│   │   │   ├── domain/
│   │   │   │   └── habit_stats.dart          # Best streak, completion rate, total check-ins
│   │   │   └── presentation/
│   │   │       ├── controllers/
│   │   │       │   └── stats_controller.dart
│   │   │       └── widgets/
│   │   │           ├── calendar_heatmap.dart      # GitHub-style contribution heatmap
│   │   │           └── stats_summary_card.dart    # Aggregate metrics visual card
│   │   └── home_widget/                      # Native home screen widget synchronization
│   │       ├── home_widget_manager.dart      # Background payload generation & widget sync
│   │       └── home_widget_payload.dart      # Serialized state for native consumption
│   ├── app.dart                              # MaterialApp widget with routing & theme configuration
│   └── main.dart                             # Entrypoint, DI initialization, widget callbacks
├── test/                                     # Automated test suites
│   ├── unit/                                 # Business logic & algorithm tests
│   │   ├── streak_calculator_test.dart       # Comprehensive edge case testing for streaks
│   │   ├── habit_model_test.dart             # Model serialization & immutability tests
│   │   ├── database_migration_test.dart      # Schema migration verification via sqflite_common_ffi
│   │   └── habit_repository_test.dart        # SQLite CRUD & streak freeze persistence integration
│   ├── widget/                               # UI component & Riverpod widget tests
│   │   ├── habit_card_test.dart              # Tap check-in, optimistic UI update tests
│   │   ├── habit_detail_sheet_test.dart      # Habit detail & streak freeze modal tests
│   │   └── stats_summary_card_test.dart      # Metrics rendering test
│   └── mocks/
│       └── mock_habit_repository.dart        # Mocktail / mock implementations for isolation
├── .env.example                              # Environment configuration template
├── analysis_options.yaml                     # Strict linting rules (pedantic/flutter_lints)
├── pubspec.yaml                              # Package dependencies & asset declarations
└── README.md                                 # Project documentation & onboarding guide
```

---

## 1.2 Implementation TODO List

A prioritized, milestone-driven roadmap for delivering the complete Habit Streak Tracker.

### Phase 1: Foundation (High Priority)
- [x] **Core Setup & Tooling**
  - [x] Configure `pubspec.yaml` with stable dependencies (`flutter_riverpod`, `sqflite`, `path_provider`, `flutter_local_notifications`, `home_widget`, `timezone`, `uuid`, `intl`).
  - [x] Configure strict static analysis rules in `analysis_options.yaml`.
  - [x] Configure environment variable templates (`.env.example`) and build flavors (`dev`, `staging`, `prod`).
- [x] **Database Architecture (SQLite)**
  - [x] Implement `AppDatabase` wrapper with SQLite connection lifecycle and foreign key support.
  - [x] Implement versioned schema migrations (`v1_schema.dart` for habits and completions, `v2_add_freeze.dart` for streak freezes).
  - [x] Write unit tests for database migrations using in-memory SQLite (`sqflite_common_ffi`).
- [x] **Domain Models & Calculation Engine**
  - [x] Create domain entities: `Habit`, `HabitCompletion`, `HabitFrequency`, and `StreakFreeze`.
  - [x] Implement `StreakCalculator` algorithm handling daily, specific days, weekend skips, and streak freeze deductions.
  - [x] Add unit test coverage (>95%) for edge cases: timezone boundary crossovers, consecutive freezes, and missing days.
- [x] **Repository Layer**
  - [x] Define abstract `HabitRepository` contract.
  - [x] Implement `HabitRepositoryImpl` utilizing SQLite DAOs with transaction safety for completion toggles.

### Phase 2: Core Features (Medium Priority)
- [x] **Reactive State Management (Riverpod)**
  - [x] Build `HabitListController` (`AsyncNotifier`) providing reactive UI updates.
  - [x] Implement optimistic UI updates for instant check-in response with rollback on error.
  - [x] Implement `HabitDetailController` for editing habits, schedules, and custom notification times.
- [x] **User Interface Components**
  - [x] Build minimalist Material 3 theme (`AppTheme`) with dark mode default and accessible contrast ratios.
  - [x] Implement `HabitCard` with fluid completion micro-interactions and haptic feedback.
  - [x] Implement `StreakBadge` displaying animated streak counter and freeze indicators.
  - [x] Build habit creation & editing modal sheet with frequency selector (Daily, Weekdays, Specific Days).
- [x] **Local Notification Scheduling**
  - [x] Implement `NotificationService` wrapping `flutter_local_notifications`.
  - [x] Configure Android notification channels (`importance: high`, `priority: max`) and iOS notification permissions.
  - [x] Schedule exact recurring daily reminders using `timezone/data/latest_all.dart`.
  - [x] Handle notification tap routing to open the specific habit.
- [x] **Native Home Screen Widgets**
  - [x] Implement `HomeWidgetService` using `home_widget` package.
  - [x] Create Android AppWidget (`RemoteViews` / Jetpack Glance layout) displaying today's pending habits.
  - [x] Create iOS WidgetKit extension showing checklist with deep links or background interactive check-in.
  - [x] Sync SQLite data snapshot to App Group / SharedPreferences whenever habits are updated.

### Phase 3: Polish & Optimization (Lower Priority)
- [x] **Analytics & Calendar Heatmap**
  - [x] Build `CalendarHeatmap` widget visualizing completion intensity across past months (GitHub-style).
  - [x] Calculate rolling completion rates, current streaks, and historical best streaks.
  - [x] Display monthly streak freeze usage overview with `StatsSummaryCard`.
- [x] **Streak Freeze Mechanism**
  - [x] Allow users to activate a "Streak Freeze" for sick/travel days to prevent streak reset.
  - [x] Automatically apply available freezes when an active day is missed.
- [x] **CI/CD & Delivery**
  - [x] Configure GitHub Actions workflow `ci.yml` for pull request static analysis, formatting, and unit tests.
  - [x] Configure GitHub Actions workflow `deploy-firebase.yml` to build release APK/AAB and deploy to Firebase App Distribution with release notes.
- [x] **Performance & Hardening**
  - [x] Implement database indexes on `(habit_id, date)` to ensure constant-time queries for year-long heatmaps.
  - [x] Verify battery consumption: ensure background sync only wakes via push or HomeWidget refresh callbacks.
