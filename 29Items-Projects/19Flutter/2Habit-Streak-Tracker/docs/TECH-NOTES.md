# Habit Streak Tracker - Technical Notes & Engineering Playbook

## 1. Overview
This guide provides actionable technical guidelines, implementation standards, DevOps workflows, testing architectures, and troubleshooting documentation for engineers maintaining and extending the **Habit Streak Tracker** Flutter application.

---

## 3.1 CI/CD Pipeline Design (GitHub Actions)

The deployment pipeline relies on GitHub Actions divided into distinct sequential jobs:

```text
[Push / Pull Request]
          │
          ▼
┌──────────────────┐
│  Static Analysis │  --> flutter analyze & dart format check
└─────────┬────────┘
          │
          ▼
┌──────────────────┐
│    Unit Tests    │  --> flutter test --coverage (StreakCalculator, Models, DAOs)
└─────────┬────────┘
          │ (Only on merge to 'main' or 'staging' release tag)
          ▼
┌──────────────────┐
│   Build Artifact │  --> Android App Bundle (AAB) / APK & iOS IPA
└─────────┬────────┘
          │
          ▼
┌──────────────────┐
│ Firebase Distro  │  --> Firebase App Distribution via CLI / Fastlane
└──────────────────┘
```

### Pipeline Stages & Automation:
1. **Lint & Formatting:**
   - Command: `dart format --output=none --set-exit-if-changed .`
   - Command: `flutter analyze --fatal-infos`
2. **Automated Testing:**
   - Command: `flutter test --coverage`
   - Coverage report is parsed; pull requests failing the minimum 80% coverage on domain logic are blocked.
3. **Build Stage:**
   - Android: `flutter build appbundle --flavor production --dart-define-from-file=config/prod.json`
   - iOS: `flutter build ipa --flavor production --export-options-plist=ios/ExportOptions.plist`
4. **Deploy Stage (Firebase App Distribution):**
   - Uses `wzieba/Firebase-Distribution-Github-Action` or Fastlane.
   - Uploads binary to Firebase App Distribution with automated release notes compiled from git commit logs.
   - Designates tester groups (`qa-internal`, `beta-testers`).

---

## 3.2 Testing Strategy

### 1. Unit Testing (Target Coverage: >85% for Domain/Core)
- **Frameworks:** `flutter_test`, `mocktail`
- **Focus Areas:**
  - `StreakCalculator`: Exhaustive edge-case tests including month/year crossovers, leap years, skipped non-scheduled days, and multiple streak freeze consumptions.
  - `DateTimeUtils`: Timezone conversions, normalized ISO-8601 date string formatting (`yyyy-MM-dd`).
  - Model serialization and database mapping (`HabitModel.fromMap`, `toMap`).
- **Database Testing:**
  - Uses `sqflite_common_ffi` to run real SQLite queries in an in-memory database during CLI unit test runs (without needing an emulator).

### 2. Widget & Component Testing (Target Coverage: >70%)
- **Frameworks:** `flutter_test` with Riverpod's `ProviderScope(overrides: [...])`.
- **Focus Areas:**
  - `HabitCard`: Verify check-in tap triggers the controller, tests optimistic UI toggle, and validates state rollback on repository error.
  - `StreakBadge`: Ensure flame animation and freeze count badge render appropriate counts.
  - `CalendarHeatmap`: Verify empty vs. full completion matrix color shades.

### 3. Integration & End-to-End Testing
- **Tools:** `patrol` or `integration_test` (built into Flutter SDK).
- **Critical Flow:**
  1. Launch app -> create new habit "Drink 2L Water" with daily frequency at 08:00.
  2. Tap complete -> verify streak increments to 1.
  3. Verify notification is scheduled for the next day.
  4. Verify home widget dataset contains "Drink 2L Water" marked done.

---

## 3.3 Deployment Strategy

