# Architecture — Personal Expense Tracker

---

## 2.1 Chosen Architectural Pattern

### Layered Local-First Monolith (single process, unidirectional dependencies)

Four layers, with a strict **inward-only** dependency rule:

```
presentation ──▶ domain ──▶ (plain data)
     │                          ▲
     └──────────▶ data ─────────┘
```

| Layer | Owns | May import | May NOT import |
|---|---|---|---|
| `presentation/` | Widgets, Providers, navigation | `flutter`, `provider`, `domain`, `data` models | — |
| `domain/` | Business rules, aggregation, scheduling | plain Dart, `core/utils` | `flutter/*`, `hive/*` |
| `data/` | Hive boxes, adapters, repositories, migrations | `hive`, `core` | `presentation/*` |
| `core/` | Money, Result, Failure, logging, theme | `flutter` (theme only) | `data/*`, `domain/*`, `presentation/*` |

### Why this pattern, and why not the alternatives

**Why layered monolith:** The entire system is one binary running on one device with one user and one
writer. There is no network partition, no concurrent writer, no independent scaling axis, no
deployment boundary. Every property that motivates a distributed architecture is absent. Introducing
one would add coordination cost and buy literally nothing.

**Why not Clean Architecture with full use-case classes?** A `GetExpensesForMonthUseCase` wrapping a
single repository call is ceremony. This design keeps the *valuable* part of Clean Architecture — the
pure, framework-free `domain/` layer — and drops the part that only pays off across many teams. When a
service method exceeds ~40 lines or grows a third collaborator, it gets extracted; not before.

**Why not BLoC/Riverpod?** The brief specifies Provider, and Provider is genuinely correct here: the
state is small, mostly derived, and the interesting logic already lives in testable services. `BLoC`'s
event/state indirection would add two classes per screen to model "the user typed an amount".

**Why not offline-first-with-sync?** Offline-first implies a server of record and conflict resolution
(vector clocks, LWW, or CRDTs). Local-first here means the device **is** the server of record. No
conflicts can exist. This is the single largest complexity saving in the whole design, and it is why
"multi-device sync" is a v2 feature requiring an explicit architectural amendment, not a config flag.

```mermaid
graph TB
    subgraph PRES["presentation/ — Flutter + Provider"]
        SCR["Screens<br/>home · add · list · budget · analytics · settings"]
        WID["Widgets<br/>AsyncValueView · BudgetProgressBar · FL Charts"]
        PRV["Providers (ChangeNotifier)<br/>Expense · Budget · Analytics · Settings"]
    end

    subgraph DOM["domain/services — pure Dart, no Flutter, no Hive"]
        BS["BudgetService<br/>progress · thresholds"]
        AS["AnalyticsService<br/>category · week · month"]
        RS["RecurringService<br/>materialiseDue()"]
        CS["CurrencyService<br/>convert · staleness"]
        ES["CsvExportService"]
        NS["NotificationService"]
    end

    subgraph DATA["data/ — persistence"]
        REPO["Repositories<br/>validation boundary"]
        MIG["HiveMigrator<br/>schemaVersion steps"]
        HS["HiveService<br/>box lifecycle"]
        BOX[("Hive Boxes<br/>expenses · budgets · categories<br/>recurring · templates · settings · rates")]
    end

    subgraph CORE["core/ — foundation"]
        MON["Money<br/>integer minor units"]
        RES["Result / Failure"]
        LOG["AppLogger"]
    end

    OS["OS Services<br/>notifications · files · share sheet"]
    FX["FX Rates API<br/>optional · cached · non-blocking"]

    SCR --> WID
    SCR --> PRV
    WID --> PRV
    PRV --> BS & AS & RS & CS & ES
    PRV --> REPO
    BS & AS & RS & CS --> MON
    ES --> REPO
    NS --> OS
    BS -.->|"threshold breached"| NS
    ES --> OS
    CS --> FX
    REPO --> HS
    HS --> BOX
    MIG --> BOX
    REPO --> RES
    REPO --> LOG

    style DOM fill:#e8f5e9,stroke:#2e7d32
    style DATA fill:#e3f2fd,stroke:#1565c0
    style PRES fill:#fff3e0,stroke:#ef6c00
    style CORE fill:#f3e5f5,stroke:#6a1b9a
```

