# Data Lakehouse Platform — Project Plan

> Enterprise data platform: Delta Lake on S3 for ACID storage, Spark for large-scale
> processing, Kafka for event ingestion, Airflow for orchestration, dbt + Trino for
> SQL modeling and interactive queries, FastAPI + React for the governance console.
> Deployed to AWS (EMR Serverless / MWAA / ECS), CI/CD via GitHub Actions.
>
> **Status:** Phases 1 and 2 are implemented and verified (unit, Spark, and
> end-to-end smoke lanes green). Phase 3 tracks cloud-account rollout and
> operational hardening.

---

## 1.1 Project File Structure

The repository is a **monorepo** with one directory per platform concern. Python
tooling (ruff, pytest, mypy) is configured once at the root; each deployable
component owns its dependency manifest so it can be built and shipped independently.

```text
1Data-Lakehouse-Platform/
├── claude-fable-5.txt                  # Model marker file (per request)
├── README.md                           # Entry point: what this is + quickstart
├── Makefile                            # Developer entry points (up, test, smoke, ...)
├── docker-compose.yml                  # Local stack: MinIO, Kafka, Postgres, Trino, Airflow, API, Console
├── alembic.ini                         # Alembic config for the catalog metadata DB
├── pyproject.toml                      # Shared Python tooling: ruff, mypy, pytest
├── .env.example                        # Template of every runtime setting (no secrets)
├── .dockerignore / .gitignore / .editorconfig
├── .pre-commit-config.yaml             # ruff, format, yaml/json checks, secrets scan
│
├── docs/
│   ├── PROJECT-PLAN.md                 # ← this file
│   ├── ARCHITECTURE.md                 # Patterns, diagrams, data flow, security
│   ├── TECH-NOTES.md                   # CI/CD, testing, deployment, pitfalls
│   └── adr/0001-delta-lake-medallion-on-s3.md
│
├── .github/                            # ---------- CI/CD ----------
│   ├── CODEOWNERS
│   ├── pull_request_template.md
│   └── workflows/
│       ├── ci.yml                      # lint → test (+coverage gate) → build, per PR
│       └── deploy.yml                  # artifact publish + env promotion via OIDC
│
├── data-platform/                      # ---------- Spark / PySpark package ----------
│   ├── pyproject.toml                  # Package `lakehouse` (built as wheel → S3 artifacts)
│   ├── lakehouse/
│   │   ├── run_module.py               # EMR Serverless entrypoint shim (python -m semantics)
│   │   ├── common/
│   │   │   ├── config.py               # Env-driven settings (URIs, Kafka security, catalog)
│   │   │   ├── logging.py              # Structured JSON logging with run context
│   │   │   ├── spark.py                # SparkSession factory (Delta + S3A + extra connectors)
│   │   │   ├── metrics.py              # Job metrics + DQ results → Delta tables
│   │   │   └── catalog.py              # Self-registration in the governance catalog
│   │   ├── ingestion/
│   │   │   ├── kafka_bronze_stream.py  # Kafka → Bronze (config-driven security/backpressure)
│   │   │   ├── batch_file_loader.py    # Landing files → Bronze with Delta manifest idempotency
│   │   │   └── sample_producer.py      # Local dev: fake order events → Kafka
│   │   ├── jobs/
│   │   │   ├── bronze_to_silver.py     # Parse, validate, quarantine, dedupe, MERGE + metrics
│   │   │   ├── silver_to_gold.py       # Daily aggregates (partition-scoped overwrite)
│   │   │   └── table_maintenance.py    # OPTIMIZE/Z-ORDER/VACUUM + file stats + checkpoint GC
│   │   ├── features/
│   │   │   └── customer_order_features.py  # Point-in-time features + drift check
│   │   └── quality/
│   │       └── checks.py               # DQ gate: registry, single-pass eval, persisted results
│   └── tests/                          # conftest (local Spark), bronze→silver, features,
│       └── ...                         # quality, config, catalog client, batch loader
│
├── orchestration/                      # ---------- Airflow ----------
│   └── airflow/
│       ├── requirements.txt            # Pinned providers + dbt (matches MWAA image)
│       ├── dags/
│       │   ├── medallion_daily.py      # Bronze→Silver→DQ→Gold→dbt→maintenance
│       │   │                           #   (submits to EMR Serverless when configured)
│       │   └── ml_feature_pipeline.py  # Data-aware (Dataset-scheduled) feature refresh
│       └── tests/test_dag_integrity.py # Import errors, house rules (retries, tags, catchup)
│
├── transform/                          # ---------- dbt (on Trino) ----------
│   └── dbt/
│       ├── dbt_project.yml / packages.yml
│       ├── profiles/profiles.yml.example   # env_var-driven; works locally, in CI, in Airflow
│       ├── models/staging/             # _silver__sources.yml (+freshness), stg_orders(+tests)
│       ├── models/marts/               # fct_daily_revenue (+tests)
│       └── tests/assert_no_negative_revenue.sql
│
├── services/                           # ---------- Backend service ----------
│   └── api/                            # Catalog & governance API (FastAPI)
│       ├── Dockerfile                  # Repo-root context; ships migrations
│       ├── docker-entrypoint.sh        # alembic upgrade head (with retry) → uvicorn
│       ├── requirements.txt
│       ├── app/
│       │   ├── main.py                 # App factory, request-id middleware, problem handlers
│       │   ├── core/                   # config, security (JWT/JWKS + roles), JSON logging
│       │   ├── db/                     # SQLAlchemy models + lazy engine/session
│       │   ├── routers/                # datasets (CRUD+versions+preview), audit, health
│       │   ├── schemas/                # Pydantic contracts + validation
│       │   └── services/               # dataset_service, audit_publisher (Kafka), trino_client
│       └── tests/                      # 47 tests: CRUD, versions, audit, JWT, health, services
│
├── frontend/                           # ---------- React console ----------
│   ├── Dockerfile + nginx.conf         # Static build behind nginx (SPA fallback, caching)
│   ├── package.json / tsconfig.json / vite.config.ts (vitest configured)
│   └── src/
│       ├── styles.css                  # Responsive stylesheet (cards on mobile)
│       ├── api/client.ts (+tests)      # Typed fetch, problem-detail errors, bearer token
│       ├── hooks/useApiQuery.ts, useDatasets.ts
│       ├── components/
│       │   ├── DatasetTable.tsx (+tests)   # List, filters, states, selection
│       │   ├── DatasetForm.tsx (+tests)    # Registration with client-side validation
│       │   └── DatasetDetail.tsx           # Edit/delete, versions, audit trail, Trino preview
│       └── test/setup.ts
│
├── migrations/                         # ---------- Catalog DB (Postgres, Alembic) ----------
│   ├── env.py / script.py.mako
│   └── versions/20260702_0001_initial_catalog.py
│
├── infra/                              # ---------- Infrastructure as Code ----------
│   ├── terraform/
│   │   ├── main.tf / variables.tf / outputs.tf
│   │   ├── envs/{dev,staging,prod}.tfvars
│   │   └── modules/
│   │       ├── s3_lake/                # Buckets: versioning, SSE(-KMS), TLS-deny, lifecycle
│   │       ├── msk/                    # MSK Serverless + client SG ingress
│   │       └── emr_serverless/         # Spark app + least-privilege job role
│   └── docker/
│       ├── airflow/Dockerfile          # Airflow + providers + dbt (self-managed option)
│       ├── spark/Dockerfile            # Linux Spark runner (tests + smoke); wheels/ cache
│       └── trino/                      # catalog/ (local: postgres federation),
│                                       # catalog-aws/ (delta on Glue, cloud-only)
│
└── scripts/
    ├── bootstrap_local.sh              # Topics + dbt profile after compose up
    ├── create_kafka_topics.sh
    ├── run_spark_docker.sh             # Spark tests / e2e smoke in the Linux runner
    ├── smoke_e2e.py                    # Kafka→Bronze→Silver→DQ→Gold→features, asserted
    └── submit_emr_job.sh               # Ad-hoc EMR Serverless submission
```

