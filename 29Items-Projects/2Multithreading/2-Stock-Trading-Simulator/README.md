# Stock Trading Simulator

A production-polished local Java 21 simulator for high-concurrency trading workflows. It runs 10,000 virtual clients, matches limit orders by price/time priority, settles portfolios atomically, records immutable trades and audit events, fetches prices asynchronously, and calculates marked profit in parallel.

## Requirements

| Tool | Supported version |
|---|---|
| JDK | 21.x |
| Maven | 3.9+ |

The Maven Enforcer rules reject unsupported toolchains and dependency-version conflicts.

## Build, test, and run

~~~powershell
$env:JAVA_HOME = "C:\Programs\jdk-21.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
java -jar target\stock-trading-simulator-1.0.0-SNAPSHOT.jar
~~~

The Java 21 installation path is only an example; use the JDK 21 path on your machine. `mvn verify` runs Checkstyle, strict dependency analysis and convergence, 27 JUnit unit/integration/load tests, the 80% JaCoCo line-coverage gate, and JAR packaging. Reports are written to `target/surefire-reports` and `target/site/jacoco`.

A successful run prints accepted orders, trades, active orders, marked profit, elapsed time, and p95 order latency. The CLI exit codes are 0 for success, 2 for invalid configuration, 130 for interruption, and 1 for other failures.

## Application API

`TradingSimulator` is the typed Java SE application boundary.

| Area | Operations |
|---|---|
| Orders | `placeOrder`, `replaceOrder`, `getOrder`, paged/unpaged `listOrders`, `cancelOrder` |
| Portfolios | `createPortfolio`, `updatePortfolio`, `getPortfolio`, paged/unpaged `listPortfolios`, `deletePortfolio` |
| Prices | `getPrice`, `updatePrice`, `listPrices`, `deletePrice` |
| Trades | `getTrade`, paged/unpaged `listTrades` |
| Audit | Paged/unpaged `listAuditEvents` |
| Analytics | `calculateAggregateProfit` |

Trades and audit events are append-only system records, so arbitrary create/update/delete operations are intentionally unavailable. Paged list methods accept a zero-based offset and a limit from 1 through 1,000. See [Java API reference](docs/API.md).

## Business behavior

- Buy and sell books use price/time priority and support partial fills.
- The older resting order determines execution price.
- Self-trading is prevented.
- Buy cash and sell inventory are reserved before acceptance.
- Buyer/seller cash and shares settle atomically and remain conserved.
- Cancellation releases all unused reservation.
- Limit prices must be within the configured reference-price band.
- Async cache misses are coalesced per symbol and bounded by timeout.
- An explicit price update/delete invalidates and cancels an older in-flight fetch.

## Configuration

Profile files live in `config/`. `SIM_ENV` selects a profile, and environment variables override it. Java SE does not automatically load `.env`; [.env.example](.env.example) is a shell/IDE template.

| Variable | Allowed value |
|---|---|
| `SIM_ENV` | 1-32 letters, digits, underscores, or hyphens |
| `SIM_TRADER_COUNT` | 1-1,000,000 |
| `SIM_PRICE_TIMEOUT_MS` | 1-3,600,000 |
| `SIM_SIMULATION_TIMEOUT_MS` | 1-3,600,000 |
| `SIM_PROFIT_PARALLELISM` | 1-1,024 |
| `SIM_RANDOM_SEED` | Signed 64-bit integer |
| `SIM_INITIAL_CASH` | Non-negative decimal, up to 24 digits and 4 decimal places |
| `SIM_INITIAL_POSITION` | 1-1,000,000,000 |
| `SIM_MAX_PRICE_DEVIATION_PERCENT` | 0-100, up to 4 decimal places |

Example:

~~~powershell
$env:SIM_ENV = "dev"
$env:SIM_TRADER_COUNT = "10000"
$env:SIM_RANDOM_SEED = "42"
java -jar target\stock-trading-simulator-1.0.0-SNAPSHOT.jar
~~~

## Profiling

~~~powershell
.\scripts\profile.ps1 -TraderCount 10000 -Output target\stock-simulator.jfr
~~~

## Security and deployment scope

This is a local Java SE layered monolith with no HTTP listener, authentication store, external database, or secrets. Runtime has zero third-party dependencies. SQL injection, XSS, CSRF, HTTPS enforcement, response compression, health endpoints, migrations, and Docker are therefore not applicable. Strict domain/config validation and immutable snapshots protect the actual local boundary.

The CLI is the presentation layer, `TradingSimulator` is the application boundary, and concurrent in-memory repositories are the persistence adapters. See [Project plan](docs/PROJECT-PLAN.md), [Architecture](docs/ARCHITECTURE.md), [Technical notes](docs/TECH-NOTES.md), and [Production audit](docs/PRODUCTION-AUDIT.md).

## Troubleshooting

| Symptom | Resolution |
|---|---|
| Enforcer reports Java outside `[21,22)` | Point `JAVA_HOME` at JDK 21 and prepend `%JAVA_HOME%\bin` to `Path`; confirm with `mvn -version`. |
| Maven reports TLS/certificate download errors | Configure the corporate CA/proxy or an approved Maven mirror in `settings.xml`; do not disable TLS verification. Retry `mvn clean verify`. |
| Startup exits with code 2 | Check the selected profile and every environment variable against the range table above. Profile names cannot contain path separators. |
| Simulation times out | Increase `SIM_SIMULATION_TIMEOUT_MS` within the one-hour bound or reduce `SIM_TRADER_COUNT`; capture JFR before changing concurrency code. |
| Price lookup fails | Use a configured synthetic symbol (`AAPL`, `MSFT`, or `NVDA`) or inject a `PriceProvider` in an embedded composition. |
| Coverage or test report is missing | Run `mvn clean verify`, not only `mvn test`; verify-phase goals create the JaCoCo report and enforce coverage. |