---

## 2.2 Key Component Interactions

There are **no API calls between components, no message queues, and no event bus infrastructure** —
everything is in-process. Four interaction mechanisms cover the whole app:

| Mechanism | Used for | Implementation |
|---|---|---|
| **Direct method call** | Provider → Service → Repository | Plain Dart, synchronous or `Future` |
| **Observer / fan-out** | Repository writes → interested Providers | `ChangeNotifier` + Hive `box.listenable()` |
| **Constructor injection** | Wiring at startup | `MultiProvider` in `main.dart` is the only composition root |
| **Platform channel** | Notifications, file system, share sheet | Plugin packages, always behind a service facade |

### Interaction rules that keep this maintainable

1. **Providers never touch Hive.** A Provider talks to a Repository or a Service. This is why the
   domain tests need no Hive at all.
2. **Repositories are the validation boundary.** Exactly like an HTTP controller: nothing gets into a
   box without passing validation, and violations return a typed `Failure`, never an untyped throw.
3. **Services are stateless.** They take data in and return data out. All mutable state lives in
   Providers (UI state) or Hive (persistent state). This makes services trivially unit-testable.
4. **Cross-feature reactions go through the notifier, not direct coupling.** `BudgetProvider`
   observes expense changes and asks `NotificationService` to alert; `ExpenseProvider` does not know
   budgets exist.

```mermaid
graph LR
    UI["UI Widget"] -->|"method call"| P["Provider<br/>ChangeNotifier"]
    P -->|"method call"| S["Domain Service<br/>stateless, pure"]
    P -->|"method call"| R["Repository"]
    R -->|"validate → put"| B[("Hive Box")]
    B -.->|"box.listenable()<br/>change event"| P
    P -.->|"notifyListeners()"| UI
    S -->|"returns computed data"| P

    BP["BudgetProvider"] -.->|"observes"| P
    BP -->|"threshold breached"| N["NotificationService"]
    N -->|"platform channel"| OS["OS notification"]

    style S fill:#e8f5e9
    style R fill:#e3f2fd
    style P fill:#fff3e0
```

---

## 2.3 Data Flow

### Write path — user logs an expense (the critical path)

```mermaid
sequenceDiagram
    actor U as User
    participant AS as AddExpenseScreen
    participant EP as ExpenseProvider
    participant ER as ExpenseRepository
    participant BX as Hive Box
    participant BP as BudgetProvider
    participant BS as BudgetService
    participant NS as NotificationService

    U->>AS: enters "12.50", category=food, taps Save
    AS->>AS: Form validation (non-empty, parseable)
    AS->>AS: Money.parse("12.50") → 1250 minor units
    AS->>EP: addExpense(draft)
    EP->>EP: state = AsyncState.loading()
    EP->>ER: create(expense)

    ER->>ER: validate: amount > 0, currency ISO-4217,<br/>categoryId exists, date not absurd
    alt validation fails
        ER-->>EP: Err(ValidationFailure)
        EP->>EP: state = AsyncState.error(msg)
        EP-->>AS: notifyListeners()
        AS-->>U: inline field error, nothing persisted
    else validation passes
        ER->>BX: put(id, expense)
        BX-->>ER: ok (synchronous in-memory + async disk flush)
        ER-->>EP: Ok(expense)
        EP->>EP: state = AsyncState.data(updatedList)
        EP-->>AS: notifyListeners()
        AS-->>U: pop + snackbar "Added"

        BX-->>BP: listenable fires
        BP->>BS: progress(budget, monthExpenses)
        BS-->>BP: BudgetProgress(spent, limit, 0.83)
        BP-->>U: progress bar animates to 83%, turns amber

        BP->>NS: maybeAlert(progress)
        NS->>NS: crossed 80% AND not already alerted this month?
        NS-->>U: local notification "83% of food budget used"
    end
```

