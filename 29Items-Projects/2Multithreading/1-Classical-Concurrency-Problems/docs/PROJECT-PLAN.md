# Classical Concurrency Problems — Project Plan

## 1. Purpose and scope

This repository is a Java 21 SE concurrency laboratory. Each classical problem lives in an independent package and exposes a small in-process API, one or more synchronization strategies, a runnable demonstration, and deterministic JUnit tests. The project intentionally has no web frontend, HTTP backend, database, container, or cloud deployment: none is required for local demonstrations of JVM synchronization primitives.

### Scope decisions

| Generic application concern | Decision for this project |
|---|---|
| Frontend | Not applicable; demonstrations run from a Java `main` method and tests. |
| Backend/API | Replaced by typed, in-process Java scenario APIs. No network boundary is needed. |
| Shared module | `common` package contains interruption and simulation-result conventions. |
| Database/migrations | Not applicable; scenario state is deliberately in memory and process-local. |
| Messaging/event bus | Not applicable; threads coordinate directly through Java concurrency primitives. |
| Configuration | Maven, Checkstyle, EditorConfig, environment template, and GitHub Actions. |
| Deployment | Local JAR execution and CI build artifact; no Docker image. |

## 2. Project file structure

```text
Classical-Concurrency-Problems/
├── .github/
│   └── workflows/
│       └── ci.yml                         # Java 21 build, tests, checks, artifact
├── config/
│   └── checkstyle/
│       └── checkstyle.xml                 # Static style policy
├── docs/
│   ├── PROJECT-PLAN.md                    # Scope, structure, phased work
│   ├── ARCHITECTURE.md                    # Architecture and interaction diagrams
│   └── TECH-NOTES.md                      # Build, test, release, and operations advice
├── src/
│   ├── main/java/com/example/concurrency/
│   │   ├── common/
│   │   │   ├── Interruptions.java         # Consistent interruption handling
│   │   │   ├── PhasedSimulation.java       # Reusable Phaser phase gate
│   │   │   └── SimulationResult.java      # Immutable run summary
│   │   ├── dining/
│   │   │   ├── DiningTable.java           # Dining strategy contract
│   │   │   ├── DeadlockDiningTable.java   # Intentional circular-wait demonstration
│   │   │   ├── ResourceHierarchyDiningTable.java
│   │   │   └── ArbitratorDiningTable.java
│   │   ├── barber/
│   │   │   └── SleepingBarberShop.java    # Multiple barbers and bounded waiting room
│   │   ├── readerswriters/
│   │   │   ├── SharedDocument.java        # Read/write contract
│   │   │   ├── ReaderPriorityDocument.java# synchronized/wait/notify variant
│   │   │   ├── ReadWriteLockDocument.java # Fair and non-fair ReadWriteLock variants
│   │   │   └── WriterPriorityDocument.java# Explicit writer-priority Conditions
│   │   ├── producerconsumer/
│   │   │   ├── BoundedBuffer.java         # ReentrantLock plus notEmpty/notFull
│   │   │   └── BatchExchanger.java         # Two-party Exchanger handoff
│   │   ├── smokers/
│   │   │   └── CigaretteSmokers.java      # Agent/smoker Semaphore coordination
│   │   ├── h2o/
│   │   │   └── WaterMoleculeBuilder.java  # Two H + one O with CyclicBarrier
│   │   ├── santa/
│   │   │   └── SantaWorkshop.java         # Latch and Semaphore group rendezvous
│   │   ├── bathroom/
│   │   │   └── UnisexBathroom.java        # Fair admission and starvation prevention
│   │   ├── deadlock/
│   │   │   ├── BankAccount.java           # Account aggregate with explicit lock
│   │   │   ├── BankTransferService.java   # Unsafe, ordered, and timed transfers
│   │   │   └── DeadlockDetector.java      # ThreadMXBean-based deadlock inspection
│   │   └── demo/
│   │       └── ConcurrencyDemo.java        # Safe command-line sample runner
│   └── test/java/com/example/concurrency/
│       ├── dining/DiningTableTest.java
│       ├── barber/SleepingBarberShopTest.java
│       ├── readerswriters/SharedDocumentTest.java
│       ├── producerconsumer/BoundedBufferTest.java
│       ├── smokers/CigaretteSmokersTest.java
│       ├── h2o/WaterMoleculeBuilderTest.java
│       ├── santa/SantaWorkshopTest.java
│       ├── bathroom/UnisexBathroomTest.java
│       └── deadlock/BankTransferServiceTest.java
├── .editorconfig                          # Cross-editor whitespace policy
├── .env.example                           # Explicit zero-environment-variable contract
├── .gitattributes                         # Stable line endings and text handling
├── .gitignore                             # Maven and IDE outputs
├── pom.xml                                # Java 21, JUnit 6, plugins, executable JAR
├── README.md                              # Build, run, and problem catalog
└── gpt-5.txt                              # Requested empty model marker
```

