# ETL Pipeline Builder — Project Plan

| | |
|---|---|
| **Project** | ETL Pipeline Builder |
| **Tech stack** | Python, Apache Airflow, dbt, Snowflake, AWS Glue, Angular |
| **Streaming / infra** | AWS MSK (Kafka), ElastiCache Redis, ECS Fargate, Terraform, GitHub Actions |
| **Data quality / lineage** | Great Expectations checkpoints + dbt docs/manifest lineage |
| **Business goals** | Real-time business metrics with **sub-second latency**; **ML anomaly detection** on streaming data with alerting |
| **Status** | **Implemented & verified locally** — code-complete for Phases 1–2; remaining unchecked items are AWS/Snowflake account provisioning ops |

Related documents: [ARCHITECTURE.md](./ARCHITECTURE.md) · [TECH-NOTES.md](./TECH-NOTES.md)

---

## 1.1 Project File Structure

The repository is a **monorepo** containing every deployable unit of the platform. Each top-level
directory is independently testable and deployable; CI uses path filters to build only what changed.

```text
3-ETL-Pipeline-Builder/
├── docs/                              # Architecture & planning documentation
│   ├── PROJECT-PLAN.md                # ← this file
│   ├── ARCHITECTURE.md                # Patterns, diagrams, data flow, security
│   └── TECH-NOTES.md                  # CI/CD, testing, deployment, env management
│
├── airflow/                           # ── ORCHESTRATION (Airflow / MWAA) ──
│   ├── Dockerfile                     # Local/custom Airflow image (MWAA uses S3-synced dags/)
│   ├── requirements.txt               # Providers installed into MWAA / local image
│   ├── dags/
│   │   ├── etl_daily_batch_dag.py     # Glue → COPY INTO Snowflake → dbt → GE gates → lineage
│   │   ├── anomaly_model_retrain_dag.py  # Weekly retrain of the streaming anomaly model
│   │   ├── streaming_health_dag.py    # 5-min watchdog: consumer lag + hot-store freshness
│   │   └── common/                    # Shared DAG utilities (importable inside dags/)
│   │       ├── __init__.py
│   │       ├── config.py              # Env-driven pipeline configuration (12-factor)
│   │       ├── callbacks.py           # on_failure / SLA-miss → SNS/Slack
│   │       └── validation.py          # Great Expectations checkpoint runner (hard gate)
│   └── tests/
│       └── test_dag_integrity.py      # DagBag import test + hygiene rules (catchup, retries, tags)
│
├── dbt/                               # ── TRANSFORMATIONS (dbt on Snowflake) ──
│   ├── dbt_project.yml
│   ├── packages.yml                   # dbt_utils (+ dbt_expectations)
│   ├── profiles/profiles.yml          # env_var()-driven profiles: dev / ci / prod
│   ├── models/
│   │   ├── staging/                   # 1:1 with raw sources; rename/cast/dedupe only
│   │   │   ├── _stg_sources.yml       # Source declarations + freshness SLAs
│   │   │   ├── _stg_models.yml        # Tests & docs for staging models
│   │   │   ├── stg_orders.sql
│   │   │   └── stg_stream_events.sql
│   │   ├── intermediate/
│   │   │   └── int_orders_enriched.sql
│   │   └── marts/                     # Business-facing, tested, incremental
│   │       ├── _marts_models.yml
│   │       ├── fct_business_metrics_daily.sql
│   │       └── dim_customers.sql
│   ├── macros/generate_schema_name.sql
│   ├── seeds/metric_definitions.csv   # Metric catalog (thresholds, units, owners)
│   └── tests/assert_metrics_non_negative.sql
│
├── great_expectations/                # ── DATA QUALITY GATES ──
│   ├── great_expectations.yml         # GX 0.18 project config (stores, datasources)
│   ├── expectations/raw_orders_suite.json
│   └── checkpoints/raw_orders_checkpoint.yml
│
├── glue/                              # ── LAKE-SIDE HEAVY LIFTING (AWS Glue 4.0 / PySpark) ──
│   ├── jobs/raw_events_to_parquet.py  # raw JSON → deduped, partitioned Parquet
│   └── tests/test_raw_events_to_parquet.py
│
├── streaming/                         # ── SPEED LAYER (MSK consumer, sub-second metrics) ──
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── processor/
│   │   ├── main.py                    # Wiring: consume → window → detect → sink
│   │   ├── config.py                  # pydantic-settings
│   │   ├── consumer.py                # aiokafka, at-least-once, DLQ hook
│   │   ├── metrics_aggregator.py      # 500 ms tumbling windows + watermarks
│   │   ├── anomaly/detector.py        # EWMA z-score online detector (TODO: river HST)
│   │   └── sinks/
│   │       ├── redis_sink.py          # Hot store: HSET + pub/sub fan-out to API
│   │       ├── snowflake_sink.py      # Dev stub (prod = MSK Connect → Snowpipe Streaming)
│   │       └── alert_publisher.py     # alerts.anomaly.v1 topic + SNS
│   └── tests/
│       ├── test_metrics_aggregator.py
│       └── test_anomaly_detector.py
│
├── api/                               # ── SERVING API (FastAPI: REST + WebSocket) ──
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── app/
│   │   ├── main.py                    # App factory, CORS, health probes
│   │   ├── config.py
│   │   ├── routers/                   # metrics (REST+WS), pipelines (CRUD), alerts
│   │   ├── schemas/                   # Pydantic v2 models (validation layer)
│   │   ├── services/                  # Business logic (service layer)
│   │   └── db/snowflake_client.py     # Warehouse access for history queries
│   └── tests/                         # TestClient tests with DI overrides
│
├── frontend/                          # ── DASHBOARD (Angular 18, standalone components) ──
│   ├── package.json / angular.json / tsconfig*.json / eslint.config.js
│   ├── proxy.conf.json                # Dev proxy → FastAPI (REST + WS)
│   ├── Dockerfile / nginx.conf        # Prod: static + reverse proxy /api
│   └── src/
│       ├── main.ts / index.html / styles.scss
│       ├── environments/              # environment.ts (+ .development.ts)
│       └── app/
│           ├── app.component.ts / app.config.ts / app.routes.ts
│           ├── core/
│           │   ├── models/            # metric / pipeline / alert interfaces
│           │   └── services/          # HttpClient + rxjs webSocket services
│           └── features/
│               ├── dashboard/         # Live tiles (WS push, <1 s end-to-end)
│               ├── pipelines/         # Pipeline CRUD list
│               └── alerts/            # Anomaly alert feed
│
├── migrations/                        # ── SNOWFLAKE DDL (schemachange, versioned) ──
│   ├── README.md
│   ├── V1.0.0__initial_warehouse_setup.sql   # DBs, warehouses, roles, grants
│   ├── V1.1.0__raw_landing_tables.sql        # RAW tables, stages, file formats
│   └── V1.2.0__pipeline_metadata.sql         # Runs, DQ results, alerts, pipeline registry
│
├── infrastructure/                    # ── IaC (Terraform) ──
│   ├── environments/{dev,staging,prod}/      # Per-env root modules + tfvars (isolated state)
│   └── modules/
│       ├── networking/                # VPC, private subnets, endpoints
│       ├── msk/                       # MSK cluster (IAM auth, TLS)
│       ├── data_lake/                 # S3 lake + artifacts, Glue catalog & jobs
│       ├── orchestration/             # MWAA environment + DAGs bucket
│       └── realtime/                  # ECS cluster, ECR, ElastiCache Redis
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                     # lint → unit tests → dbt parse → fe build → tf validate
│   │   ├── cd.yml                     # images → ECR; DAGs → S3; schemachange; ECS deploy
│   │   └── infra.yml                  # terraform plan (PR) / apply (main, gated)
│   ├── dependabot.yml
│   ├── CODEOWNERS
│   └── PULL_REQUEST_TEMPLATE.md
│
├── scripts/
│   ├── bootstrap_local.sh             # compose up kafka/redis + create topics
│   ├── seed_kafka_events.py           # Synthetic order events (with --burst anomaly mode)
│   ├── run_ge_checkpoint.py           # CLI wrapper for GE checkpoints (CI/Airflow)
│   └── export_dbt_lineage.py          # dbt manifest → lineage graph JSON
│
├── docker-compose.yml                 # Local stack: kafka, redis, airflow, api, processor, frontend
├── Makefile                           # One-liners for every dev task
├── pyproject.toml                     # ruff / mypy / pytest shared config
├── requirements-dev.txt
├── .env.example                       # Canonical env contract (see TECH-NOTES §3.4)
├── .pre-commit-config.yaml / .editorconfig / .sqlfluff / .gitignore
├── README.md
└── Claude-Fable-5.txt                 # Model marker file
```