### Ownership boundaries

| Directory | Deployable artifact | Deployed to |
|---|---|---|
| `data-platform/` | Python wheel + `run_module.py` | S3 artifacts bucket → EMR Serverless |
| `orchestration/airflow/` | DAG files (+ requirements) | MWAA S3 DAGs bucket |
| `transform/dbt/` | dbt project (runs inside Airflow worker/ECS task) | with Airflow |
| `services/api/` | Docker image (migrations included) | ECR → ECS Fargate |
| `frontend/` | Static bundle | S3 + CloudFront |
| `migrations/` | Alembic revisions | applied by deploy step / API entrypoint |
| `infra/terraform/` | Terraform plan/apply | AWS account per environment |

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority) — **complete**

- [x] **Repo & tooling**: monorepo layout, ruff/mypy/pytest config, pre-commit, CODEOWNERS
- [x] **Local dev stack**: docker-compose (MinIO, Kafka, Postgres, Trino, Airflow, API, Console) + bootstrap script
- [x] **IaC baseline**: S3 lake buckets with versioning, SSE(-KMS option), TLS-deny policies, public-access block, lifecycle tiering; `terraform validate` green
- [x] **Naming & layout conventions**: `s3://<env>-lakehouse-<layer>/<domain>/<table>/`, UTC everywhere, partition standards (`ingest_date`, `event_date`)
- [x] **Bronze ingestion path**: versioned topic contract (`orders.v1` + DLQ), Structured Streaming → Bronze Delta with checkpointing, config-driven SASL/backpressure
- [x] **Catalog metadata DB**: Alembic migration 0001 (datasets, dataset_versions, audit_events); applied automatically by the API container entrypoint
- [x] **CI skeleton**: ruff + fast tests with 70% coverage gate + Spark lane + frontend lint/test/build + `dbt parse` + `terraform validate` + DAG import test
- [x] **Secrets & identity**: GitHub OIDC deploy roles, Secrets Manager for DB URL in deploy, no secrets in the repo

### Phase 2 — Core features (medium priority) — **complete**

