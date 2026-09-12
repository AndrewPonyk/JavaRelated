# Event Processing Pipeline — Project Plan

> Three-stage, in-process event pipeline built on Java 21 concurrency primitives.
> No Docker, no cloud services, no external broker — everything runs locally and is
> verified with JUnit 5.

---

## 1. Scope & Non-Goals

| | |
|---|---|
| **Goal** | Demonstrate a correct, observable, gracefully-shutting-down producer→filter→aggregate pipeline with bounded backpressure. |
| **Runtime** | Single JVM, `java -jar target/event-processing-pipeline-1.0.0-SNAPSHOT.jar` |
| **Build** | Maven (`mvn verify`) — Checkstyle → Surefire → JaCoCo → JAR |
| **Non-goals** | Distributed processing, exactly-once semantics across restarts, Kafka/RabbitMQ, containers, Kubernetes, web SPA frontend. |

### Functional requirements

1. **FR-1** Generate synthetic sensor events at a *configurable rate* (events/second) for a configurable duration or event count.
2. **FR-2** Batch events into configurable-size batches before enqueueing (amortises queue contention).
3. **FR-3** Filter events against a *threshold* predicate using a **fixed thread pool** of N consumer threads.
4. **FR-4** Aggregate surviving events **in parallel** with a `ForkJoinPool` (`RecursiveTask` divide-and-conquer) into per-sensor count/min/max/sum/avg.
5. **FR-5** Apply **backpressure**: bounded `ArrayBlockingQueue` between every stage; a slow downstream must throttle upstream, never drop events silently.
6. **FR-6** **Graceful shutdown** via the *poison pill* pattern — every in-flight event is drained and accounted for; exit code reflects success.
7. **FR-7** Emit a metrics snapshot (produced / filtered-in / filtered-out / aggregated / queue depths / offer-blocked time) and reconcile the counters at the end.

### Quality requirements