### Package boundaries

Each problem package owns its mutable state and synchronization policy. Packages do not share locks, semaphores, executors, or mutable collections. Cross-cutting code in `common` is stateless. This keeps examples isolated, makes tests independent, and lets a new solution be added without changing unrelated scenarios.

Every concrete strategy has a sibling `*Demo.java` with its own `main()` method. The
central `ConcurrencyDemo` dispatches the same demos from the executable JAR and provides
an `all-safe` command that excludes only the two deliberate-deadlock workloads.

### CI/CD and tooling

| Area | File | Responsibility |
|---|---|---|
| Continuous integration | `.github/workflows/ci.yml` | Compile on Java 21, run style checks and tests, package JAR, upload artifact. |
| Build | `pom.xml` | Reproducible compiler, test, Checkstyle, JaCoCo, and JAR configuration. |
| Style | `config/checkstyle/checkstyle.xml` | Small, teachable set of Java style rules. |
| Editors | `.editorconfig`, `.gitattributes` | UTF-8, line endings, indentation, final newlines. |
| Local environment | `.env.example` | Documents that no environment variables or secrets are consumed. |
| Containers | None | Explicitly excluded because local Java execution is the deployment target. |

## 3. Implementation TODO list

### Phase 1: Foundation — high priority

- [ ] Confirm Java 21 and Maven 3.9+ are installed locally.
- [x] Establish the Maven build, warning-as-error compilation, JUnit 6, Checkstyle, and JaCoCo.
- [ ] Define package-local ownership rules for mutable state and synchronization objects.
- [ ] Add reusable interruption handling and immutable result types.
- [ ] Implement bounded waits in tests so a regression fails instead of hanging CI.
- [ ] Add GitHub Actions for clean Java 21 builds.
- [ ] Document how to capture thread dumps with `jcmd` and `jstack`.

### Phase 2: Core features — medium priority

- [ ] Complete Dining Philosophers deadlock, resource-hierarchy, and arbitrator scenarios.
- [ ] Complete Sleeping Barber with multiple barbers and bounded waiting-room admission.
- [ ] Complete fair/non-fair ReadWriteLock and explicit writer-priority variants.
- [ ] Complete bounded Producer-Consumer with multiple producers and consumers.
- [ ] Complete Semaphore-based Cigarette Smokers coordination.
- [ ] Complete H2O grouping with exactly two hydrogen threads and one oxygen thread per barrier generation.
- [ ] Complete Santa Claus grouping for nine reindeer and groups of three elves.
- [ ] Complete fair Unisex Bathroom admission with capacity and starvation prevention.
- [ ] Complete unsafe bank transfer, ordered locking, timed `tryLock`, and JVM deadlock detection.
- [ ] Add repeatable tests for safety invariants, progress, interruption, shutdown, and accounting.

### Phase 3: Polish and optimization — lower priority

- [ ] Add a command-line scenario selector and configurable iteration counts.
- [ ] Add Java Flight Recorder profiles for contention demonstrations.
- [ ] Add stress suites using jcstress for memory-model-sensitive variants.
- [ ] Add property-based tests for conservation rules such as balances and produced/consumed counts.
- [ ] Record timing, queue depth, rejection, and fairness metrics without putting logging inside lock-heavy hot paths.
- [ ] Add pedagogical diagrams and expected thread-dump excerpts per problem.
- [ ] Review every condition wait for a predicate loop and every acquired permit/lock for structured release.
- [x] Enforce at least 80% line coverage while keeping deliberate deadlocks out of the test JVM.

## 4. Definition of done

A scenario is complete when it has a documented invariant, an interruptible API, a deterministic shutdown path, at least one progress test with a timeout, and no leaked non-daemon threads after the test. Prevention solutions must explain the Coffman condition they break. Demonstration-only unsafe code must be visibly named, isolated from the default runner, and never invoked by routine CI.

## 5. Out of scope

Web pages, REST CRUD endpoints, database schemas, migrations, message brokers, authentication, cloud infrastructure, and Docker are out of scope. Adding them would obscure the concurrency concepts and contradict the local Java 21 SE deployment requirement.