**Conventions**

- Python packages have unique import roots (`app`, `processor`, `jobs`, `dags/common`) so one
  `pytest`/`mypy` invocation at the repo root covers everything without name collisions.
- Every environment-specific value flows through environment variables declared in `.env.example`.
- Snowflake **objects** are owned by `migrations/` (schemachange); Snowflake **models** are owned
  by dbt. Terraform owns AWS only — one owner per resource type, no drift wars.

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (P0, weeks 1–3)

> Goal: infrastructure exists, CI is green, one event can travel end-to-end in dev.
> Unchecked items require an AWS/Snowflake account and are operational, not code.

- [x] **Repo & tooling**
  - [x] Scaffold adopted; pre-commit config in place (`pre-commit install` per clone)
  - [ ] Enable branch protection + required CI checks on `master` *(repo-settings op)*
- [ ] **AWS bootstrap (Terraform)** *(account-level ops; all HCL is written)*
  - [ ] Create tfstate S3 bucket + DynamoDB lock table (one-time manual bootstrap)
  - [ ] Configure GitHub OIDC provider + `deploy` IAM roles per environment (no static keys)
  - [ ] Apply `dev`: networking → MSK (2 brokers, IAM auth) → data lake → realtime (ECS/ECR/Redis)
  - [ ] Fill IAM policy TODOs in `data_lake` (Glue role) and `orchestration` (MWAA execution role)
