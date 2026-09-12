# Event Processing Pipeline

A three-stage concurrent pipeline in **Java 21**, with bounded queues between the
stages, a fixed thread pool of filter workers, parallel aggregation on a dedicated
`ForkJoinPool`, and a graceful shutdown built on the poison-pill pattern.

```
generate ──▶ [queue] ──▶ filter ──▶ [queue] ──▶ aggregate ──▶ report
 1 thread              N threads              ForkJoinPool
```

No Docker, no broker, no database. One jar and a JDK.

```bash
mvn package
java -jar target/event-processing-pipeline-1.0.0-SNAPSHOT.jar
```

---

## What it actually demonstrates

The point of the project is not "move events from A to B" — that is 40 lines. It is
the set of things that go wrong when you do:

| Problem | How this codebase answers it |
| --- | --- |
| A fast producer and a slow consumer | Bounded `ArrayBlockingQueue`. `put()` blocks, and the blocked time is **measured** (`blocked=…ms`) rather than hidden. |
| Telling N consumers to stop | One poison pill per consumer thread, count **derived** from `consumerThreads` so the two cannot drift. FIFO ordering is what makes it safe. |
| Consumers that are stuck in `take()` | `poll(timeout)` instead, so a cooperative stop flag is always observable within `pipeline.poll.timeout.millis`. |
| Aggregating in parallel without a lock | `AggregateResult.merge` is a commutative monoid, so fork/join is legal. This is why `sum` is stored and the mean is **derived** — a stored mean cannot be merged. |
| Ctrl+C mid-run | A shutdown hook that *waits* on a latch, drains both queues, prints a report, and exits **130**. |
| "Did we lose anything?" | End-of-run reconciliation: `produced == passed + rejected`. Printed as `reconciled=true\|false`, asserted in CI, and enforced again as a SQL `CHECK`. |

The concurrency invariants and the reasoning behind each one are in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Requirements

* **JDK 21+** — the code uses records, sealed interfaces and exhaustive pattern-matching
  `switch`. `maven.compiler.release=21` pins the language level.
* **Maven 3.9+**.

One test-scoped dependency (JUnit 5). **Zero runtime dependencies** — that is a design
constraint, not an accident (`docs/ARCHITECTURE.md` §2.6).

> On Windows, Maven frequently defaults to an older JDK. Set `JAVA_HOME` to a JDK 21
> install first; `scripts/run.ps1` does this for you and fails loudly if it cannot.

---

## Running it

### Wrapper scripts (recommended)

They check the JDK version first, rebuild only when the jar is stale, run from the
project root so `config/` and `output/` resolve, and pass the exit code through
unchanged.

```powershell
.\scripts\run.ps1                                     # dev profile, ~1 second
.\scripts\run.ps1 -Env prod -- --pipeline.event.count=5000000
.\scripts\run.ps1 -- --help
```

```bash
scripts/run.sh                                        # dev profile
scripts/run.sh --env prod
scripts/run.sh -- --pipeline.event.count=5000000
```

Everything after `--` goes to the application untouched, so the script's own `--env`
and the application's `--pipeline.env` can never be confused.

### The jar directly

```bash
java -jar target/event-processing-pipeline-1.0.0-SNAPSHOT.jar \
  --pipeline.event.count=1000000 \
  --pipeline.events.per.second=0 \
  --pipeline.consumer.threads=8 \
  --pipeline.queue.capacity=256 \
  --pipeline.dashboard.enabled=false
```

`--pipeline.events.per.second=0` means **unthrottled**. That is the interesting
setting: the producer then spends its life blocked in `put()`, which is exactly the
state a drain bug needs in order to show itself.

### Exit codes — the contract

| Code | Meaning |
| --- | --- |
| `0` | Completed and the counters reconcile. |
| `1` | The run failed, or events were lost. |
| `2` | Invalid configuration — **nothing was started**. |
| `130` | Interrupted (Ctrl+C) after a clean drain. |

`2` is separate from `1` deliberately: a scheduler may retry a flaky run, and it must
never retry a bad config. The two are indistinguishable if both exit `1`.

---

## Configuration

Five layers, lowest precedence first:

```
built-in defaults
  └─ classpath application.properties            (committed baseline)
      └─ config/application-<env>.properties      (profile)
          └─ PIPELINE_* environment variables     (deployment)
              └─ --key=value CLI flags            (always wins)
```

