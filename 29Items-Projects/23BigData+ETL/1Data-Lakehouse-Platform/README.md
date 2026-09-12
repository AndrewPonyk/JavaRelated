# Data Lakehouse Platform

Enterprise lakehouse: **Delta Lake on S3** (ACID storage) · **Spark** (processing) ·
**Kafka** (ingestion) · **Airflow** (orchestration) · **dbt + Trino** (SQL modeling &
queries) · **FastAPI + React** (governance console). Deployed on AWS via GitHub Actions.

Implemented end to end: streaming ingestion → medallion promotion with data-quality
gates → gold marts & ML features → self-registering governance catalog with audit
trail, JWT auth, and a responsive console.

## What it does

**Data pipeline (Spark + Delta Lake)**

- Ingests order events from Kafka into the Bronze layer via Structured Streaming — checkpointed, replayable, raw payloads kept immutable with Kafka lineage columns
- Batch-loads landing files (SFTP/partner drops) into Bronze with a Delta manifest, so re-runs never double-load a file
- Promotes Bronze → Silver: parses against the versioned `orders.v1` contract, quarantines invalid rows with explicit reasons (missing keys, negative amounts, missing/unknown status, missing currency), deduplicates at-least-once delivery (latest event per key wins), and MERGEs idempotently — backfills and retries are always safe
- Enforces data-quality gates between layers: registry-driven checks with `fail`/`warn` severities; a failing gate blocks all downstream tasks (non-zero exit is the Airflow contract) and results are persisted to a Delta audit table
- Builds Gold aggregates (daily order stats per currency) with partition-scoped overwrites
- Builds analyst marts with dbt on Trino (`stg_orders` → incremental-merge `fct_daily_revenue`) with schema tests, source-freshness SLAs, and a business-invariant test
- Computes point-in-time ML feature snapshots per customer (7/30-day rolling spend and order counts, lifetime stats, favorite currency) with no target leakage, and drift-checks each snapshot against the previous one
- Maintains tables automatically: OPTIMIZE/Z-ORDER compaction, VACUUM (guarded to never break time travel), stale streaming-checkpoint cleanup, file-count/size metrics per table
- Records per-run job metrics (rows in/clean/quarantined, totals) to Delta and emits structured JSON logs with run context

**Orchestration (Airflow)**

- Runs the daily medallion pipeline (drain Kafka → silver → DQ gate → gold → dbt → maintenance) with retries and exponential backoff
- Triggers the ML feature pipeline data-aware (Airflow Datasets) — it runs when gold data actually updates, and backfills re-trigger it automatically
- Submits Spark jobs to EMR Serverless when configured (MWAA in AWS) or to the local runner otherwise — same job code either way

**Governance catalog (FastAPI + Postgres)**

- Registers, lists, and searches datasets (layer filter, exact-name lookup, pagination); updates owner/description; deletes catalog entries (never the underlying data)
- Tracks auto-numbered schema version history with row counts per dataset
- Records a full audit trail of every mutation (who/what/when/details), queryable by entity and actor — and fans events out to the Kafka topic `platform.audit.v1` (best-effort, circuit-breakered)
- Lets pipelines self-register their outputs: every job reports its dataset, schema, and row count to the catalog after each run
- Serves read-only, LIMIT-capped data previews through Trino with strict identifier validation
- Enforces JWT auth against your IdP's JWKS (RS256, audience, configurable roles claim): reads need a valid principal, writes need `platform-admin`/`data-engineer`; explicit dev mode when no IdP is configured
- Returns RFC 7807 problem responses (400/401/403/404/409/422/502/503), correlates everything with `X-Request-ID`, gzips large responses, exposes `/healthz` + dependency-aware `/readyz`, and applies its own DB migrations on startup

**Console (React)**