- [ ] **Snowflake bootstrap** *(account-level ops; all SQL is written)*
  - [ ] Create service users (key-pair auth) for LOADER / TRANSFORMER / REPORTER
  - [ ] Run `schemachange` V1.0.0–V1.2.0 against dev account
  - [ ] Create S3 storage integration + external stage (replace TODO in V1.1.0)
- [x] **Local dev experience**
  - [x] `scripts/bootstrap_local.sh` + `docker compose --profile apps up` verified end-to-end
  - [x] Seeded events render live tiles; `scripts/smoke_e2e.sh` automates the round-trip
- [x] **CI skeleton**
  - [x] `ci.yml` jobs defined incl. e2e compose smoke (python, dbt parse, sqlfluff, frontend, terraform validate, DAG tests, integration)
  - [x] `cd.yml` → reusable `deploy.yml` per environment (images → ECR, DAGs → S3, schemachange, ECS roll)

### Phase 2 — Core features (P1, weeks 4–8)

> Goal: the two business capabilities work: sub-second metrics and anomaly alerts.
> ✅ Verified locally end-to-end (see TECH-NOTES §3.2 for the smoke evidence).

- [x] **Batch ELT path**
  - [x] Real transform logic in `glue/jobs/raw_events_to_parquet.py` (typing, dedupe, contract **quarantine zone**, catalog partition, CloudWatch volume metrics)
  - [x] GE suites for raw (landing + parsed contract) and marts; fail-the-DAG gates wired (write–audit–publish); results recorded in `DATA_QUALITY_RESULTS`
  - [x] dbt marts: `fct_business_metrics_daily` incremental MERGE with 3-day late-data tail
  - [x] Serving-cache warm task (mart daily aggregates → Redis) after the marts gate
  - [ ] Swap BashOperator dbt step for `EcsRunTaskOperator` on MWAA *(needs the MWAA env)*
  - [ ] Source freshness checks (`dbt source freshness`) scheduled in Airflow
- [x] **Speed layer**
  - [x] DLQ producer path for poison messages (`events.deadletter.v1`, base64 + error context)
  - [x] Graceful SIGTERM drain (flush open windows → clean exit for ECS rolling deploys)
  - [x] Rolling live history per metric (Redis zsets) + periodic ops stats logging
  - [ ] Deploy to ECS; autoscale on MSK consumer-lag CloudWatch metric *(cloud op)*
  - [ ] Stand up MSK Connect + Snowflake Kafka connector (Snowpipe Streaming) *(cloud op)*