**Note the ordering guarantee:** the write is acknowledged to the UI *before* the budget/notification
fan-out runs. A failure in notification scheduling can never fail or roll back an expense write. This
is deliberate — logging an expense is the user's goal; alerting is a side effect.

### Read path — analytics screen

```mermaid
flowchart TD
    A["AnalyticsScreen builds"] --> B["Consumer&lt;AnalyticsProvider&gt;"]
    B --> C{"AsyncState?"}
    C -->|loading| D["Shimmer skeleton"]
    C -->|error| E["Error view + Retry"]
    C -->|"data, empty"| F["Empty state:<br/>'Log an expense to see charts'"]
    C -->|"data, non-empty"| G["Render charts"]

    B -.->|"on first build / month change"| H["AnalyticsProvider.load(month)"]
    H --> I["ExpenseRepository.byDateRange(month)"]
    I --> J[("Hive Box<br/>in-memory read")]
    J --> K["List&lt;Expense&gt;"]
    K --> L["CurrencyService.convertAll(→ base)"]
    L --> M["AnalyticsService.byCategory()<br/>AnalyticsService.byWeek()"]
    M --> N["List&lt;CategorySlice&gt;<br/>List&lt;PeriodBucket&gt;"]
    N --> O["AsyncState.data(...)"]
    O --> B

    G --> P["CategoryPieChart<br/>FL Chart PieChart"]
    G --> Q["WeeklyBarChart<br/>FL Chart BarChart"]

    style J fill:#e3f2fd
    style M fill:#e8f5e9
```

**Why conversion happens before aggregation:** summing mixed-currency amounts is meaningless. All
values are normalised to the user's base currency first, then aggregated. Each `Expense` retains its
original amount *and* currency so historical records stay truthful even if rates change.

### Recurring materialisation — the catch-up problem

```mermaid
flowchart LR
    A["App resume / cold start"] --> B["RecurringService.materialiseDue(now)"]
    B --> C["for each active RecurringExpense"]
    C --> D["cursor = lastGeneratedDate ?? startDate"]
    D --> E{"cursor + frequency<br/>≤ now?"}
    E -->|no| I["done — advance nothing"]
    E -->|yes| F["emit Expense at cursor+step"]
    F --> G["cursor = cursor + step"]
    G --> H{"guard: emitted<br/>&lt; maxCatchUp?"}
    H -->|yes| E
    H -->|"no — bail"| J["log warning, clamp"]
    I --> K["batch put + update watermark"]
    J --> K

    style B fill:#e8f5e9
```

This loop is **idempotent** (re-running generates nothing new because the watermark advanced) and
**catch-up-capable** (a user who ignores the app for six weeks gets all six weekly instances, not
one). Both properties are unit-tested; both are easy to get wrong.

---

## 2.4 Scalability & Performance Strategy

"Scalability" for a local-first app is **not** about servers or throughput. There are exactly three
axes that matter, and each has a concrete budget:

### Axis 1 — Data volume growth over years of use

A heavy user logs ~15 expenses/day → ~5,500/year → ~55,000 after a decade. Each `Expense` is roughly
120 bytes on disk, so a decade is ~7 MB. Hive loads a box into memory on open.

| Volume | Strategy |
|---|---|
| < 20k records | Do nothing. Full in-memory box, O(n) scans are sub-millisecond. |
| 20k–100k | Month-partitioned reads; keep a **derived monthly-totals box** so charts never scan all history. |
| > 100k | Archive boxes per year (`expenses_2027`), lazily opened; current year stays hot. |

The important architectural move is that **`ExpenseRepository` is the only thing that knows how data
is partitioned.** Swapping to year-partitioned boxes is a change in one file, invisible to services
and UI. That is the whole point of the repository layer in an app with no network.

### Axis 2 — UI responsiveness

