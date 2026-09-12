# Stock Trading Simulator - Technical Notes

## 1. CI/CD

GitHub Actions uses Temurin 21 and runs mvn verify. Pull requests also run dependency review. The pipeline performs toolchain enforcement, dependency convergence and bytecode usage analysis, style validation, warning-free compilation, all tests including the 10,000-client load flow, JaCoCo reporting/checking, JAR packaging, and artifact/report upload. The uploaded JAR is the local deployment artifact; there is no remote environment to deploy.

~~~mermaid
flowchart LR
    A[Checkout] --> B[Temurin 21 and Maven cache]
    B --> C[Dependency and style checks]
    C --> D[Unit and integration tests]
    D --> E[10K load flow]
    E --> F[JaCoCo 80 percent gate]
    F --> G[Runnable JAR]
    G --> H[Upload reports and artifact]
~~~

## 2. Testing strategy and implemented coverage

| Layer | Implemented tests |
|---|---|
| Domain | Validation, normalization, defensive copies, partial/complete fills, cash/share operations, trade notional. |
| Concurrency | 10,000 atomic IDs, concurrent order submission, optimistic cache reads/writes, 1,000 concurrent portfolios. |
| Business | Price/time matching, partial fills, self-trade prevention, reservations, insufficient resources, cancellation, price bands. |
| Integration | Full facade workflow, cash/share conservation, immutable trade/audit ledgers, configuration precedence. |
| End-to-end | CLI output/metrics and a bounded 10,000-client virtual-thread run. |

JaCoCo measured 919 of 1,022 lines (89.9%) during the production audit. pom.xml enforces a minimum bundle line ratio of 0.80. The 27 tests use latches/futures and bounded waits instead of timing-based sleeps.

## 3. Local deployment and profiling

Requirements are JDK 21 and Maven 3.9 or newer:

~~~powershell
$env:JAVA_HOME = "C:\Programs\jdk-21.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
java -jar target\stock-trading-simulator-1.0.0-SNAPSHOT.jar
~~~

Capture a Java Flight Recording after building:

~~~powershell
.\scripts\profile.ps1 -TraderCount 10000 -Output target\stock-simulator.jfr
~~~

Containerization, external deployment, and migrations are not applicable to this Java SE local executable.

## 4. Configuration

SIM_ENV selects config/application-environment.properties. Environment variables override profile properties; code defaults apply when a key is absent.

| Environment variable | Meaning |
|---|---|
| SIM_ENV | Profile name: dev, staging, or prod. |
| SIM_TRADER_COUNT | Number of virtual client tasks. |
| SIM_PRICE_TIMEOUT_MS | Deadline for a price-provider miss. |
| SIM_SIMULATION_TIMEOUT_MS | Global bounded wait for all clients. |
| SIM_PROFIT_PARALLELISM | Dedicated fork/join parallelism. |
| SIM_RANDOM_SEED | Deterministic workload seed. |
| SIM_INITIAL_CASH | Starting cash for every trader. |
| SIM_INITIAL_POSITION | Starting shares per configured symbol. |
| SIM_MAX_PRICE_DEVIATION_PERCENT | Accepted distance from reference price. |

Java SE does not automatically load .env; .env.example is a shell/IDE template. Malformed, non-positive, or out-of-range values fail at startup.

## 5. Version control

Use trunk-based GitHub Flow: short-lived branches, required green CI/review, squash merge, protected main, and semantic release tags. Keep formatting-only changes separate from lock or settlement changes so reviewers can inspect critical sections clearly.

## 6. Operational pitfalls

| Pitfall | Implemented mitigation |
|---|---|
| Virtual threads used for CPU work | Profit reduction uses a dedicated bounded ForkJoinPool. |
| Blocking while holding the book lock | The book returns immutable trades; settlement and audit happen after unlock. |
| Non-atomic concurrent-map composition | Reservations and two-account settlement use one transaction lock. |
| Invalid optimistic reads | Every optimistic stamp is validated and fallback reads are counted. |
| Duplicate price fetches | The in-flight future is published per symbol and removed before completion is exposed. |
| Explicit price racing with an older fetch | A per-symbol revision prevents stale provider data from replacing the explicit value. |
| Unbounded async waits | Price requests and the global simulation both have deadlines. |
| Floating-point money | All money uses BigDecimal. |
| Overselling/overspending | Cash and position reservations precede order-book acceptance. |
| Partial state on cancellation | The book changes status under lock, then the reservation is released. |
| Resource leaks | CLI and tests own/close executors and fork/join services. |

## 7. Commands

| Command | Purpose |
|---|---|
| mvn test | Run all 27 tests, including integration and load flows. |
| mvn verify | Run style, tests, coverage gate, and package the runnable JAR. |
| java -jar target/stock-trading-simulator-1.0.0-SNAPSHOT.jar | Execute the configured simulation. |
| .\scripts\profile.ps1 | Execute with Java Flight Recorder enabled. |
