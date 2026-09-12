# Data Lakehouse Platform — Technical Notes

## 3.1 CI/CD Pipeline Design

Two workflows in `.github/workflows/`:

- **`ci.yml`** — runs on every PR and push to `main`. Fast feedback, no AWS access.
- **`deploy.yml`** — runs on version tags / manual dispatch. Publishes artifacts and
  promotes through environments with approval gates (GitHub *environments*).

```mermaid
flowchart LR
    subgraph CI["ci.yml — every PR"]
        L["Lint<br/>ruff + eslint + tf fmt"] --> T["Test<br/>pytest / dbt parse / DAG import"]
        T --> B["Build<br/>vite build + wheel + images (no push)"]
    end
    subgraph CD["deploy.yml — tag or dispatch"]
        P["Publish<br/>wheel→S3, image→ECR,<br/>DAGs→MWAA bucket"] --> DEV["Deploy dev<br/>terraform apply + alembic + smoke"]
        DEV --> STG["staging<br/>(auto, gated by smoke)"]
        STG --> PRD["prod<br/>(manual approval)"]
    end
    CI --> CD
```

Key stages per component:

| Stage | data-platform | airflow | dbt | api | frontend | infra |
|---|---|---|---|---|---|---|
| Lint | ruff | ruff | dbt schema tests | ruff | eslint, prettier | `terraform fmt` |
| Test | pytest (`-m "not spark"` fast lane, `-m spark` lane) | DAG import + house rules | `dbt parse` in CI, `dbt build` in prod runs | pytest (70% coverage gate; currently ~96%) | vitest + `tsc --noEmit` | `terraform validate` |
| Build | wheel + `run_module.py` | — | — | Docker image (ships migrations) | vite bundle | plan |
| Deploy | `aws s3 cp` wheel | S3 sync DAGs + dbt bundle | runs in Airflow | ECR push + ECS rolling update | S3 sync + CloudFront invalidation | apply |

Principles: **artifacts are immutable and versioned** (wheel version = git tag; image
tag = git SHA); the *same* artifact is promoted dev → staging → prod — environments
differ only by configuration. CI never has AWS credentials; deploy uses GitHub OIDC.

## 3.2 Testing Strategy

**Unit (fast lane — every PR, seconds):**
- Python: `pytest -m "not spark"` at repo root; API tested with `TestClient` +
  dependency-overridden in-memory SQLite; JWT auth tested against a locally
  generated RSA key with the JWKS lookup patched; Kafka/Trino integrations tested
  through injected fakes.
- Coverage gate: **70%** enforced in CI on the API package (currently ~96%);
  transformations are covered by the Spark lane instead of chasing a number.
- Dependencies stay patched via Dependabot (`.github/dependabot.yml`): weekly
  grouped PRs across pip/npm/actions/docker, each gated by the full CI run.

**Spark transformation tests (`-m spark` lane, minutes):**
- Local `SparkSession` fixture (`data-platform/tests/conftest.py`) + tiny in-memory
  DataFrames; assert on collected rows.
- Test *transform functions* (e.g. `refine_orders`, `compute_features`), never jobs
  end-to-end — jobs are thin CLI wrappers around testable functions.
