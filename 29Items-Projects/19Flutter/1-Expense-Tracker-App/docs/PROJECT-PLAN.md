# Personal Expense Tracker — Project Plan

**Package:** `expense_tracker`
**Stack:** Dart 3.9 / Flutter 3.35 · Provider · Hive · FL Chart · flutter_local_notifications
**Targets:** Android (Play Store), iOS (App Store), Web + Windows (dev/debug convenience only)
**CI/CD:** Codemagic (primary, store publishing) + GitHub Actions (fast PR gate)

---

## 0. Architectural Stance (read this first)

This is a **local-first, single-process mobile application**. There is **no backend, no API server, no
network database, and no user accounts.** Every byte of user data lives in Hive boxes in the app's
private sandbox directory on the device.

That constraint is not a limitation to be worked around — it is the product decision that makes the
rest of the design simple, and it is why this plan deliberately **does not** contain:

| Commonly expected | Why it is absent here |
|---|---|
| REST/GraphQL controllers | No server exists. The equivalent boundary is the **repository layer**. |
| Auth / JWT / OAuth | No remote identity. Device-level security (OS sandbox + optional app lock) replaces it. |
| SQL migrations | Hive is schemaless NoSQL. Schema evolution is handled by a **versioned migrator** (`lib/data/migrations/`). |
| Docker / Kubernetes | The deliverable is a signed `.aab` / `.ipa`, not a container. |
| Message queues, event bus infra | In-process `ChangeNotifier` + `ValueListenable` cover all fan-out needs. |

> **Deliverable mapping note.** The brief asks for a "Backend Endpoint (CRUD + service layer +
> input validation)". In this stack that role is filled by
> `lib/data/repositories/expense_repository.dart` (CRUD + validation at the boundary) and
> `lib/domain/services/budget_service.dart` (business logic). They are written to the same standard
> an HTTP controller would be: validate at the edge, return typed failures, never trust the caller.

One deliberate exception to "no network": **multi-currency support for travelers** needs FX rates.
That is a single, optional, cacheable GET against a public rates API — see §2.6 and
`lib/domain/services/currency_service.dart`. The app is fully functional offline with stale rates.

---

## 1.1 Project File Structure

