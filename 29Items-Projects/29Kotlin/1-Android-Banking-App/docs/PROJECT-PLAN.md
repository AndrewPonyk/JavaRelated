# Android Banking App — Project Plan

Modern Android banking client (Kotlin, Jetpack Compose, Coroutines, Room, Retrofit, Hilt, Firebase)
with a Kotlin/Ktor backend providing payments, transfers, statements, and ML-based fraud detection
on transaction patterns. Distributed via Google Play.

> **Status:** Phase 1 (Foundation) **complete** — Compose-only UI, login gate, statements,
> transfers round-trip against the Ktor backend, tests green on app + backend. Phase 2/3
> items below remain the roadmap. Two Phase-2 notes were pulled forward into Phase 1 because
> they were trivial: `Clock` injection (3.6 #10) and the `bank_secure_prefs` backup-rule
> exclusion (3.6 #8).

## 1. Goals & Non-Goals

**Goals**
- Single-activity Compose UI with unidirectional data flow (state down, events up).
- Offline-first accounts/transactions via Room cache + Retrofit refresh.
- Transfers & payments with **idempotent** submission and dual fraud detection
  (on-device rules + server-side scoring service, later a real ML model).
- Biometric gate for sensitive operations; encrypted token storage.
- Ktor backend exposing `api/v1` REST endpoints (accounts, payments, statements).
- CI/CD on GitHub Actions → signed AAB → Google Play internal track.

**Non-Goals (for now)**
- Real money movement / PSP integration (Sandbox/demo only — no PSD2 license scope).
- Real-time push transaction feeds over WebSockets (FCM push only).
- Multi-user production auth server (stubbed with tokens; Phase 2 adds JWT).

## 1.1 Project File Structure

```
1-Android-Banking-App/
├── app/                                # Android application module
│   ├── build.gradle.kts                # Compose, Hilt/KSP, Room, Retrofit, detekt
│   ├── google-services.json.example    # Placeholder — copy real file, then enable plugin
│   ├── schemas/                        # Room exported schemas (generated on build)
│   └── src/
│       ├── main/java/com/example/a1_android_banking_app/
│       │   ├── BankApplication.kt      # @HiltAndroidApp entry point
│       │   ├── MainActivity.kt         # Template fragments today → Compose in Phase 1
│       │   ├── di/                     # Hilt modules (Network, Database, Repository)
│       │   ├── data/
│       │   │   ├── local/              # Room: BankDatabase, entities, DAOs
│       │   │   ├── remote/             # Retrofit API + DTOs (kotlinx.serialization)
│       │   │   └── repository/         # BankRepository (offline-first facade)
│       │   ├── domain/model/           # Pure Kotlin models (Account, Transaction, …)
│       │   ├── fraud/                  # On-device fraud engine (rules → TFLite later)
│       │   ├── security/               # EncryptedSharedPreferences TokenStore
│       │   ├── biometric/              # BiometricPrompt wrapper
│       │   ├── push/                   # FCM push service
│       │   ├── ui/
│       │   │   ├── accounts/           # AccountsScreen + ViewModel (loading/error/data)
│       │   │   ├── transfer/           # TransferScreen + ViewModel (fraud-aware flow)
│       │   │   └── BankApp.kt          # Compose NavHost root
│       │   └── ui/…template fragments/ # Home/Dashboard/Notifications (removed in Phase 1)
│       ├── main/res/                   # Themes, strings, navigation graph (template)
│       ├── test/…/fraud/               # JVM unit tests (fraud rules)
│       └── androidTest/                # Compose UI / instrumented tests
├── backend/                            # Ktor server module (Kotlin/JVM)
│   ├── build.gradle.kts                # Ktor 2.x, kotlinx.serialization, logback
│   └── src/
│       ├── main/kotlin/com/example/bank/backend/
│       │   ├── Application.kt          # EngineMain + module wiring
│       │   ├── plugins/                # Serialization, Monitoring, StatusPages, Routing
│       │   ├── api/                    # HTTP routes (accounts, payments) + validation
│       │   ├── service/                # BankService, FraudDetectionService
│       │   ├── data/                   # PaymentRepository (in-memory → Exposed+PG later)
│       │   └── model/                  # Serializable DTOs + domain results
│       ├── main/resources/
│       │   ├── application.conf        # HOCON config w/ env overrides
│       │   └── db/migration/V1__core_schema.sql   # Flyway schema (PostgreSQL)
│       └── test/kotlin/…               # Ktor test-host route tests
├── docs/                               # PROJECT-PLAN.md, ARCHITECTURE.md, TECH-NOTES.md
├── .github/workflows/
│   ├── ci.yml                          # detekt → unit tests → assemble (app + backend)
│   └── deploy-play.yml                 # Signed AAB → Play internal track (manual/tag)
├── .env.example                        # Environment variable template
├── detekt.yml                          # Static analysis config
├── .editorconfig                       # Consistent formatting
├── build.gradle.kts                    # Root: plugin versions (AGP, Kotlin, KSP, Hilt…)
├── settings.gradle.kts                 # Repositories + :app, :backend modules
└── gradle/wrapper/                     # Gradle 8.13
```

**Tooling map**

| Concern            | Tool                                            |
|--------------------|--------------------------------------------------|
| Build              | Gradle 8.13, AGP 8.12, Kotlin 2.0.21            |
| DI                 | Hilt 2.52 (KSP)                                  |
| UI                 | Jetpack Compose (BOM 2024.12.01), Material 3     |
| Persistence        | Room 2.6.1 (KSP, schema export)                  |
| Networking         | Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization |
| Backend            | Ktor 2.3.12 (Netty), logback                     |
| Push               | Firebase Messaging (BOM 33.7.0)                  |
| Static analysis    | detekt 1.23.7, Android Lint                      |
| CI/CD              | GitHub Actions (+ r0adkll/upload-google-play)    |

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority)

- [x] Scaffold repo structure, docs, CI skeletons (this deliverable)
- [x] Gradle wiring: Compose, Hilt/KSP, Room, Retrofit, serialization, detekt
- [x] Domain models + money as `Long` minor units (never floating point)
- [x] Room layer: entities, DAOs, `BankDatabase`, schema export
- [x] Network layer: `BankingApi`, DTOs, Hilt `NetworkModule`/`DatabaseModule`
- [x] Offline-first `BankRepository` (observe Room → refresh via Retrofit)
- [x] Ktor backend skeleton: routes, services, validation, status pages, in-memory repo
- [x] Flyway V1 schema for PostgreSQL (accounts, transactions, fraud_alerts, idempotency)
- [x] **Switch `MainActivity` to `setContent { BankApp() }`**, delete template fragments/layouts
- [x] Login screen: PIN + biometric (`BiometricAuthManager`) → `TokenStore`
- [x] Statements screen v0 (per-account Room history; Paging 3 stays in Phase 2)
- [x] Transfer account picker + client balance pre-check (replaced hardcoded `acc-1`)
- [x] Wire emulator loop: run backend (`:backend:run`), app hits `http://10.0.2.2:8080/`
- [x] CI commands verified locally (detekt + unit tests + assembleDebug + backend:check);
      first GitHub Actions run lands with the next push

### Phase 2 — Core features (medium priority)

- [ ] Transfers: account picker, confirmation screen, receipts, statement export (PDF/CSV)
- [ ] Statements screen: paginated transaction history (Room `PagingSource` + RemoteMediator)
- [ ] Backend: JWT auth (`ktor-server-auth`), per-user account scoping
- [x] Backend: Exposed + PostgreSQL + Flyway (config-selectable via `DB_ENABLED`; in-memory
      remains the zero-dependency default for tests/`:backend:run`; docker-compose runs the
      full stack — persistence verified across container restarts)
- [ ] On-device fraud rules v2: merchant category anomalies, geo-velocity, device signals
- [ ] Server fraud model: train gradient-boosted classifier on tx features (offline job), serve scores
- [ ] FCM: transaction notifications, token registration endpoint
- [ ] Offline queue for transfers (WorkManager + Room outbox)
- [ ] R8/minify + baseline profiles; Crashlytics + Play Vault access

### Phase 3 — Polish & optimization (lower priority)

- [ ] Replace rule engine with TFLite model on-device (quantized, <1 MB)
- [ ] Compose `LazyColumn` performance pass, `@Immutable` state classes, baseline profiles
- [ ] Accessibility audit (TalkBack, content descriptions, 18sp support)
- [ ] UI tests for transfer flow; screenshot tests (Paparazzi/Roborazzi)
- [ ] Play release: data-safety form, staged rollout, Play App Signing
- [ ] Threat modeling pass: TLS pinning, root/emulator detection, anti-tamper

## Milestones

| Milestone | Exit criteria                                                        |
|-----------|----------------------------------------------------------------------|
| M1 demo   | App shows seeded accounts from Ktor backend; transfer round-trips    |
| M2 secure | Login + biometric + JWT backend; transfers persisted in PostgreSQL   |
| M3 smart  | Fraud model live both sides; FCM push; CI→Play internal track works  |

## Open Decisions

1. **Backend hosting** — Cloud Run vs VM (fat jar). No container work needed for the app itself (Play).
2. **Firebase project** — must be created; `google-services.json.example` documents the shape.
3. **Fraud model training data** — synthetic generator vs public dataset (IEEE-CIS fraud dataset).