| Budget | Approach |
|---|---|
| Cold start → first frame < 400 ms | Only `settings` + `categories` boxes opened eagerly; expenses opened lazily after first frame |
| Scroll at 60/120 fps | `ListView.builder` with `itemExtent`; never `Column` over a full list |
| No dropped frames on chart rebuild | Aggregation is memoised per `(month, baseCurrency)` — recomputed only when inputs change |
| Rebuild scope | `Selector`/`ValueListenableBuilder` over `Consumer` at the top of the tree; a new expense repaints the list row + total, not the screen |

### Axis 3 — Feature growth without structural rot

The `domain/services/` boundary means new features (savings goals, receipt photos, split expenses)
attach as new services + new boxes without touching existing ones. The lint-enforced import rule is
what stops the classic decay into a mud-ball where a widget writes directly to a box.

### Explicit non-goals

Multi-device sync, real-time collaboration, and server-side reporting are **out of scope by design**.
Adding any of them is a v2 architectural amendment: it introduces a server of record, conflict
resolution, and auth — and it invalidates the privacy promise in §2.5. It should be a deliberate
product decision, never an incremental commit.

---

## 2.5 Security Considerations

The threat model is specific and worth stating plainly, because it differs sharply from a web app:
**there is no server to attack, no network traffic carrying user data, no credentials to steal, and no
multi-tenant data to leak across.** The realistic threats are device-local.

```mermaid
graph TD
    T1["Threat: device lost/stolen,<br/>attacker has physical access"] --> M1["OS sandbox + optional biometric app lock<br/>+ encrypted Hive box (Phase 3)"]
    T2["Threat: malicious app on<br/>rooted/jailbroken device"] --> M2["Encrypted box with key in<br/>Keystore/Keychain, not in code"]
    T3["Threat: secrets leaked<br/>in the shipped binary"] --> M3["No secrets in app; FX API key via<br/>--dart-define, never committed"]
    T4["Threat: CSV export leaks<br/>financial data"] --> M4["Export to app cache dir, share-sheet only,<br/>user-initiated, cache purged after share"]
    T5["Threat: MITM on FX rates"] --> M5["HTTPS only, cert validation on,<br/>rates are non-sensitive anyway"]
    T6["Threat: sensitive data<br/>in logs/crash reports"] --> M6["AppLogger redacts amounts+notes in release;<br/>no PII to crash reporter"]

    style T1 fill:#ffebee
    style T2 fill:#ffebee
    style T3 fill:#ffebee
    style T4 fill:#ffebee
    style T5 fill:#ffebee
    style T6 fill:#ffebee
```

### Authentication & authorization

**There is no user authentication, and adding it would be a security regression** — an account
creates a remote attack surface and a data-breach liability where none currently exists. Authorization
is delegated entirely to the OS: the app's Hive files live in the app-private sandbox
(`getApplicationDocumentsDirectory()`), unreadable by other apps under normal Android/iOS
permissions.

The one legitimate addition is a **local re-authentication gate**: optional biometric/PIN app lock via
`local_auth` (Phase 3), guarding against casual access to an unlocked device. This is a UX gate, not
a cryptographic boundary — the crypto boundary is box encryption below.

### Data protection

- **At rest, baseline:** OS full-disk encryption + app sandbox. Adequate for most users.
- **At rest, hardened (Phase 3):** `Hive.openBox(encryptionCipher: HiveAesCipher(key))` where the
  256-bit key is generated once, stored in Android Keystore / iOS Keychain via
  `flutter_secure_storage`, and **never** hardcoded, logged, or exported. Note the migration
  consequence: enabling encryption on an existing box requires read-plaintext → write-encrypted →
  delete-plaintext, which belongs in `HiveMigrator` as a versioned step.
- **In transit:** only FX rates. HTTPS with default certificate validation; never disable it.
- **In backups:** consider excluding boxes from iCloud/Android auto-backup if the encryption key is
  device-bound — otherwise a restored backup yields an undecryptable box. Document the choice.