- Browses the catalog with layer filters and badges; responsive layout (tables become cards on mobile)
- Registers datasets through a validated form (mirrors the API rules, surfaces conflicts)
- Shows a detail view per dataset: metadata, inline edit, delete with confirmation, schema-version history, audit trail, and a Trino data preview with clean degraded-state errors
- Handles loading/error/empty states everywhere with retry

**Platform & operations**

- Starts the entire stack with one command (`docker compose up -d --build`): MinIO, Kafka, Postgres, Trino, Airflow, API, Console
- Proves the whole platform with one command (`make smoke`): real Kafka events through every layer with asserted outcomes (dedupe, quarantine counts, metrics, catalog registration)
- Runs Spark tests in a Linux runner image (`make test-spark-docker`) so they pass on any workstation
- Ships CI with 7 gates (lint, tests + coverage gate, Spark lane, DAG integrity, dbt parse, frontend, terraform) and a deploy workflow (OIDC, immutable artifact promotion dev → staging → prod, migrations from Secrets Manager, post-deploy smoke gates)
- Provisions the AWS data plane with Terraform: lake buckets (versioning, SSE/KMS, TLS-deny, lifecycle tiering), MSK Serverless, EMR Serverless with a least-privilege job role
- Federates queries in Trino: the catalog DB is queryable next to lake tables (`catalogdb.public.datasets`)
- Keeps dependencies patched via Dependabot (pip/npm/actions/docker, weekly grouped PRs)

## Documentation

| Doc | Contents |
|---|---|
| [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) | Repo layout, ownership, phased plan with verification status |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Pattern, diagrams, data flow, API surface, security, error handling |
| [docs/TECH-NOTES.md](docs/TECH-NOTES.md) | CI/CD, testing strategy, deployment, environments, pitfalls |

## Prerequisites

- Docker (Desktop) with compose v2
- Python 3.11+ (fast test lane; the Spark lane can run fully inside Docker)
- Node 20+ (only for frontend development outside Docker)

## Quickstart (local, ~5 minutes after image pulls)

```bash
cp .env.example .env          # local defaults match docker-compose.yml
docker compose up -d --build  # MinIO, Kafka, Postgres, Trino, Airflow, API, Console
bash scripts/bootstrap_local.sh   # Kafka topics + dbt profile
```

Then open:

| Service | URL |
|---|---|
| Console (React) | http://localhost:3000 |
| Catalog API (OpenAPI docs) | http://localhost:8000/docs |
| Airflow | http://localhost:8085 (standalone; admin password in `docker compose logs airflow`) |
| Trino | http://localhost:8081 |
| MinIO console | http://localhost:9001 (minioadmin / minioadmin) |

The API container applies Alembic migrations on startup. In dev mode (no
`AUTH_JWKS_URL`) requests act as a local platform-admin; set the JWKS URL of your
IdP to enforce JWT auth with roles (see `.env.example`).

## Run the pipeline end-to-end

```bash
make smoke        # = scripts/run_spark_docker.sh smoke
```

This produces real Kafka events (valid + duplicates + invalid), runs the actual
jobs — Bronze streaming ingest, Bronze→Silver (quarantine + dedupe + MERGE), the
DQ gate, Silver→Gold aggregates, the ML feature snapshot — and asserts row-level
outcomes, persisted metrics, and catalog self-registration. Expected final line:
`SMOKE E2E: ALL CHECKS PASSED`.

## Tests

```bash
pip install -r services/api/requirements-dev.txt   # once (fast lane deps)

make test                # fast lane + API coverage gate (70%; currently ~96%)
make test-spark-docker   # Spark transformation tests in the Linux runner image
make frontend-test       # eslint + vitest + strict tsc build
```

Native `make test-spark` also works on Linux/macOS (and on Windows with
`HADOOP_HOME` pointing at winutils — see TECH-NOTES §3.6 #14 for why the Docker
lane exists).

## API examples

Interactive docs live at http://localhost:8000/docs (OpenAPI). The essentials:

```bash
# register a dataset (dev mode; add "Authorization: Bearer <jwt>" when auth is enforced)
curl -X POST http://localhost:8000/api/v1/datasets \
  -H "Content-Type: application/json" \
  -d '{"name":"sales.orders","layer":"silver","owner_email":"eng@example.com",
       "s3_path":"s3://dev-lakehouse-silver/sales/orders","description":"orders"}'

# list / exact-name lookup / filter by layer (paginated)
curl "http://localhost:8000/api/v1/datasets?layer=silver&limit=20&offset=0"
curl "http://localhost:8000/api/v1/datasets?name=sales.orders"

# register a schema version (pipelines do this automatically after each run)
curl -X POST http://localhost:8000/api/v1/datasets/<id>/versions \
  -H "Content-Type: application/json" \
  -d '{"schema_json":{"order_id":"string","amount":"double"},"row_count":80}'

# audit trail for one dataset
curl "http://localhost:8000/api/v1/audit?entity_type=dataset&entity_id=<id>"

# read-only sample via Trino (502 problem response if Trino/metastore is absent)
curl "http://localhost:8000/api/v1/datasets/<id>/preview?limit=10"
```

Errors are RFC 7807 problem JSON (`400/401/403/404/409/422/502/503`), and every
response carries an `X-Request-ID` header for log correlation.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `docker compose up` fails pulling `bitnami/*` | Bitnami left Docker Hub (2025); this repo already uses `apache/kafka` — pull errors on other images usually mean Docker Desktop isn't fully started yet |
| Port conflict on 8080/8081/3000/8000 | Another local app owns the port. Airflow is already mapped to **8085** for this reason; adjust the left side of the `ports:` mapping in `docker-compose.yml` |
| API container restarts with migration errors | Postgres wasn't healthy yet — the entrypoint retries 10×; check `docker compose logs api postgres` |
| `/readyz` shows `"trino": "unreachable"` | Trino takes ~45 s to start; the API is still fully functional (previews degrade to a 502 problem response) |
| Preview returns 502 `Catalog 'delta' not found` | Expected locally: the Delta catalog needs a Glue/Hive metastore (`infra/docker/trino/catalog-aws/`); local Trino serves the `catalogdb` federation catalog |
| Local `pytest -m spark` crashes workers on Windows | Endpoint protection kills JVM-spawned Python workers; use `make test-spark-docker` (see TECH-NOTES §3.6 #14) |
| First Spark run downloads jars | ivy resolves Delta/Kafka connectors once; they persist in the `lakehouse-ivy` Docker volume |
| Frontend shows "Catalog API is unreachable" | API not up or CORS origin mismatch — check `API_CORS_ORIGINS` matches the console origin |

## Repository layout (short)

- `data-platform/` — PySpark package `lakehouse` (ingestion, medallion jobs, features, DQ, metrics, catalog client)
- `orchestration/airflow/` — DAGs (EMR Serverless or local submission) and DAG tests
- `transform/dbt/` — dbt project (Gold marts on Trino)
- `services/api/` — FastAPI governance catalog (datasets, versions, audit, preview)
- `frontend/` — React console
- `migrations/` — Alembic migrations for the catalog DB
- `infra/` — Terraform (S3 lake, MSK, EMR Serverless) + Docker + Trino catalogs
- `scripts/` — bootstrap, topic creation, Spark runner, e2e smoke, EMR submission
- `.github/workflows/` — `ci.yml` (7 gates) and `deploy.yml` (OIDC, env promotion)

## Deploying to AWS

`deploy.yml` publishes immutable artifacts (wheel → S3, image → ECR, DAGs/dbt →
MWAA bucket, console → S3+CloudFront), applies Terraform, runs migrations from
Secrets Manager, and gates promotion on smoke probes. Configure per-environment
GitHub environment variables (listed in the workflow header) and an OIDC deploy
role; prod carries a required-reviewer rule. See TECH-NOTES §3.3.