### Firebase App Distribution Workflow
- **Beta Releases:** Every push to the `develop` branch automatically produces an internal APK distributed to QA testers.
- **Staging Releases:** Pushing a semantic release tag (e.g., `v1.2.0-beta.1`) triggers production flavor builds deployed to product stakeholders via Firebase App Distribution.
- **Store Production Releases:** Once approved in Firebase App Distribution, the same signed release bundle (AAB/IPA) is promoted to Google Play Internal Track and Apple TestFlight.

---

## 3.4 Environment Management

The application utilizes Flutter's `--dart-define-from-file` and compile-time constants for environment isolation (`dev`, `staging`, `prod`).

### Configuration Files
- `config/dev.json`
- `config/staging.json`
- `config/prod.json`

### `.env.example` Template
```ini
# Environment Profile
APP_ENV=development
APP_NAME=Habit Tracker (Dev)
APP_BUNDLE_ID=com.example.habittracker.dev

# Feature Flags
ENABLE_STREAK_FREEZE=true
ENABLE_CALENDAR_HEATMAP=true
ENABLE_HOME_WIDGET=true

# Notification Defaults
NOTIFICATION_CHANNEL_ID=habit_reminders_channel_dev
NOTIFICATION_CHANNEL_NAME=Habit Reminders Dev

# Database
DATABASE_NAME=habit_tracker_dev.db
DATABASE_VERSION=2

# Firebase App Distribution
FIREBASE_APP_ID_ANDROID=1:000000000000:android:abcdef123456
FIREBASE_APP_ID_IOS=1:000000000000:ios:abcdef123456
FIREBASE_TESTER_GROUPS=internal-qa,dev-team
```

---

## 3.5 Version Control Workflow

We mandate **Trunk-Based Development** with short-lived feature branches:

```text
main (Always deployable & stable)
  │
  ├── feature/HT-101-streak-freeze-logic ────► PR Review & CI ──► Squash & Merge
  ├── fix/HT-102-timezone-offset-fix ────────► PR Review & CI ──► Squash & Merge
  └── release/v1.0.0 (Release branch for store submission)
```

### Rationale:
- Minimizes merge debt and long-lived branch synchronization conflicts.
- Encourages continuous automated testing on every pull request.
- Every merge to `main` produces an installable build on Firebase App Distribution.

---

## 3.6 Common Pitfalls & How to Avoid Them

### 1. HomeWidget Background Execution Limits (Android & iOS)
- **Problem:** Android 12+ and iOS 14+ severely restrict background process execution and frequent widget updates to save battery.
- **Solution:** Never poll or compute heavy data inside widget background workers. Instead, compute and format the widget's final JSON payload inside the main Flutter app whenever a database mutation occurs, then call `HomeWidget.saveWidgetData` and `HomeWidget.updateWidget`.

### 2. Timezone Offsets & Day-Boundary Shifts in Streaks
- **Problem:** Storing timestamps with local device offsets causes habits checked at 23:59 to register on the next day if the user travels or during Daylight Saving Time (DST) transitions.
- **Solution:** Store completion dates as date-only ISO-8601 strings (`yyyy-MM-dd`) in the user's localized calendar day at the time of check-in. Never use raw epoch milliseconds for streak comparison logic.

### 3. SQLite Foreign Key Enforcements
- **Problem:** SQLite disables foreign key constraints by default (`PRAGMA foreign_keys = OFF`), leading to orphaned `habit_completions` records if a habit is deleted.
- **Solution:** Explicitly execute `await db.execute('PRAGMA foreign_keys = ON;');` inside the `onConfigure` callback of `openDatabase`.

### 4. Streak Freeze Deduction Race Conditions
- **Problem:** If a user misses 2 days and opens the app, asynchronous checks might accidentally consume all available streak freezes multiple times.
- **Solution:** Wrap the gap evaluation and freeze deduction inside a single SQLite transaction with an atomic check on the remaining freeze inventory.

### 5. Notification Rescheduling on Device Reboot
- **Problem:** Android clears all pending `AlarmManager` reminders when the device reboots.
- **Solution:** Register `RECEIVE_BOOT_COMPLETED` permission in `AndroidManifest.xml` and invoke `NotificationService.rescheduleAllHabits()` on application initialization or via a native boot receiver.
