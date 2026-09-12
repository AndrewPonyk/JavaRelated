# Stock Trading Simulator - Completed Project Plan

## 1. Delivered scope

The application is a complete local Java 21 layered monolith. It simulates 10,000 concurrent traders with virtual threads, performs price/time-priority matching and partial fills, prevents self-trades, reserves buying power and inventory, atomically settles both portfolios, records immutable trades and audit events, and calculates marked profit in a dedicated ForkJoinPool.

The persistence boundary is deliberately in-memory. There is no HTTP server, browser frontend, external database, migration tool, message broker, or container because none is applicable to the Java SE/local-execution target. The CLI is the presentation adapter and TradingSimulator is the complete application API.

## 2. Project structure

~~~text
Stock-Trading-Simulator/
|-- .github/workflows/ci.yml
|-- config/
|   |-- application-dev.properties
|   |-- application-staging.properties
|   +-- application-prod.properties
|-- docs/
|   |-- PROJECT-PLAN.md
|   |-- ARCHITECTURE.md
|   |-- TECH-NOTES.md
|   |-- API.md
|   +-- PRODUCTION-AUDIT.md
|-- scripts/profile.ps1
|-- src/
|   |-- main/
|   |   |-- java/com/example/trading/
|   |   |   |-- application/
|   |   |   |   |-- exception/
|   |   |   |   +-- port/
|   |   |   |-- domain/
|   |   |   |-- infrastructure/
|   |   |   |   |-- concurrent/
|   |   |   |   |-- config/
|   |   |   |   +-- marketdata/
|   |   |   +-- presentation/cli/
|   |   +-- resources/application.properties
|   +-- test/java/com/example/trading/
|       |-- application/
|       |-- domain/
|       |-- infrastructure/
|       |-- integration/
|       +-- presentation/cli/
|-- .editorconfig
|-- .env.example
|-- .gitignore
|-- checkstyle.xml
|-- gpt-5.txt
|-- pom.xml
+-- README.md
~~~

## 3. Implemented entity and API surface

| Entity | Operations |
|---|---|
| Order | Place, read, list, replace through cancel-and-resubmit, cancel; immutable history remains queryable. |
| Portfolio | Create, read, list, update, and delete; mutation is blocked while reservations are active. |
| Price | Async fetch/read, cache list, explicit update, and delete. |
| Trade | System-created immutable ledger entries; read and list. Arbitrary update/delete is prohibited by design. |
| Audit event | System-created append-only lifecycle events; list in sequence. |

## 4. Completion checklist

### Phase 1 - Foundation

- [x] Establish Maven/Java 21 layout and package boundaries.
- [x] Define immutable validated domain records.
- [x] Generate unique order IDs with AtomicLong.
- [x] Implement optimistic price-cache reads with StampedLock.
- [x] Implement concurrent portfolio storage with ConcurrentHashMap.
- [x] Protect compound order-book mutation with a fair ReentrantLock.
- [x] Test invariants for positive quantities, unique IDs, immutable snapshots, and conserved matched quantity.
- [x] Add Checkstyle, JUnit Jupiter, JaCoCo, Maven Enforcer, and GitHub Actions.

### Phase 2 - Trading core

- [x] Coalesce asynchronous price misses and enforce request deadlines with CompletableFuture.
- [x] Run client tasks with a virtual-thread-per-task executor.
- [x] Calculate aggregate marked profit in an owned ForkJoinPool.
- [x] Implement price/time-priority matching and partial-fill accounting.
- [x] Reserve buying power/inventory before acceptance.
- [x] Settle buyer and seller portfolios atomically after matching.
- [x] Implement order cancellation races, lifecycle transitions, self-trade prevention, and audit events.
- [x] Add deterministic synthetic market data.
- [x] Add a bounded 10,000-client load test with elapsed and p95 latency metrics.

### Phase 3 - Quality and operations

- [x] Measure the complete workflow with a repeatable load harness; end-to-end timing is more relevant here than an isolated JMH microbenchmark.
- [x] Expose optimistic/fallback cache-read counters and use a configurable fair order-book lock.
- [x] Provide a Java Flight Recorder profiling script.
- [x] Enforce at least 80% line coverage; the completed suite measures 89.9%.
- [x] Verify graceful executor/pool shutdown and bounded waits through end-to-end tests.
- [x] Document the optional persistence/HTTP extension boundary without coupling it to the core.
- [x] Confirm structured network logging, authentication, migrations, and container deployment are not applicable until a network adapter exists.
- [x] Lock portfolio reads against two-account settlement so snapshots cannot observe half a transaction.
- [x] Make explicit price updates/deletes win over stale in-flight provider fetches.
- [x] Validate trade batches before append, add immutable pagination, and bound all external configuration.
- [x] Enforce Java 21, Maven 3.9+, dependency convergence/usage, compiler warnings, and expanded style rules.
- [x] Add dependency-change review, API documentation, troubleshooting, and a production audit.

## 5. Acceptance evidence

| Milestone | Evidence |
|---|---|
| Foundation | mvn verify compiles with Java 21, runs Checkstyle, and produces a runnable JAR. |
| Trading core | Integration tests verify partial fills, price/time priority, reservations, settlement, cancellation, and value conservation. |
| Scale | TenThousandClientLoadTest completes 10,000 virtual-client tasks within a bounded timeout. |
| Quality | 27 JUnit tests pass; JaCoCo covers 919/1,022 lines (89.9%) and the build gate is 80%. |

## 6. Definition of done

All public inputs are validated, expected business failures use specific exceptions, concurrency invariants have bounded tests, resource ownership is explicit, no work is performed outside the documented architectural boundary, and mvn clean verify is the single release gate.