`java -jar … --help` prints all 24 keys, the precedence chain above, the rule that turns
a key into its environment variable (`pipeline.batch.size` → `PIPELINE_BATCH_SIZE`) and
the exit codes. **Unknown keys are rejected** with exit `2` rather than silently ignored —
a typo in `--pipeline.consumer.thread=8` is otherwise a run that quietly uses the
default.

### Profiles

| Profile | Purpose | Shape |
| --- | --- | --- |
| `dev` (default) | Finishes in about a second; deliberately tiny queues so backpressure is *visible*. | 20k events, capacity 16, dashboard on |
| `staging` | The soak. Unthrottled and oversubscribed, to make timing bugs reachable. | 300s, capacity 32, 8 threads, control plane on |
| `prod` | Unattended batch — "scheduled and unwatched", not a cluster. | 1M events, capacity 256, dashboard off |

`staging` enables the HTTP control plane, which **refuses to start** without
`PIPELINE_HTTP_TOKEN` (≥16 chars). That is intentional: an unauthenticated endpoint
that can retune a running pipeline is not a convenience.

### Secrets

Never in a file. `pipeline.http.token` and `pipeline.jdbc.password` are absent from
every committed `.properties` file and come from the environment only. See
[`.env.example`](.env.example) — it documents the variables and is not itself read by
anything.

---

## Observability

**Console dashboard** (on by default in `dev`) — a fixed-width, in-place refresh of
queue depths, throughput and blocked time. It writes to stdout; the log writes to
stderr, so `> report.txt` captures one without the other.

**Optional HTTP control plane** — `--pipeline.http.enabled=true`, bound to loopback:

```bash
export PIPELINE_HTTP_TOKEN=$(openssl rand -hex 24)

curl -H "X-Pipeline-Token: $PIPELINE_HTTP_TOKEN" localhost:8080/health
curl -H "X-Pipeline-Token: $PIPELINE_HTTP_TOKEN" localhost:8080/metrics
curl -H "X-Pipeline-Token: $PIPELINE_HTTP_TOKEN" localhost:8080/aggregates

# Retune the filter without restarting
curl -X POST -H "X-Pipeline-Token: $PIPELINE_HTTP_TOKEN" \
     -d '{"threshold": 75.0}' localhost:8080/threshold
```

`jdk.httpserver` from the JDK, no framework. Token comparison uses
`MessageDigest.isEqual`, which does not return early on the first mismatching byte.

**Logs** — `java.util.logging`, to stderr, through a custom `PipelineLogFormatter`.
Every line carries the thread name (`pipeline-filter-2`), because the first question
about any log line in a pipeline is which stage emitted it. The JDK's
`SimpleFormatter` cannot do this: a `LogRecord` carries a thread *id*, not a name.

---

## Troubleshooting

Every message below is the literal text the code prints, so it can be grepped for.
Configuration problems go to **stderr** and exit `2`; run failures print a report to
**stdout** and exit `1`. That split is the first diagnostic: if you got a report at all,
the pipeline started.

### Build

| Symptom | Cause | Fix |
| --- | --- | --- |
| `invalid target release: 21`, or `class file has wrong version` | Maven is running on an older JDK. It picks up `JAVA_HOME`, not whatever `java -version` on your `PATH` reports — on Windows these are routinely different. | `java -version` *and* `mvn -v` must both say 21+. Set `JAVA_HOME` to a JDK 21 install (here: `C:\Programs\jdk-21.0.2`), or use `scripts/run.ps1`, which checks and fails loudly. |
| `Rule violated for bundle …: lines covered ratio is 0.8x, but expected minimum is 0.85` | The `jacoco:check` coverage gate. Not a flake — new code arrived without tests. | Read `target/site/jacoco/index.html`, which shows the uncovered lines. Lower the threshold only with a reason you would defend in review. |
| `You have N Checkstyle violations` during `validate` | Style gate, and it includes test sources. | `mvn checkstyle:check` prints file and line. The most common one is an import left behind after deleting a class's last usage. |

### Startup — exit `2`, nothing was started