- [x] **Silver layer**: schema enforcement, dedupe (latest event wins), quarantine with reasons, idempotent MERGE, `--run-date` backfills
- [x] **Data-quality gates**: single-pass check engine with fail/warn severities, results persisted to a Delta audit table, non-zero exit blocks downstream tasks
- [x] **Gold layer**: Spark daily aggregates (partition-scoped overwrite) + dbt marts on Trino with schema tests and a singular business-invariant test
- [x] **Orchestration**: `medallion_daily` DAG with retries/backoff, Airflow Datasets for the data-aware `ml_feature_pipeline`, dual submission (EMR Serverless when configured, local runner otherwise)
- [x] **ML feature pipelines**: point-in-time snapshots, rolling windows, favorite-currency categorical, drift check vs previous snapshot
- [x] **Observability of pipelines**: per-run job metrics (rows in/clean/quarantined, file stats) persisted to Delta + structured JSON logs
- [x] **Governance loop**: every job self-registers its dataset + schema version + row count in the catalog; audit trail on every catalog mutation with Kafka fan-out (`platform.audit.v1`)
- [x] **Trino**: Delta catalog config (Glue), Postgres federation catalog, read-only preview service with strict identifier validation
- [x] **Catalog API + Console**: CRUD + versions + audit + preview endpoints (JWT auth with roles, RFC 7807 problem responses, request-ids); responsive React console with create/edit/delete, versions, audit, preview
- [x] **Deploy pipeline**: wheel + entrypoint → S3, image → ECR + ECS rollout, DAG/dbt sync → MWAA, Alembic via Secrets Manager, terraform apply, post-deploy smoke gate
- [x] **End-to-end verification**: `scripts/smoke_e2e.py` — real Kafka → Bronze → Silver → DQ → Gold → features with row-level assertions (dedupe, quarantine, metrics, catalog registration)

### Phase 3 — Rollout & optimization (operational; requires AWS accounts)

- [ ] Provision dev/staging/prod accounts and run the deploy workflow end-to-end (all config and gates are in place)
- [ ] Serving-tier IaC modules: MWAA environment, Trino cluster, ECS API service, CloudFront console
- [ ] Lake Formation fine-grained access (column masking for PII), PII tagging in the catalog
- [ ] Lineage & dashboards: OpenLineage emission → Marquez/DataHub; CloudWatch dashboards over the persisted job metrics/DQ tables; SLA alerts
- [ ] Cost controls: budgets + per-job tags review, Trino resource groups, EMR right-sizing from metrics history
- [ ] Resilience drills: Kafka replay runbook, Delta `RESTORE` time-travel drill, region-failure tabletop

---

## Verification summary (what "complete" means above)

| Lane | Command | Result |
|---|---|---|
| Fast tests + coverage | `make test` | 77 tests, API coverage ~96% (gate: 70%) |
| Spark transformations | `make test-spark-docker` | 11 tests in the Linux runner |
| Frontend | `make frontend-test` | eslint + 11 vitest tests + strict tsc build |
| dbt | `dbt deps && dbt parse` | project + profiles parse cleanly |
| Terraform | `terraform validate` | valid |
| End-to-end | `make smoke` | full pipeline with asserted outcomes |

### Production-hardening pass (Phase 3 audit)

Fixes shipped after auditing the Phase 2 implementation, each with a regression test:

- **NULL slip-through in Bronze→Silver validation** — Spark's `~isin(...)` is NULL
  (never true) for NULL inputs, so events with missing status/currency reached
  Silver; they now quarantine with explicit reasons, and DQ checks are NULL-safe.
- **PATCH with explicit `null` for a required field** returned a 500 (DB constraint);
  now a 422 with a clear message.
- **Unique-key write races** (dataset name, version number) surfaced as 500s;
  `IntegrityError` now maps to retryable 409 problem responses.
- **Audit fan-out ordering** — Kafka events publish strictly *after* commit (no
  phantom events), and a 60 s circuit-breaker with bounded producer timeouts stops
  a down broker from stalling request threads.
- **IdP outage** during JWT validation now returns 503 (service problem), not 401.
- Dataset names are restricted to dot-separated identifiers (each segment a valid
  Trino identifier), removing an unquotable-name 500 edge in previews;
  `schema_json` payloads are size-capped.
- Query indexes added for the observed filters (migration `0002`: datasets.layer,
  audit_events.actor); responses gzip-compressed; runtime vs dev requirements
  split so test tooling stays out of the production image; Dependabot configured;
  `--run-date` arguments validated as strict ISO dates in every job entrypoint.

## Top risks

| Risk | Mitigation |
|---|---|
| Small-file explosion in Bronze (streaming) | availableNow triggers + scheduled OPTIMIZE + file-count metrics per table |
| Schema drift from producers | Versioned topic contracts, quarantine with reasons + DQ gate instead of pipeline failure |
| Delta concurrent-write conflicts | Partition-scoped writes; `max_active_runs=1` per DAG; single writer per table |
| dbt/Trino vs Spark ownership confusion | Hard rule enforced in docs and reviews: Spark owns Bronze/Silver, dbt owns Gold marts |
| Cost runaway (EMR + S3 requests) | Budgets + per-job tags from day one; metrics history feeds right-sizing in Phase 3 |