### API security

The only outbound call is `GET /latest?base=USD` to a public FX endpoint. Rules:

1. Treat the response as **untrusted input** — validate shape and types, reject non-finite or
   negative rates, and never `as`-cast blindly into a model.
2. **Never block the UI on it.** Timeout ≤5 s, fall back to cached rates, surface staleness.
3. If the provider requires a key, it is a **build-time** `--dart-define`, and it is understood to be
   extractable from the binary — so use a key with rate-limit-only scope, never a billable secret.

### Secret management

| Secret | Where it lives | Never |
|---|---|---|
| FX API key (if any) | Codemagic env var → `--dart-define` | in git, in `pubspec.yaml`, in a committed `.env` |
| Android upload keystore | Codemagic signing config | in the repo |
| iOS certs / provisioning | Codemagic code-signing (App Store Connect API key) | in the repo |
| Hive encryption key | Device Keystore/Keychain, generated on device | in source, in CI, in backups |

`.env` is git-ignored; only `.env.example` (placeholder values) is committed.

---

## 2.6 Error Handling & Logging Philosophy

### Principle: fail loudly in development, degrade gracefully in production, never silently

```mermaid
flowchart TD
    A["Operation"] --> B{"Recoverable?"}
    B -->|"No — programmer error<br/>(bad typeId, broken invariant)"| C["assert / throw StateError"]
    C --> D["DEBUG: crash loudly"]
    C --> E["RELEASE: caught by global handler,<br/>logged, generic user message"]

    B -->|"Yes — expected condition<br/>(bad input, missing file, network down)"| F["return Err(Failure)"]
    F --> G["Repository / Service returns<br/>Result&lt;T&gt;, does NOT throw"]
    G --> H["Provider maps Failure →<br/>AsyncState.error(userMessage)"]
    H --> I["AsyncValueView renders<br/>message + Retry"]

    J["Uncaught Flutter error"] --> K["FlutterError.onError"]
    L["Uncaught async error"] --> M["PlatformDispatcher.instance.onError"]
    K & M --> N["ErrorHandler.recordFatal:<br/>log, breadcrumb, optional crash report"]

    style F fill:#e8f5e9
    style C fill:#ffebee
```

### The two-category rule

Every error is classified once, at the point it arises:

**Category A — expected conditions** (invalid user input, no data yet, FX API unreachable, export
cancelled). These are **values, not exceptions**: functions return `Result<T>` = `Ok(T) | Err(Failure)`.
`Failure` is a sealed hierarchy (`ValidationFailure`, `StorageFailure`, `NetworkFailure`,
`PermissionFailure`, `UnexpectedFailure`) so exhaustive `switch` gives compile-time proof that every
case is handled — including new ones added later.

**Category B — programmer errors** (unregistered adapter, negative money constructed internally,
impossible enum state). These `throw` and are allowed to crash in debug. Swallowing them is how a bug
becomes a mystery three releases later.

### Logging rules

| Level | Use | Ships in release? |
|---|---|---|
| `trace` | Loop internals, cursor advances | No — stripped |
| `debug` | Box opened, migration step ran | No |
| `info` | App start, schema version, recurrences materialised (count only) | Yes |
| `warn` | Stale FX rates, notification permission denied, catch-up clamped | Yes |
| `error` | Failed write, migration failure, uncaught error | Yes |

**Redaction is mandatory and enforced in `AppLogger`:** in release builds, expense amounts and note
text are never logged — they are the most sensitive data the app holds. Log the *shape* of the
problem (`"write failed for expense id=…, box=expenses"`), never the values. A log line that would
embarrass the user if screenshotted does not belong in release.

### User-facing error contract

1. Never show a raw exception, stack trace, or `Failure` class name to the user.
2. Every error view offers either a **Retry** or a clear next action.
3. Errors that lose user input are unacceptable — the add-expense form retains its state on failure.
4. A failed side effect (notification, FX refresh) must never present as a failed primary action.