- [x] **ML anomaly detection**
  - [x] EWMA detector with per-metric parameter overrides + cooldown/dedupe in the publisher
  - [x] `anomaly_model_retrain_dag.py`: fetch window → robust-stats fit → holdout evaluation gate → publish (Redis hot-reload + S3 archive); replay parity pinned by tests
  - [x] Processor hot-reloads published models (no restart, no rebalance)
  - [x] Alert fan-out: alerts topic → API feed (Redis) with acknowledgements
  - [ ] SNS bridge → Slack + PagerDuty *(cloud op; topic ARN config exists)*
- [x] **Serving & UI**
  - [x] `history` endpoint: live granularity (Redis hot window) + daily (Snowflake marts or warmed cache)
  - [x] Pipeline CRUD persisted: Redis backend (local default) + `RAW.METADATA.PIPELINES` backend (`PIPELINE_STORE=snowflake`)
  - [x] Dashboard: sparkline per tile (dataviz stat-tile spec), staleness greying, WS reconnect UX
  - [x] Pipeline creation form (reactive, mirrors API validation), pause/activate, delete
  - [x] Alert feed: 15 s polling + acknowledge action
  - [x] API auth implemented: `AUTH_MODE=none|api_key|jwt` (HS256/JWKS — Cognito-compatible) + Angular credential interceptor
  - [ ] IdP login flow (redirect → localStorage token) + route guards *(needs the IdP)*
- [ ] **Promotion**
  - [ ] Stand up staging environment (Terraform + Snowflake clone) *(cloud op; CD path exists)*

### Phase 3 — Polish & optimization (P2, weeks 9+)

- [ ] **Performance & cost**
  - [ ] Snowflake: warehouse right-sizing, auto-suspend audit, query_tag-based cost attribution
  - [ ] MSK: partition strategy review; enable tiered storage if retention grows
  - [ ] Load tests (k6/Locust): 10× event volume; verify sub-second SLO holds; tune window/hop
- [ ] **Lineage & governance**
  - [ ] OpenLineage emitters on Airflow + dbt → Marquez (or DataHub); link GE results to datasets
  - [ ] Publish dbt docs + GE Data Docs to an internal static site on every prod run
  - [ ] Snowflake masking policies + tags for PII columns
- [ ] **Resilience & operations**
  - [ ] Blue/green (CodeDeploy) for API; canary for the stream processor (parallel consumer group)
  - [ ] DR runbook: MSK replay procedure, Snowflake Time Travel restore drill, Redis cold-start warmup
  - [ ] Chaos test: kill a processor task mid-window; verify at-least-once + idempotent sink behavior
- [ ] **Developer experience**
  - [ ] E2E tests (Playwright) against ephemeral preview stack
  - [ ] Backstage/service-catalog entry, on-call rotation, alert runbooks
  - [ ] `dbt-osmosis`/docs coverage ≥ 90 %, mypy strict mode, coverage gate ≥ 80 %

---

## Milestone acceptance criteria

| Milestone | Proof |
|---|---|
| M1 (end Phase 1) | A seeded Kafka event appears on the local dashboard; CI green; dev infra applied from `master` |
| M2 (end Phase 2) | p99 producer→dashboard < 1 s in staging at nominal load; injected anomaly pages Slack within 5 s; daily batch publishes GE-validated marts |
| M3 (end Phase 3) | Load test at 10× volume passes; lineage graph browsable; DR drill executed and documented |

## Top risks

1. **Sub-second SLO** depends on the hot path never touching Snowflake — enforce via architecture review (see ARCHITECTURE §2.4).
2. **MWAA dependency conflicts** (dbt inside Airflow) — mitigated by running dbt in its own ECS task (TECH-NOTES §3.6).
3. **Anomaly alert noise** — mitigated by warmup windows, per-metric thresholds, cooldowns, and the weekly retrain loop.
4. **Snowflake cost creep** — auto-suspend 60 s, per-workload warehouses, query tags from day one.