| Message | What happened |
| --- | --- |
| `configuration error: pipeline.consumer.thread is not a known setting; run with --help for the list` | A typo in a key. Unknown keys are **rejected**, not ignored: silently falling back to the default turns a mis-set flag into a run whose numbers mean something other than you think. |
| `pipeline.event.count either pipeline.event.count or pipeline.duration.seconds must be non-zero, otherwise the producer never stops` | Both are `0`. `0` means "unbounded" for each, so together they describe a run with no termination condition. |
| `pipeline.http.token must be at least 16 characters when pipeline.http.enabled=true` | The control plane can retune a running pipeline, so it refuses to start unauthenticated. Usually hit by selecting the `staging` profile, which enables it. Set `PIPELINE_HTTP_TOKEN` (`openssl rand -hex 24`). |
| `pipeline.jdbc.url is set, but JDBC persistence is not implemented … leave it blank to use the in-memory repository` | The JDBC repository is a documented stub. This is checked **before any thread starts** on purpose: the earlier behaviour was a run that did all its work, wrote the CSV, and only then failed on `save` — reporting FAILED for a run that had in fact succeeded. |
| `could not bind control plane to …:8080` (the address is appended, so it reads `localhost/127.0.0.1:8080`) | Port already in use. The socket is bound in the constructor on purpose, so a clash fails at startup instead of leaving the operator without a control plane they think they have. Change `--pipeline.http.port`, or find the holder: `ss -lptn 'sport = :8080'` / `netstat -ano \| findstr :8080`. Note the `staging` profile uses **8081**, not the default 8080. |

### A run that finished badly — exit `1`

Look at the report's last two lines before anything else:

```
produced=200000 passed=99987 rejected=100000 aggregated=99987 errors=0 blocked=412ms reconciled=false
result : FAILED
```

* **`reconciled=false`** — `produced ≠ passed + rejected`. Events were **lost**, which is a
  correctness bug in the shutdown path, not a tuning problem. Keep the log and the
  configuration: it is reproducible from `--pipeline.random.seed`, and `scripts/soak.sh`
  exists to reproduce exactly this.
* **`errors=N`** with `reconciled=true` — the data path was fine and something on the
  reporting edge failed, almost always the CSV sink. Check that `--pipeline.output.dir`
  is writable and is not nested under a regular file. A sink failure is deliberately
  *counted* rather than thrown, so you get a report instead of a stack trace.
* **`pipeline stalled: no progress for 30000 ms`** — no counter moved for the whole
  shutdown budget, so the orchestrator gave up and stopped the stages. Either a genuine
  deadlock (keep a `jcmd <pid> Thread.print`), or a budget shorter than the slowest
  legitimate step; `--pipeline.shutdown.timeout.seconds` bounds the *drain*, so raising
  it is only correct if the pipeline was in fact still moving.

### A run that looks stuck

With `--pipeline.events.per.second=0` the producer spends most of its life blocked in
`put()` on a full queue. **That is the intended state**, not a hang — it is what makes a
drain bug reachable. Turn the dashboard on (`--pipeline.dashboard.enabled=true`) and
watch the queue depths: both pinned at capacity with `aggregated` climbing is a healthy
saturated pipeline. Nothing moving anywhere for 30 s produces the stall message above.

### Configuration that seems to be ignored

* **`.env` is not read by the application.** `.env.example` is documentation; nothing
  loads it. Export it yourself — `set -a && . ./.env && set +a` — or pass `--key=value`
  flags, which win over everything.
* **A profile file can be silently absent.** `--pipeline.env=stagin` loads no overrides
  and is not an error, because a missing profile file is a legitimate deployment. If
  settings you expected did not apply, the startup log line
  `starting with PipelineConfig[env=…` shows every value actually in force — with the
  token and password reduced to `(set)`/`(unset)`, never their content or even their
  length, so the line is safe to paste into a bug report.

### Control plane returns nothing useful

| Response | Cause |
| --- | --- |
| `{"error":"unauthorised"}`, `401` | Missing or wrong header. It is `X-Pipeline-Token`, and comparison is constant-time, so a wrong token is indistinguishable from a wrong length — the response tells you nothing on purpose. The log says `rejected unauthenticated request to /metrics`. |
| Connection refused from another host | It binds `127.0.0.1` only, and that is not configurable. Tunnel over SSH if you need it remotely. |
| `{"error":"method not allowed"}`, `405` | `/threshold` is `POST`; `/health`, `/metrics` and `/aggregates` are `GET`. A route that exists but does not answer your method says `405`, in both directions — never `404`, which would send you hunting for a typo in a path you spelled correctly. |
| `{"error":"not found"}`, `404` | Those four paths are the whole surface. You only ever see this *after* authenticating: an unknown path without a token is `401`, because a `404`/`401` split enumerates the routes that exist. |
| `413 request body too large`, or `400 body must be {"threshold": <number>}` | The body is size-capped before being read, and must be exactly that object. A finite value with magnitude ≤ 10⁶ is also enforced — `NaN`, `Infinity` and absurd magnitudes are rejected rather than quietly making the filter pass or reject everything. |

