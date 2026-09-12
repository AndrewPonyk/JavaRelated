# Production Readiness Audit

Audit date: 2026-08-17

This project is a local Java 21 concurrency library and executable demonstration suite.
It has no web tier, API, database, authentication, persistent data, secrets, containers,
or remote deployment. Checks for SQL injection, XSS/CSRF, HTTP status codes, pagination,
compression, database indexes/migrations, HTTPS, password hashing, health endpoints, and
Docker are therefore not applicable.

| Area | Status | Evidence |
|---|---|---|
| Code quality | Pass | Checkstyle includes unused/redundant imports; `javac -Xlint:all -Werror`; no debug or commented-out source blocks. |
| Input and boundary validation | Pass | Public capacities, IDs, amounts, callbacks, contents, and timeouts are validated; timeout overflow saturates safely. |
| Concurrency lifecycle | Pass | Admission/close races are serialized; shutdown is idempotent; interrupts are propagated/restored; partial acquisitions are rolled back. |
| Performance relevance | Pass | Bounded queues/permits, predicate waits, small critical sections, and no application busy-waiting or lock-held I/O. |
| Secrets and remote attack surface | Pass | No secrets or environment variables are consumed; no runtime dependencies or network listeners. |
| Dependencies | Pass | Current pinned JUnit/build plugins, Maven dependency convergence enforcement, and weekly Dependabot updates. |
| Tests | Pass | 35 JUnit tests cover safe end-to-end flows, concurrency invariants, invalid inputs, callback failures, overflow, lifecycle errors, and timeouts. |
| Coverage | Pass | JaCoCo gate requires at least 80% line coverage; audited result is over 83% line coverage. |
| Documentation | Pass | README includes Java/Maven setup, all commands, outputs, CI behavior, and troubleshooting; `.env.example` records that no variables are used. |
| Build and startup | Pass | Java 21/Maven 3.9 enforced; clean verification, executable JAR startup, all safe demos, and both bounded deadlock-detection demos verified. |
| CI/CD | Pass | Read-only workflow permissions, non-persistent checkout credentials, Java 21 verification, reports, JAR, and SHA-256 checksum artifacts. |

## Verification commands

```shell
mvn --batch-mode --no-transfer-progress clean verify
java -jar target/classical-concurrency-problems-1.0.0-SNAPSHOT.jar all-safe
java -jar target/classical-concurrency-problems-1.0.0-SNAPSHOT.jar dining-deadlock
java -jar target/classical-concurrency-problems-1.0.0-SNAPSHOT.jar bank-deadlock
```

The two deliberate deadlock commands run deadlocked work on daemon threads, confirm the
cycle through `ThreadMXBean`, report the result, and exit successfully. They are not run
inside the routine JUnit process.