```
1-Expense-Tracker-App/
├── claude-opus-5.txt                  # model marker (per brief)
├── pubspec.yaml
├── analysis_options.yaml              # strict lints + custom rules
├── .env.example                       # build-time config template (→ --dart-define)
├── codemagic.yaml                     # PRIMARY CI/CD: build, sign, publish to stores
├── l10n.yaml                          # localisation config (currency/date formats)
│
├── .github/
│   └── workflows/
│       ├── ci.yaml                    # fast PR gate: format → analyze → test
│       └── release-tag.yaml           # tag validation + changelog draft
│
├── docs/
│   ├── PROJECT-PLAN.md                # ← you are here
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── migrations/                        # Hive schema evolution, one doc per version
│   ├── README.md                      # how Hive "migrations" differ from SQL
│   ├── 0001_initial_schema.md         # typeId registry + field maps (SOURCE OF TRUTH)
│   └── 0002_example_add_field.md      # worked example of a safe additive change
│
├── scripts/
│   ├── dart_define.sh                 # .env → --dart-define-from-file bridge
│   └── coverage.sh                    # lcov + threshold enforcement
│
├── lib/
│   ├── main.dart                      # composition root: init → provide → run
│   ├── app.dart                       # MaterialApp, theme, routes, locale
│   │
│   ├── core/                          # zero-dependency foundation (no feature imports)
│   │   ├── config/
│   │   │   ├── env.dart               # compile-time config via String.fromEnvironment
│   │   │   └── app_config.dart        # feature flags, tunables
│   │   ├── constants/
│   │   │   ├── app_constants.dart
│   │   │   └── hive_boxes.dart        # box names + typeId registry (single source)
│   │   ├── error/
│   │   │   ├── failures.dart          # sealed Failure hierarchy
│   │   │   └── error_handler.dart     # guard(), global hooks, user-facing mapping
│   │   ├── logging/
│   │   │   └── app_logger.dart        # levelled, redacting, release-safe
│   │   ├── router/
│   │   │   └── app_router.dart        # named routes, typed args
│   │   ├── theme/
│   │   │   ├── app_colors.dart        # category palette (colour-blind safe)
│   │   │   └── app_theme.dart         # light/dark Material 3
│   │   └── utils/
│   │       ├── money.dart             # minor-unit integer money (NEVER double)
│   │       ├── date_range.dart        # month/week boundary maths
│   │       └── result.dart            # Result<T> = Ok | Err
│   │
│   ├── data/                          # persistence — knows Hive, knows nothing of UI
│   │   ├── models/                    # @HiveType data classes
│   │   │   ├── expense.dart
│   │   │   ├── expense_category.dart
│   │   │   ├── budget.dart
│   │   │   ├── recurring_expense.dart
│   │   │   ├── quick_template.dart
│   │   │   ├── app_settings.dart
│   │   │   └── currency_rate.dart
│   │   ├── adapters/
│   │   │   └── hive_adapters.dart     # HAND-WRITTEN TypeAdapters (see §1.3)
│   │   ├── datasources/
│   │   │   └── hive_service.dart      # box lifecycle, open/close, compaction
│   │   ├── migrations/
│   │   │   └── hive_migrator.dart     # runs versioned steps on startup
│   │   └── repositories/
│   │       ├── expense_repository.dart      # CRUD + validation boundary
│   │       ├── budget_repository.dart
│   │       ├── recurring_repository.dart
│   │       ├── template_repository.dart
│   │       ├── settings_repository.dart
│   │       └── currency_repository.dart
│   │
│   ├── domain/                        # pure business logic — no Flutter, no Hive imports
│   │   └── services/
│   │       ├── budget_service.dart        # progress, thresholds, overspend
│   │       ├── analytics_service.dart     # category/week/month aggregation
│   │       ├── recurring_service.dart     # materialise due recurrences
│   │       ├── csv_export_service.dart    # RFC-4180 export + share
│   │       ├── notification_service.dart  # budget alerts, tz-aware scheduling
│   │       └── currency_service.dart      # FX conversion, stale-rate policy
│   │
│   └── presentation/                  # Flutter + Provider only
│       ├── providers/
│       │   ├── async_state.dart       # Loading | Data | Error (UI contract)
│       │   ├── expense_provider.dart
│       │   ├── budget_provider.dart
│       │   ├── analytics_provider.dart
│       │   └── settings_provider.dart
│       ├── screens/
│       │   ├── home/home_screen.dart
│       │   ├── expenses/expense_list_screen.dart
│       │   ├── expenses/add_expense_screen.dart
│       │   ├── budget/budget_screen.dart
│       │   ├── analytics/analytics_screen.dart
│       │   └── settings/settings_screen.dart
│       └── widgets/
│           ├── async_value_view.dart      # loading/error/empty in ONE place
│           ├── budget_progress_bar.dart
│           ├── category_pie_chart.dart    # FL Chart
│           ├── weekly_bar_chart.dart      # FL Chart
│           ├── expense_list_tile.dart
│           └── quick_add_sheet.dart
│
├── test/
│   ├── helpers/hive_test_helper.dart  # temp-dir Hive, no mocking of Hive itself
│   ├── unit/money_test.dart
│   ├── unit/budget_service_test.dart
│   ├── unit/analytics_service_test.dart
│   ├── unit/recurring_service_test.dart
│   └── widget/budget_progress_bar_test.dart
│
└── integration_test/
    └── app_test.dart                  # real device: add expense → chart updates
```

### 1.3 Two structural decisions worth defending

**(a) Hand-written `TypeAdapter`s instead of `hive_generator` + `build_runner`.**
The generated adapters are ~40 lines of mechanical code per model that you must read anyway when
debugging a corrupt box. Hand-writing them (in the exact same wire format the generator emits)
removes `build_runner` from the toolchain: no `.g.dart` files in review diffs, no codegen step in CI,
no "did you run build_runner?" onboarding failure, and `flutter analyze` is green on a fresh clone.
The cost is discipline: **adding a field means editing `hive_adapters.dart` and
`migrations/000N_*.md` together.** For a 7-model schema that trade is clearly worth it. If the schema
grows past ~20 models, revisit and adopt codegen.

**(b) `domain/` may not import `package:flutter/*` or `package:hive/*`.**
Business rules (is this budget breached? what is this month's category split? which recurrences are
due?) are pure Dart functions over plain data. That is what makes them testable in milliseconds with
no `WidgetTester`, no temp directories, and no `setUpAll`. This is enforced by lint, not by hope —
see `analysis_options.yaml`.

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority, blocks everything)

- [x] `flutter create` with `--platforms=android,web,windows`, verify all three build
- [x] Directory skeleton + docs
- [x] **Money as integer minor units** (`core/utils/money.dart`) + exhaustive unit tests
      *Do this before any model.* Retrofitting `double`→`int` after data exists means a migration.