### Output

A **header-only `aggregates.csv` is a successful run** — every reading was below
`--pipeline.filter.threshold` (default `50.0`, against sensor ranges that straddle it).
Writing no file at all would be indistinguishable from a crash, which is why the header
is always written. If the file is missing entirely, the run exited `2` before the sink
was wired, or `--pipeline.output.dir` could not be created.

---

## Tests

```bash
mvn verify          # tests + JaCoCo report at target/site/jacoco/index.html
mvn checkstyle:check
```

**597 tests in 32 classes; 92.5 % line and 90.6 % branch coverage.** `mvn verify` fails
below **85 % line / 80 % branch** (`jacoco:check`), so that number is a gate rather than
a fact to admire. Expect `verify` to take about a minute — roughly 47 seconds of it is
the two control-plane test classes, each of which starts and stops a real HTTP server
per test. That is slow on purpose: an authenticating endpoint tested through a mock
handler proves nothing about the socket.

Concurrency tests are structured to be *decidable* rather than fast: `@Timeout` on
every test that could hang, `@RepeatedTest` where a single pass proves nothing, and no
assertion of the form "this finished within N milliseconds" — a shared CI runner makes
that a guaranteed future flake. Where pacing genuinely had to be verified
(`TokenBucketRateLimiter`), the clock is injected and the fake *advances* it by the
requested sleep rather than spending it. Coverage targets and the reasoning, including
what is still deliberately untested and why, are in
[`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) §3.2.

For the bugs that only appear once in fifty runs:

```bash
scripts/soak.sh --iterations 200 --duration 30 --interrupt-every 5
```

Hostile settings on purpose — unthrottled producer, more threads than cores, shallow
queues, a **different random seed each iteration**, and `SIGINT` injected into every
fifth run. It fails on the first bad iteration and keeps that iteration's log.

---

## Layout

```
├── config/                  Profile overrides (dev, staging, prod)
├── docs/                    PROJECT-PLAN, ARCHITECTURE, TECH-NOTES
├── migrations/              PostgreSQL DDL for optional persistence (see note below)
├── scripts/                 run.ps1, run.sh, soak.sh
├── src/main/java/com/example/pipeline/
│   ├── domain/              Records and sealed types. Imports the JDK and nothing else.
│   ├── application/         Orchestration, stages, ports. Never imports infrastructure.
│   ├── infrastructure/      Queues, executors, config, generator, http, sinks, metrics
│   └── presentation/cli/    main, argument parsing, dashboard, log formatter
└── src/main/resources/      application.properties, logging.properties
```

The dependency rule is one-directional: `presentation → application → domain`, with
`infrastructure` implementing `application.port`. It is what lets the whole pipeline be
tested without touching a queue implementation, and it is enforced by review rather
than by a module system — see `docs/ARCHITECTURE.md` §2.1.

### About `/migrations`

Reviewable schema for a feature that is **designed but not implemented**. The default
repository is in-memory; `JdbcAggregateRepository` is a documented stub that throws on
`save`. Persisting a run's aggregates is a Phase 3 item, and the schema is committed
first because the shape of the data is the part worth arguing about before any code
exists. [`migrations/README.md`](migrations/README.md) is explicit about this.

---

## Documentation

| Document | Contents |
| --- | --- |
| [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) | File structure and the prioritised TODO list by phase |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Pattern choice, component interactions, data-flow diagrams, scalability, security, error handling |
| [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) | CI/CD, testing strategy, deployment, environments, git workflow, **common pitfalls** |
| [`migrations/README.md`](migrations/README.md) | The data model and what finishing persistence requires |

If you read one section, read **§3.6 Common pitfalls** in `TECH-NOTES.md`. Every entry
in it is a mistake that was actually made or narrowly avoided while writing this code.