| ID | Requirement | Verified by |
|---|---|---|
| QR-1 | No event lost or double-counted: `produced == passed + rejected` | `PipelineOrchestratorTest#reconcilesEveryEvent` (repeated) |
| QR-2 | `Ctrl+C` drains what is already queued and loses nothing | `PipelineOrchestratorTest#stoppingTheProducerDrainsCleanly`, `#requestStopAllDrainsCleanly` |
| QR-2b | `shutdown.timeout.seconds` bounds the *drain*, never the run: a pipeline that stops moving events is reported as stalled, a long healthy run is not | `PipelineOrchestratorTest#aStallIsReported`, `#outlivesTheShutdownBudget` |
| QR-3 | Bounded memory: queue capacity is a hard cap | `BoundedStageQueueTest#putBlocksUntilSpaceAppears` |
| QR-4 | ≥ 85 % line and ≥ 80 % branch coverage over **every** package, not just `domain` + `application` — currently **92.5 %** line (1 589 of 1 718) and **90.6 %** branch, over 597 tests | `jacoco:check` bound to `verify` — **gated**, so the build fails rather than the number quietly sliding. The scope widened in Phase 3: the original requirement covered `domain` + `application` only and read 88.9 %, which flattered the project by excluding exactly the adapters where the untested code actually was (the control plane, the CSV sink's write path, the CLI entry point). The threshold sits below the measured figure on purpose — a gate that trips on any refactor gets raised until it means nothing. |
| QR-5 | Zero Checkstyle violations (build fails otherwise) | `mvn validate` |
| QR-6 | Deterministic tests — no `Thread.sleep`-based assertions in unit tests | Code review |

---

## 2. Project File Structure

```text
3-Event-Processing-Pipeline/
├── pom.xml                                  # Maven build: Java 21, JUnit 5, Checkstyle, JaCoCo
├── README.md                                # Quick start, CLI flags, sample output
├── checkstyle.xml                           # Lint rules (fails the build on violation)
├── .editorconfig                            # Whitespace/encoding contract for all editors
├── .gitignore
├── .env.example                             # Documented env-var overrides (PIPELINE_*)
├── claude-opus-5.txt                        # Model marker file
│
├── .github/
│   ├── dependabot.yml                       # Weekly Maven + Actions dependency PRs
│   └── workflows/
│       ├── ci.yml                           # lint → test → package → upload artifacts
│       └── release.yml                      # tag-driven release (stub)
│
├── config/                                  # Externalised per-environment overrides
│   ├── application-dev.properties           # Chatty, tiny queues, fast feedback
│   ├── application-staging.properties       # Production-like sizing, soak-test friendly
│   └── application-prod.properties          # Conservative, quiet, metrics-only output
│
├── docs/
│   ├── PROJECT-PLAN.md                      # ← this file
│   ├── ARCHITECTURE.md                      # Patterns, diagrams, data flow, security
│   └── TECH-NOTES.md                        # CI/CD, testing, deployment, pitfalls
│
├── migrations/                              # Optional persistence (JDBC sink stub)
│   ├── README.md                            # How migrations are applied (manual/Flyway)
│   ├── V001__create_pipeline_tables.sql     # run + aggregate tables
│   └── V002__add_indexes.sql                # Query-path indexes
│
├── scripts/
│   ├── run.ps1                              # Windows launcher (JAVA_HOME aware)
│   ├── run.sh                               # POSIX launcher
│   └── soak.sh                              # Long-running stability run
│
└── src/
    ├── main/
    │   ├── java/com/example/pipeline/
    │   │   ├── domain/                       # Pure data + invariants, zero dependencies
    │   │   │   ├── SensorEvent.java          # record: id, sensorId, type, value, unit, timestamp
    │   │   │   ├── SensorType.java           # enum: TEMPERATURE, PRESSURE, HUMIDITY, VIBRATION
    │   │   │   ├── EventBatch.java           # record: batchId + immutable List<SensorEvent>
    │   │   │   ├── PipelineMessage.java      # sealed interface: EventBatch | PoisonPill
    │   │   │   ├── PoisonPill.java           # singleton shutdown sentinel
    │   │   │   ├── AggregateResult.java      # per-sensor count/min/max/sum, mergeable
    │   │   │   ├── AggregateSnapshot.java    # immutable map of sensorId → AggregateResult
    │   │   │   ├── MetricsSnapshot.java      # consistent-enough copy of the counters
    │   │   │   └── StageStats.java           # per-stage tallies returned by each stage
    │   │   │
    │   │   ├── application/                  # Use cases; depends only on domain + ports
    │   │   │   ├── PipelineOrchestrator.java # Wires stages, CompletableFuture lifecycle
    │   │   │   ├── PipelineReport.java       # Final result object (stats + snapshot)
    │   │   │   ├── port/                     # Hexagonal boundaries (interfaces only)
    │   │   │   │   ├── EventGenerator.java
    │   │   │   │   ├── EventPredicate.java
    │   │   │   │   ├── MessageChannel.java       # what a stage sees of a queue
    │   │   │   │   ├── RateLimiter.java
    │   │   │   │   ├── StageLifecycle.java       # pool ownership, kept out of application
    │   │   │   │   ├── AggregateSink.java
    │   │   │   │   ├── AggregateRepository.java
    │   │   │   │   └── MetricsRecorder.java
    │   │   │   ├── filter/
    │   │   │   │   └── ThresholdPredicate.java   # per-SensorType threshold rules
    │   │   │   └── stage/
    │   │   │       ├── ProducerStage.java        # Stage 1 — rate-limited batch producer
    │   │   │       ├── FilterStage.java          # Stage 2 — fixed pool of consumers
    │   │   │       ├── AggregationStage.java     # Stage 3 — ForkJoinPool dispatcher
    │   │   │       ├── AggregationOutcome.java   # stats + snapshot, stage 3's return
    │   │   │       └── AggregationTask.java      # RecursiveTask divide-and-conquer
    │   │   │
    │   │   ├── infrastructure/               # Adapters: concurrency, IO, config, HTTP
    │   │   │   ├── config/
    │   │   │   │   ├── PipelineConfig.java   # Validated, immutable config + builder
    │   │   │   │   ├── ConfigurationException.java  # carries the offending key
    │   │   │   │   └── ConfigLoader.java     # defaults ← file ← env ← CLI precedence
    │   │   │   ├── queue/
    │   │   │   │   └── BoundedStageQueue.java    # ArrayBlockingQueue + pill + metrics
    │   │   │   ├── concurrent/
    │   │   │   │   ├── NamedThreadFactory.java   # Readable thread names in dumps
    │   │   │   │   └── PipelineExecutors.java    # Pool factory + orderly shutdown
    │   │   │   ├── generator/
    │   │   │   │   ├── SyntheticSensorEventGenerator.java
    │   │   │   │   └── TokenBucketRateLimiter.java
    │   │   │   ├── metrics/
    │   │   │   │   └── AtomicMetricsRecorder.java  # LongAdder counters
    │   │   │   ├── sink/
    │   │   │   │   ├── CompositeAggregateSink.java # fan-out, one bad sink cannot kill
    │   │   │   │   ├── ConsoleAggregateSink.java
    │   │   │   │   └── CsvAggregateSink.java
    │   │   │   ├── persistence/
    │   │   │   │   ├── InMemoryAggregateRepository.java
    │   │   │   │   └── JdbcAggregateRepository.java # stub (TODO)
    │   │   │   └── http/                            # Optional local control plane
    │   │   │       ├── ControlPlaneServer.java      # jdk.httpserver, no deps
    │   │   │       ├── PipelineControlHandler.java  # GET/POST + validation
    │   │   │       └── Json.java                    # Minimal JSON writer
    │   │   │
    │   │   └── presentation/cli/
    │   │       ├── PipelineApplication.java  # main(): parse → run → report → exit code
    │   │       ├── CliArguments.java         # --key=value parser + --help
    │   │       ├── ConsoleDashboard.java     # Live view: loading/error/data states
    │   │       ├── LoggingSupport.java       # installs the formatter + level
    │   │       └── PipelineLogFormatter.java # one-line, thread-named log records
    │   │
    │   └── resources/
    │       ├── application.properties        # Baseline defaults
    │       └── logging.properties            # java.util.logging config
    │
    └── test/java/com/example/pipeline/            # 32 test classes, 597 tests
        ├── TestEvents.java                        # shared event/batch builders (not a test)
        ├── application/
        │   ├── PipelineOrchestratorTest.java      # end-to-end, graceful shutdown, stall
        │   ├── PipelineReportTest.java
        │   ├── filter/ThresholdPredicateTest.java
        │   ├── port/PortDefaultsTest.java         # the no-op/accept-all default impls
        │   └── stage/
        │       ├── AggregationOutcomeTest.java
        │       ├── AggregationTaskTest.java       # split logic + parallel≡sequential
        │       └── FilterStageTest.java           # pill fan-out, parameterised 1/2/4/8
        ├── domain/
        │   ├── AggregateResultTest.java           # monoid laws
        │   ├── SensorEventTest.java
        │   ├── SensorTypeTest.java
        │   └── StageStatsTest.java
        ├── infrastructure/
        │   ├── concurrent/{NamedThreadFactory,PipelineExecutors}Test.java
        │   ├── config/{ConfigLoader,PipelineConfig}Test.java   # precedence, unknown keys
        │   ├── generator/SyntheticSensorEventGeneratorTest.java
        │   ├── generator/TokenBucketRateLimiterTest.java       # injected clock, no sleeping
        │   ├── http/{ControlPlaneServer,PipelineControlHandler,Json}Test.java
        │   │                                      # a real server per test; ~39 s of the suite
        │   ├── metrics/AtomicMetricsRecorderTest.java
        │   ├── persistence/{InMemory,Jdbc}AggregateRepositoryTest.java
        │   ├── queue/BoundedStageQueueTest.java   # backpressure, pills, poll timeout
        │   └── sink/{Composite,Console,Csv}AggregateSinkTest.java
        │                                          # Csv: containment, locale, crash safety
        └── presentation/cli/
            ├── CliArgumentsTest.java
            ├── ConsoleDashboardTest.java
            ├── LoggingSupportTest.java
            ├── PipelineApplicationTest.java       # run() without System.exit
            └── PipelineLogFormatterTest.java
```

> The `{A,B}Test.java` brace notation above is shorthand for two or three sibling files, not
> a filename. Written out the tree is a page longer and no clearer.

> `MetricsSnapshot` sits in `domain`, not in `infrastructure/metrics` as first drafted: the
> `MetricsRecorder` port returns one, so putting it beside its adapter would have made
> `application` import `infrastructure` and inverted the dependency rule below.

### Layer dependency rule

```text
presentation ──► application ──► domain
       │              ▲
       └──► infrastructure ┘        (infrastructure implements application.port)
```

`domain` imports nothing outside the JDK. `application` never imports
`infrastructure`. Violations are visible as an import of `...infrastructure...`
inside `application/` — grep-able, and cheap to enforce in review.

---

## 3. Implementation TODO List

### Phase 1 — Foundation (high priority) ✅

- [x] `pom.xml`: Java 21 release, JUnit 5, Surefire, Checkstyle (fail-on-violation), JaCoCo, executable JAR manifest
- [x] `checkstyle.xml`, `.editorconfig`, `.gitignore`
- [x] `domain`: `SensorEvent`, `SensorType`, `EventBatch`, `AggregateResult`, `AggregateSnapshot`, `StageStats`
- [x] Sealed `PipelineMessage` hierarchy (`EventBatch` | `PoisonPill`) so the queue is type-safe instead of using `null`/`instanceof Object` sentinels
- [x] `PipelineConfig` + `ConfigLoader` with validation and defaults ← file ← env ← CLI precedence
- [x] `BoundedStageQueue` wrapper over `ArrayBlockingQueue` (capacity, blocked-time metric, pill helpers)
- [x] `NamedThreadFactory` + `PipelineExecutors`
- [x] `AtomicMetricsRecorder` (LongAdder) + `MetricsSnapshot`
- [x] `mvn verify` green on a clean checkout

### Phase 2 — Core features (medium priority) ✅

- [x] `SyntheticSensorEventGenerator` + `TokenBucketRateLimiter` (configurable events/sec, deterministic via seed)
- [x] `ProducerStage`: batch assembly, bounded `put`, N poison pills on completion
- [x] `ThresholdPredicate`: per-`SensorType` threshold rules, null-safe
- [x] `FilterStage`: fixed `ExecutorService`, one consumer task per thread, pill fan-out to stage 3 by the *last* consumer only
- [x] `AggregationTask` (`RecursiveTask<Map<String, AggregateResult>>`) with sequential-cutoff threshold
- [x] `AggregationStage`: dispatcher thread submits to `ForkJoinPool`, merges results
- [x] `PipelineOrchestrator`: `CompletableFuture` per stage, `allOf` join, stall detection on the monotonic counter sum, `shutdownNow` fallback, counter reconciliation
- [x] `PipelineApplication` + `CliArguments` + `ConsoleDashboard`
- [x] `ConsoleAggregateSink`, `CsvAggregateSink` (path-contained), `CompositeAggregateSink`, `InMemoryAggregateRepository`
- [x] Unit + integration tests for all of the above (11 test classes, 201 tests)

### Phase 3 — Polish & optimisation (lower priority)

- [x] `.github/workflows/ci.yml` (lint → test → package → artifacts) and `release.yml` stub
- [x] Per-environment property files (`dev`/`staging`/`prod`) + `.env.example`
- [x] Optional zero-dependency HTTP control plane (`jdk.httpserver`) — metrics read + threshold update with validation
- [x] `migrations/` DDL for the optional JDBC aggregate sink
- [x] Make `CsvAggregateSink` crash-safe: render and validate every row **first**, then write a uniquely-named temp file *in the target's own directory* (`ATOMIC_MOVE` cannot cross filesystems) and move it into place, falling back to a logged non-atomic replace on `AtomicMoveNotSupportedException`. The render-first ordering is the half that is easy to miss: with it reversed, one unrenderable sensor id truncated a perfectly good report on its way to throwing. `CsvAggregateSinkTest.CrashSafety` pins both halves
- [x] Fail fast on a configured-but-unimplemented `pipeline.jdbc.url`: exit 2 *before any thread starts*, naming the key to unset and the in-memory alternative. Previously such a run did all its work correctly, wrote the CSV, and only then failed on `save` — reporting FAILED and exit 1 for a run that had in fact succeeded, which is the worst of the available outcomes
- [x] Gate coverage rather than merely report it: `jacoco:check` at 85 % line / 80 % branch over the whole bundle, and widen the tests until it passes with headroom (**92.5 %** line over 597 tests, 32 test classes — up from 53.5 % when the audit started). See QR-4
- [ ] **TODO** Implement `JdbcAggregateRepository` (batch upsert, `PreparedStatement` reuse, H2/SQLite tested). The fail-fast guard above is the *placeholder* for this, not a substitute: it makes the gap loud instead of closing it
- [ ] **TODO** Add JMH microbenchmarks comparing batch sizes 1 / 32 / 256 / 1024 and queue capacities
- [ ] **TODO** Add a `VirtualThreadFilterStage` variant and benchmark it against the fixed pool (Java 21 `Executors.newVirtualThreadPerTaskExecutor`)
- [ ] **TODO** Emit JFR events (`jdk.jfr.Event`) per stage for `jfr print` post-mortem analysis
- [ ] **TODO** Add a `--drop-oldest` overflow policy as an alternative to blocking backpressure
- [ ] **TODO** Property-based tests (jqwik) for `AggregateResult.merge` associativity/commutativity
- [ ] **TODO** Mutation testing with PIT on `domain` + `application`, target ≥ 70 % mutation score
- [ ] **TODO** GraalVM native-image build profile for sub-50 ms startup

### Definition of Done (per task)

1. Code compiles under `-Xlint:all` with no new warnings.
2. Checkstyle clean.
3. New behaviour covered by a test that fails before the change.
4. `mvn verify` green locally *and* in CI.
5. Public API has Javadoc explaining **thread-safety** and **blocking behaviour**.

---

## 4. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Deadlock: consumer blocks on a full downstream queue while producer blocks on a full upstream queue | Medium | High | Queues are strictly ordered stage1→stage2→stage3; no stage ever writes upstream. Shutdown uses `poll(timeout)` not `take()`. |
| Poison pill consumed by the wrong thread, leaving a consumer blocked forever | Medium | High | Producer emits exactly `consumerCount` pills; each consumer exits on first pill and never re-offers it upstream. Verified by `FilterStageTest`. |
| Lost events at shutdown | Medium | High | Counter reconciliation (`produced == passed + rejected`) asserted in tests and logged at exit. |
| **`Ctrl+C` stops the consumers while queue #1 still holds batches** — those events are lost *and* the producer wedges in `put()` against a queue nobody drains, so a clean stop reports as a forced shutdown | Medium | High | **Happened.** `requestStopAll()` now stops *only* the producer, at the head of the graph: its pills travel behind the queued data and cascade. The all-stages stop is private and reserved for runs that have already failed. Verified by `PipelineOrchestratorTest#requestStopAllDrainsCleanly`. |
| **The drain budget is mistaken for a run deadline**, killing a healthy long run and printing `FAILED` above `reconciled=true` | Medium | High | **Happened** (the shipped defaults sat exactly on the boundary). The wait now watches *liveness*: the monotonic sum of the event/batch counters. An unchanged sum for the whole budget is a stall; a run that keeps moving runs as long as its own bounds say. Verified by `#outlivesTheShutdownBudget` and `#aStallIsReported`. |
| Flaky timing-based tests | High | Medium | Unit tests use `CountDownLatch`/`CompletableFuture`, never `sleep`, and assert on the *verdict* rather than on elapsed time — no test contains a "finished within N ms" bound, so a loaded CI runner cannot fail one. `@Timeout` guards the hang, which is how a pipeline bug actually presents. |
| `ForkJoinPool.commonPool()` starvation from blocking work | Low | Medium | A dedicated `ForkJoinPool` is created for aggregation; aggregation tasks are CPU-only and never block on IO. |
| Unbounded memory from a large batch size × queue capacity | Low | Medium | Bounded by construction, not by a check: each queue holds at most `queueCapacity` messages of at most `batchSize` events, so in-flight events never exceed `2 × queueCapacity × batchSize` regardless of the production rate. Both factors are validated `≥ 1`; there is deliberately no upper-bound validation, because the operator sizing the queues is the one who knows the heap. |
