# Production Readiness Audit

Audit date: 2026-08-18

## Result

| Area | Result | Evidence |
|---|---|---|
| Code quality | Pass | Java 21 compilation uses `-Xlint:all,-serial` with warnings treated as errors; expanded Checkstyle reports zero violations. |
| Correctness | Pass | 27 tests cover domain, error, concurrency, facade, CLI, and 10,000-client flows. |
| Coverage | Pass | JaCoCo measured 919 of 1,022 lines covered (89.9%); Maven enforces at least 80%. |
| Build | Pass | `mvn clean verify` creates the runnable JAR and enforces Java/Maven versions plus dependency convergence. |
| Startup | Pass | The packaged JAR completed a production-profile 100-client smoke run with exit code 0. |
| Dependencies | Pass | Runtime dependency tree contains no third-party artifacts; JUnit is test-scoped and current; verify fails on undeclared/unused dependency problems. |
| Secrets | Pass | Source/config scan found no credentials; `.env.example` contains non-secret simulator settings only. |
| CI | Pass | GitHub Actions verifies on Java 21, reviews pull-request dependency changes, and uploads reports/JAR artifacts. |
| Documentation | Pass | README, Java API reference, architecture, technical notes, environment template, and troubleshooting are synchronized. |

## Relevant fixes

| Risk | Production treatment |
|---|---|
| Half-observed portfolio settlement | All portfolio reads and compound writes share the transaction lock; a concurrent conservation test guards the invariant. |
| Failed portfolio computation during settlement | Buyer and seller results are both validated before either portfolio is committed. |
| Missing resting-order lifecycle audit | Matching returns immutable snapshots of every affected order so partial, filled, and self-trade cancellation states are audited. |
| Stale asynchronous price overwriting an explicit update | Per-symbol revisions and cancellation ensure explicit put/delete wins over older fetches. |
| Partial trade batch append | The complete batch is validated under an append lock before any trade is stored. |
| Configuration path traversal or resource exhaustion | Profile names are allow-listed by syntax; trader, timeout, parallelism, quantity, and money values have strict bounds. |
| Invalid identifiers/symbols/money | Central validation rejects control characters, malformed IDs, excessive precision/scale, invalid symbols, and invalid quantities. |
| Unbounded list results for consumers | Orders, portfolios, trades, and audit records expose immutable pagination with a maximum page size of 1,000. |
| Silent wrong-JDK builds | Maven Enforcer requires JDK 21.x and Maven 3.9+. |
| Caller errors mislabeled as provider errors | Explicit price updates return argument validation errors; only provider output is classified as an internal provider failure. |
| Locale-sensitive symbol conversion | Symbol normalization uses `Locale.ROOT`. |
| Ambiguous process failures | CLI returns documented exit codes and logs unexpected failures through `System.Logger`. |
| Dependency drift | Stable JUnit, Checkstyle, Clean, Enforcer, and Resources releases were applied; milestone and Maven 4 beta plugins were deliberately excluded. |

## Not applicable to this deployment

| Checklist item | Reason |
|---|---|
| SQL injection, indexes, migrations, N+1 queries | No SQL or external database exists; persistence is intentionally in-memory. |
| XSS, CSRF, HTTPS, response compression, HTTP status/health endpoint | No HTTP server or browser frontend exists. |
| Password hashing, authentication, authorization | No accounts or network trust boundary exist in the local simulator. |
| Docker and docker-compose | The declared deployment target is a local Java 21 JAR and the project explicitly requires no Docker. |
| Cloud deployment stages | CI produces a tested local JAR artifact; there is no authorized remote target. |

These items must be revisited if an HTTP, authentication, durable persistence, or distributed adapter is added.