- [x] `hive_boxes.dart`: freeze the **typeId registry**. A reused typeId silently corrupts data.
- [x] All 7 Hive models + hand-written adapters + round-trip tests
- [x] `HiveService.init()` — open boxes, register adapters, wire compaction strategy
- [x] `HiveMigrator` — read `schemaVersion`, run steps, write back. Test the 0→1 path.
- [x] `Failure` hierarchy + `ErrorHandler` + `FlutterError.onError` / `PlatformDispatcher.onError`
- [x] `AppLogger` with release-mode redaction (amounts are sensitive)
- [x] Repositories with validation at the boundary (reject negative amounts, unknown categoryId)
- [x] `AsyncState<T>` + `AsyncValueView` — the ONLY place loading/error/empty is rendered
- [x] Theme + colour-blind-safe category palette
- [x] Seed built-in categories (food, transport, entertainment) idempotently on first run
- [x] CI green: GitHub Actions format→analyze→test on every PR

### Phase 2 — Core features (medium priority, this is the product)

- [x] Daily expense logging: add / edit / delete, date picker, category picker, note
- [x] Expense list: grouped by day, month scoping, category & search filter, running total header
- [x] Monthly budgets: overall + per-category, `BudgetService.progress()`
- [x] `BudgetProgressBar` with 3 visual states (under / near-threshold / over)
- [x] Analytics: category breakdown pie (FL Chart)
- [x] Analytics: weekly + monthly trend bars, with empty-state handling
- [x] Recurring expenses: frequency model, `RecurringService.materialiseDue()` on app resume + UI management screen
      *Must be idempotent and catch-up-capable — user may not open the app for 3 weeks.*
- [x] Quick-add templates: create from an existing expense, one-tap re-log + swipe/trash deletion
- [x] Local notifications: budget threshold alert (80% default) + monthly rollover reminder
      *Requires tz init, Android 13+ POST_NOTIFICATIONS, Android 12+ exact-alarm handling.*
- [x] Multi-currency: base currency setting, per-expense currency, rate cache, manual rate entry, stale-rate banner
- [x] CSV export: RFC-4180 correct, share sheet, currency + rate columns included
- [x] Widget tests for progress bar states; unit tests for all services ≥85%
- [x] `integration_test/app_test.dart`: add expense → list + chart both reflect it

### Phase 3 — Polish & optimisation (lower priority)

- [ ] Dark mode audit; verify chart legibility in both themes
- [ ] Accessibility: semantic labels on charts, 48dp touch targets, text scaling to 200%
- [ ] Localisation (`intl`): date/number/currency formats per locale, not hardcoded `$`
- [ ] Performance: `ValueListenableBuilder` on box listenables instead of full-list rebuilds
- [ ] Hive compaction policy tuning + startup-time budget (<400ms cold to first frame)
- [ ] Optional app lock (biometric) — `local_auth`
- [ ] Encrypted Hive boxes with key in platform keystore (see ARCHITECTURE §2.5)
- [ ] Onboarding flow + empty-state illustrations
- [ ] Codemagic: automated Play internal-track + TestFlight on tag
- [ ] Crash reporting decision (Sentry vs Crashlytics) — must respect local-first privacy promise
- [ ] Store assets: screenshots, privacy policy ("your data never leaves your device")

---

## 2. Risk Register

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| 1 | Float rounding corrupts totals | High if `double` used | Severe, silent | Integer minor units from day 1; `Money` is the only money type |
| 2 | Hive typeId collision on schema change | Medium | Data loss | Frozen registry in `hive_boxes.dart` + `migrations/` doc per version |
| 3 | Recurring generation duplicates or skips | High | User-visible wrong totals | `lastGeneratedDate` watermark + idempotent catch-up loop + unit tests |
| 4 | Notification permissions/exact-alarm on Android 13/14 | High | Feature silently dead | Runtime permission request + graceful degradation to inexact |
| 5 | FX rates stale or API dead | Medium | Wrong converted totals | Cache with `updatedAt`, show staleness banner, never block on network |
| 6 | iOS build/signing on Codemagic | Medium | Release blocked | Set up signing in Phase 1, not at release time |
| 7 | `flutter_local_notifications` unsupported on Windows/Web | Certain | Dev-time crashes | Platform guards in `NotificationService` |

---

## 3. Definition of Done (per feature)

1. Pure logic lives in `domain/services/`, unit-tested, no Flutter import
2. `flutter analyze` clean under strict lints — zero new warnings
3. Loading + error + empty states all rendered via `AsyncValueView`
4. Money handled as `Money`, never `double`
5. Any schema change has a matching `migrations/000N_*.md` entry
6. Feature works with airplane mode on
