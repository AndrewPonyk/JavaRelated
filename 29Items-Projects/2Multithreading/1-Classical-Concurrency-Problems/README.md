# Classical Concurrency Problems

A Java 21 SE learning project containing isolated, testable implementations of classical thread-coordination problems. The examples favor explicit invariants, interruption support, bounded waits, and safe cleanup.

## Problem catalog

| Package | Strategies / primitives |
|---|---|
| `common` | Immutable results, interruption boundary, reusable `Phaser` gate |
| `dining` | Deliberate deadlock, resource hierarchy, fair semaphore arbitrator |
| `barber` | Multiple barber workers, bounded waiting room, semaphores |
| `readerswriters` | Fair/non-fair `ReadWriteLock`, explicit writer priority with Conditions |
| `producerconsumer` | Generic bounded FIFO with Conditions, two-party `Exchanger` |
| `smokers` | Agent/smoker rendezvous with Semaphores |
| `h2o` | 2H + 1O admission with Semaphores and `CyclicBarrier` |
| `santa` | Reindeer latch, elf grouping, Santa notification semaphore |
| `bathroom` | Capacity, category exclusivity, fair lock and alternating turn |
| `deadlock` | Unsafe transfer, ordered prevention, timed `tryLock`, detection |

### Problems versus runnable demos

The project contains 9 primary concurrency problems. Some problems have multiple
algorithmic variants, and the tech stack adds two supplemental primitive demos.

| Problem | Executable variants |
|---|---:|
| Dining Philosophers | 3: resource hierarchy, arbitrator, deliberate deadlock |
| Sleeping Barber | 1 |
| Readers-Writers | 4: reader priority, writer priority, fair lock, unfair lock |
| Producer-Consumer | 1 |
| Cigarette Smokers | 1 |
| H2O Building | 1 |
| Santa Claus | 1 |
| Unisex Bathroom | 1 |
| Bank Transfer Deadlock | 3: unsafe, ordered, timed `tryLock` |
| Supplemental primitive demos | 2: `Exchanger`, `Phaser` |
| **Total runnable demos** | **18** |

| Count category | Total | What it represents |
|---|---:|---|
| Classical problem families | **9** | The nine named concurrency problems above |
| Core algorithms / strategies | **16** | All implementations belonging to those nine problems |
| Supplemental examples | **2** | Standalone `Exchanger` and `Phaser` demonstrations |
| Runnable implementations | **18** | Core strategies plus supplemental examples |
| Standalone demo `main()` methods | **18** | One independently executable class per implementation |
| Total `main()` entry points | **19** | The 18 demos plus the central JAR dispatcher |

## Build and run

Requirements: JDK 21 and Maven 3.9 or newer.

Verify that Maven is using Java 21 before building:

```shell
java -version
mvn -version
```

On PowerShell, select the installed JDK explicitly when another Java version is the
machine default:

```powershell
$env:JAVA_HOME = "C:\Programs\jdk-21.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
```

The project has no runtime dependencies, external services, secrets, database, or
required environment variables. Maven downloads JUnit and build plugins for tests and
packaging.

```shell
mvn clean verify
java -jar target/classical-concurrency-problems-1.0.0-SNAPSHOT.jar list
java -jar target/classical-concurrency-problems-1.0.0-SNAPSHOT.jar all-safe
```

`mvn clean verify` compiles with all Java warnings treated as errors, runs Checkstyle
and all JUnit tests, enforces at least 80% line coverage, and creates:

- `target/classical-concurrency-problems-1.0.0-SNAPSHOT.jar`
- `target/site/jacoco/index.html`
- `target/surefire-reports/`

Every algorithm has a dedicated `*Demo.java` containing its own `main()` and is also
available through the packaged JAR:

| Command | Algorithm |
|---|---|
| `dining-hierarchy` | Dining Philosophers resource hierarchy |
| `dining-arbitrator` | Dining Philosophers N-1 semaphore arbitrator |
| `dining-deadlock` | Deliberate Dining Philosophers deadlock and detection |
| `sleeping-barber` | Multiple Sleeping Barbers and bounded waiting room |
| `readers-reader-priority` | Reader priority with `synchronized`/`wait`/`notify` |
| `readers-writer-priority` | Writer priority with Conditions |
| `readers-fair-lock` | Fair `ReadWriteLock` |
| `readers-unfair-lock` | Non-fair `ReadWriteLock` |
| `producer-consumer` | Condition-based bounded buffer |
| `exchanger` | Two-party batch exchange |
| `cigarette-smokers` | Semaphore agent/smokers coordination |
| `h2o` | CyclicBarrier molecule formation |
| `santa` | Reindeer latch and elf semaphore groups |
| `unisex-bathroom` | Fair, exclusive-category admission |
| `phaser` | Dynamic phase coordination |
| `bank-deadlock` | Deliberate opposite-transfer deadlock and detection |
| `bank-ordered` | Account-ID lock ordering |
| `bank-try-lock` | Timed lock acquisition, rollback, and retry |

For example:

```shell
java -jar target/classical-concurrency-problems-1.0.0-SNAPSHOT.jar h2o
java -jar target/classical-concurrency-problems-1.0.0-SNAPSHOT.jar bank-ordered
```

The deliberate-deadlock demos use daemon workers, detect their lock cycles, print one
summary line, and exit normally. For manual inspection of a separately modified or
long-running deadlock demonstration, use:

```shell
jcmd -l
jcmd <pid> Thread.print -l
```

## Design conventions

- Each scenario owns its locks, permits, conditions, and mutable state.
- Blocking APIs propagate `InterruptedException`.
- Condition predicates are re-checked in loops.
- Releases happen in `finally` after successful acquisition.
- Tests coordinate starts and enforce time bounds rather than relying on long sleeps.
- No frontend, HTTP API, database, broker, cloud service, or Docker runtime is required.

## Troubleshooting

| Symptom | Cause and resolution |
|---|---|
| `release version 21 not supported` | Maven is using an older JDK. Check `mvn -version`, set `JAVA_HOME` to JDK 21, and retry. |
| Maven cannot download plugins or dependencies | Confirm network/proxy access to Maven Central and that the user Maven cache (`~/.m2`) is writable. |
| A concurrency test times out | Inspect `target/surefire-reports`, then capture `jcmd <pid> Thread.print -l` if the process is still running. Do not hide the failure with retries. |
| `Unknown demo` | Run the JAR with `list` and use one of the documented command names. |
| Deliberate deadlock appears to hang after modification | The shipped demos use daemon workers and exit. A modified non-daemon version must be terminated after collecting a thread dump. |
| `.env` changes have no effect | No environment variables are consumed. Java SE does not load `.env` files automatically; use the documented CLI commands. |

## CI and dependency maintenance

GitHub Actions runs the same `mvn clean verify` command on Java 21, uploads test and
coverage reports, and publishes the JAR with a SHA-256 checksum. Dependabot checks Maven
and GitHub Actions dependencies weekly. Workflow permissions are read-only and checkout
does not persist credentials.

See [the project plan](docs/PROJECT-PLAN.md), [architecture](docs/ARCHITECTURE.md),
[technical notes](docs/TECH-NOTES.md), and
[production-readiness audit](docs/PRODUCTION-READINESS.md) for rationale, verification,
and extension guidance.