- On workstations where local Spark workers are unreliable (see §3.6 #14), run the
  identical lane in the Linux runner image: `make test-spark-docker`.

**dbt:**
- Schema tests (`unique`, `not_null`, `accepted_values`, `relationships`) on every
  model — they run in production too via `dbt build`.
- Singular tests for business invariants (`assert_no_negative_revenue.sql`).
- `dbt parse` in CI catches ref/jinja errors without a warehouse connection.

**Orchestration:**
- DAG integrity test: DagBag import produces zero errors, no cycles — catches ~90% of
  Airflow breakage pre-deploy.

**Integration / end-to-end (implemented — `make smoke`):**
- `scripts/smoke_e2e.py` runs against the compose stack: produces real Kafka events
  (valid + duplicates + invalid), drains them through Bronze → Silver → DQ gate →
  Gold → feature snapshot, then asserts dedupe semantics, quarantine counts,
  persisted metrics/DQ results, and catalog self-registration. It runs inside the
  `lakehouse-spark` Linux runner joined to the compose network.

**Post-deploy smoke (per environment, in deploy.yml):**
- `curl` probes of `/healthz`, `/readyz`, and the dataset list gate promotion to the
  next environment. Browser-level E2E (Playwright) is a Phase 3 addition if the
  Console grows beyond the current screens.

**Data quality is not testing** — DQ gates (`lakehouse/quality/checks.py`) run *in
production on every run*, because production data changes even when code doesn't.

## 3.3 Deployment Strategy

- **Spark**: code ships as a versioned wheel to `s3://<env>-lakehouse-artifacts/wheels/`;
  EMR Serverless jobs reference `--py-files` + entrypoint. No cluster to maintain;
  per-job sizing. (Databricks alternative: same wheel via asset bundles — decision
  recorded in ADR-0001.)
- **Airflow**: MWAA; DAGs synced to the MWAA S3 bucket. DAGs stay thin (no business
  logic) so a DAG deploy is low-risk. `requirements.txt` pinned to the MWAA image.
- **dbt**: runs inside the Airflow worker (or an ECS task for isolation); the dbt
  project ships with the DAG bundle.
- **Containerization**: only the *services* are containerized — API image (ECS
  Fargate behind ALB, rolling deploy, `/healthz` checks) and a Spark image for
  local/CI parity. Frontend is static: S3 + CloudFront, atomic by versioned prefix.
- **DB migrations**: Alembic runs as a deploy step *before* the new API tasks roll
  out; migrations must be backward-compatible one release back (expand → migrate →
  contract).
- **Environments**: three AWS accounts (dev/staging/prod) — hard blast-radius
  isolation; identical Terraform with per-env `tfvars`.
- **Rollback**: API = previous image tag; Spark = previous wheel version; data =
  Delta time travel (`RESTORE TABLE ... TO VERSION AS OF`) — practiced via runbook.

## 3.4 Environment Management

- **Precedence**: real env vars > `.env` file > safe defaults (local dev values).
  Code never contains environment names in logic (`if env == "prod"` is banned).
- **Local**: `.env` (gitignored) from `.env.example`; docker-compose provides
  MinIO/Kafka/Postgres/Trino/Airflow.
- **AWS**: ECS task definitions and EMR job configs inject env vars; secrets come
  from Secrets Manager (never in Terraform state as plaintext — use data sources).
  Airflow connections/variables via the Secrets Manager backend.
- **dbt**: one `profiles.yml` driven entirely by `env_var()` with local defaults —
  the same profile works locally, in CI (`dbt parse`), and in Airflow.
- **Terraform**: `envs/{dev,staging,prod}.tfvars` + separate state per environment.

`.env.example` (committed template — full version at repo root):

```dotenv
# --- AWS / S3 (local values point at MinIO) ---
AWS_REGION=eu-central-1
AWS_ENDPOINT_URL=http://localhost:9000        # unset in real AWS
AWS_ACCESS_KEY_ID=minioadmin                  # local only — never commit real keys
AWS_SECRET_ACCESS_KEY=minioadmin
LAKE_BRONZE_URI=s3a://lakehouse-bronze
LAKE_SILVER_URI=s3a://lakehouse-silver
LAKE_GOLD_URI=s3a://lakehouse-gold
# --- Kafka ---
KAFKA_BOOTSTRAP_SERVERS=localhost:9094
KAFKA_ORDERS_TOPIC=orders.v1
# --- Catalog DB / API ---
DATABASE_URL=postgresql+psycopg://lakehouse:lakehouse@localhost:5432/catalog
API_CORS_ORIGINS=http://localhost:5173
# --- Trino / dbt ---
TRINO_HOST=localhost
TRINO_PORT=8081
# --- Frontend ---
VITE_API_BASE_URL=http://localhost:8000
```

## 3.5 Version Control Workflow

**Trunk-based development** with short-lived branches:

- Branch from `main` (`feat/...`, `fix/...`), PR within 1–2 days, squash-merge.
- `main` is always deployable; releases are **tags** (`v1.4.0`), and promotion happens
  by moving *artifacts* through environments — not by long-lived release branches.
- Required PR checks: full `ci.yml`, one review (CODEOWNERS routes data-model changes
  to the platform team), conventional-commit titles for changelog generation.

**Why not Gitflow:** a data platform deploys many small units (a DAG, a model, a
migration) continuously; Gitflow's release branches add merge drift and delay fixes.
Trunk-based keeps schema migrations linear — critical for Alembic and Delta schema
evolution, where parallel divergent migration histories are painful. Feature flags /
DAG pause-unpause cover incomplete work.

## 3.6 Common Pitfalls (this stack specifically)

1. **Small files in Bronze.** Streaming appends thousands of tiny parquet files;
   queries slow to a crawl. → `availableNow` triggers, scheduled `OPTIMIZE`
   (compaction) from day one, monitor file count per partition.
2. **Delta concurrent-write conflicts.** Two jobs MERGEing the same table throw
   `ConcurrentAppendException`. → one writer per table, partition-scoped predicates,
   orchestrate ordering in Airflow rather than relying on retries.
3. **`VACUUM` vs time travel / streaming.** Vacuuming below the default 7-day
   retention breaks time travel and any stream reading the table's history. Never
   `spark.databricks.delta.retentionDurationCheck.enabled=false` in prod.
4. **Exactly-once is a myth at the edges.** Kafka + checkpoints give at-least-once
   into Bronze. Design Silver as the dedupe point (MERGE on business key +
   `dropDuplicates`) instead of chasing transactional producers.
5. **Kafka retention vs replay.** If Bronze ingestion breaks longer than topic
   retention, data is gone. → retention ≥ 7 days, lag alerts, and Bronze-from-Kafka is
   the *only* consumer that must never fall behind.
6. **Schema evolution discipline.** `mergeSchema=true` sprinkled everywhere turns
   Silver into a junk drawer. → additive-only evolution via explicit contract change
   (PR + catalog update); anything else goes to quarantine.
7. **Airflow logical dates.** `{{ ds }}` is the *data interval start*, not "today".
   Off-by-one bugs in backfills are the classic symptom; test a 3-day backfill early.
8. **Top-level DAG code.** Heavy imports or S3 calls at module level slow the
   scheduler loop for *every* DAG. Keep DAG files declarative (they only shell out).
9. **dbt-on-Trino specifics.** Not all incremental strategies are supported per
   connector; verify `merge` works against the Delta connector version, and pin
   `dbt-trino` to the Trino version. Watch timestamp precision (µs) mismatches
   between Spark and Trino.
10. **Trino needs a metastore.** The Delta connector requires Glue or a Hive
    metastore — plan it in Terraform; locally either run an HMS container or register
    tables explicitly (see `infra/docker/trino/catalog/delta.properties`).
11. **Timezones.** UTC everywhere: Spark session TZ, Airflow, Postgres
    (`timestamptz`), Kafka timestamps. Convert only at the UI edge.
12. **Cost surprises.** S3 request costs from small files, EMR jobs left oversized,
    Trino full scans without partition pruning. Tag every job, budget alarms in
    Phase 1, partition-filter enforcement (`delta.checkpoint` stats + query review).
13. **PII creep into Bronze.** Once raw PII lands in immutable Bronze, erasure is
    expensive. Tokenize at ingestion where legally required, or accept targeted
    Delta `DELETE` + `VACUUM` as the erasure path and rehearse it.
14. **Local Spark on Windows workstations.** Two distinct traps: (a) Hadoop needs
    `winutils.exe`/`hadoop.dll` (set `HADOOP_HOME`), and (b) corporate endpoint
    protection can kill JVM-spawned Python worker processes mid-socket, which
    surfaces as `Python worker exited unexpectedly` with no traceback. Don't fight
    it — `make test-spark-docker` runs the identical lane in the Linux runner image,
    which is also what CI uses.
15. **Docker Hub image churn.** Vendor images disappear (bitnami's 2025 catalog
    withdrawal broke `bitnami/kafka` tags). Prefer official project images
    (`apache/kafka`) and pin exact versions.
