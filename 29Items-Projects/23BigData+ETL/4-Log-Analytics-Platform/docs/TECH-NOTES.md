# Log Analytics Platform — Technical Notes

## 3.1 CI/CD Pipeline Design

Three workflows in `.github/workflows/`: `ci.yml` and `deploy.yml` below, plus
`retrain.yml` — weekly anomaly-model retraining from OpenSearch history into the S3
registry, with a holdout drift guard (details: RUNBOOKS.md runbook 5).

**`ci.yml` — every PR and every push to `main`**

| Stage | What runs | Gate |
|---|---|---|
| 1. Lint | `ruff check` + `ruff format --check` | hard fail |
| 2. Typecheck | `mypy src` | hard fail |
| 3. Unit tests | `pytest -m "not integration and not e2e"` + coverage | fail < 80% on core packages |
| 4. Integration tests | compose brings up Kafka + OpenSearch → `pytest -m integration` | hard fail |
| 5. Build & push | Docker buildx → ECR (`app` + `spark` images), tags `sha-<git-sha>` | `main` only, OIDC auth |

Lint/typecheck/unit run in parallel; integration and build wait on them. Concurrency groups
cancel superseded runs per branch. Pip cache keyed on requirements files.

**`deploy.yml` — environment-gated delivery**

1. Push to `main` → auto-deploy **dev**; **staging**/**prod** via `workflow_dispatch` with a
   chosen image tag, protected by GitHub Environment required reviewers.
2. Order inside a deploy: assume OIDC role → **apply search-schema migrations**
   (`scripts/es_migrate.py`, additive-only — see pitfalls) → update ECS services (gateway,
   api, alerting) → package Spark sources to S3 → restart EMR Serverless streaming job runs →
   smoke test (`/healthz`, cluster health, consumer-group liveness).
3. Migrations before services; Spark jobs last (they tolerate old data shape by design).

Rollback = redeploy previous image tag (`workflow_dispatch` input); schema migrations are
forward-only and additive, so old code always runs against new schema.

## 3.2 Testing Strategy

**Pyramid, enforced by pytest markers** (`unit` default, `integration`, `e2e`):

- **Unit (target: seconds, ≥80% coverage on `common/`, `ml/`, `alerting/`, `api/`)**
  - Framework: `pytest`; FastAPI endpoints tested with `TestClient` and dependency
    overrides (see `tests/unit/test_api_alert_rules.py` — full CRUD round-trip, no network).
  - Business logic is deliberately Spark-free, so parsing, feature engineering, rule
    evaluation and the Isolation Forest wrapper all unit-test directly (fixtures in
    `tests/conftest.py`).
  - ML tests assert *behavior*, not weights: outliers must score higher than normal traffic;
    save/load round-trips must produce identical scores.
- **Integration (CI + on demand):** real Kafka and OpenSearch from `docker-compose.yml`;
  tests skip automatically when endpoints aren't reachable (`tests/integration/`). Cover:
  migration runner idempotency (apply twice → second is a no-op), produce/consume round-trip,
  gateway → topic contract. Spark transformation tests (`test_spark_transforms.py`) run
  against a `local[2]` session and are marked `integration` (~30 s JVM spin-up): parse/DLQ
  split, enrichment invariants, and the Python↔Spark contract (schema fields = `LogEvent`,
  `template_id` parity through both regex engines). On Windows run them inside the spark
  image (command in the test docstring). Consider `testcontainers-python` once the suite grows.
- **E2E (nightly + pre-release):** full compose stack, `seed_sample_logs.py --burst-errors`,
  then assert via OpenSearch queries: logs indexed, anomaly docs present, alert doc created.
  Skeleton in `tests/e2e/test_pipeline_smoke.py`. Load testing with k6 against the gateway
  is a Phase-3 item.
- **Contract discipline:** `common/models.py` is the schema for every Kafka topic — any
  producer/consumer change goes through those models and their tests.

## 3.3 Deployment Strategy

**Everything is a container or a managed service; Terraform owns the wiring (`infra/terraform/`).**

| Component | Runtime | Notes |
|---|---|---|
| Kafka | **Amazon MSK** (3 brokers, 3 AZ, TLS) | topics created by `create_kafka_topics.py` in deploy |
| Spark jobs | **EMR Serverless** (Spark 3.5) | code zip + venv archive on S3; one long-running job run per streaming job; checkpoints in S3 |
| Gateway / API / Alerting | **ECS Fargate** behind ALB | one shared image (`docker/app.Dockerfile`), different `CMD`; rolling deploys, min healthy 100% |
| Search & dashboards | **AWS OpenSearch** domain | fine-grained access control, SigV4; ISM lifecycle from migrations |
| Model registry / raw archive / checkpoints | **S3** | versioned bucket, `latest.json` pointer per model |
| Alert dedup | **ElastiCache Redis** (or single-node t4g for MVP) | TTL-based SETNX |

- **Images:** `docker/app.Dockerfile` (python-slim, non-root TODO) for all three services —
  one build, three task definitions. `docker/spark.Dockerfile` exists for local pipeline work
  and an EMR-on-EKS future; EMR Serverless itself consumes plain code artifacts, not images.
- **Streaming-job upgrades:** stop job run → new run from same checkpoint (state-compatible
  changes), or new consumer group + fresh checkpoint with overlap window (breaking changes).
  Document per release which kind it is.
- **Local = prod-shaped:** the compose file mirrors the AWS topology 1:1 (OpenSearch not ES,
  same topics, same env vars), which is what makes the integration tests honest.

## 3.4 Environment Management

- **One mechanism:** typed settings via `pydantic-settings` (`common/config.py`), env vars
  prefixed `LA_`. No per-env YAML forks; behavior differences are data, not code.
- **Local:** `.env` file (gitignored) loaded automatically; start from `.env.example`.
- **CI:** vars set in workflow / GitHub Environments.
- **AWS:** non-secrets in ECS task definitions & EMR job parameters (Terraform-managed);
  secrets referenced from AWS Secrets Manager / SSM at task start. Never baked into images.

| Setting | dev (local) | staging | prod |
|---|---|---|---|
| `LA_KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | MSK TLS endpoints | MSK TLS endpoints |
| `LA_OPENSEARCH_URL` | `http://localhost:9200` | domain endpoint | domain endpoint |
| `LA_APP_ENV` | `dev` | `staging` | `prod` |
| Auth | none / basic | SigV4 + FGAC | SigV4 + FGAC |
| Retention (ISM) | 3 days | 7 days | 30 days + UltraWarm |

**`.env.example` template** (the real file lives at repo root; copy to `.env`):

```dotenv
# ── Core ─────────────────────────────────────────────
LA_APP_ENV=dev
LA_LOG_LEVEL=INFO

# ── Kafka ────────────────────────────────────────────
LA_KAFKA_BOOTSTRAP_SERVERS=localhost:29092

# ── OpenSearch / Elasticsearch ───────────────────────
LA_OPENSEARCH_URL=http://localhost:9200
LA_OPENSEARCH_USERNAME=
LA_OPENSEARCH_PASSWORD=

# ── Ingestion gateway ────────────────────────────────
LA_GATEWAY_API_KEYS=            # comma-separated; empty = auth disabled (dev only!)
LA_GATEWAY_MAX_BATCH=1000

# ── Alerting ─────────────────────────────────────────
LA_REDIS_URL=redis://localhost:6379/0
LA_SLACK_WEBHOOK_URL=
LA_PAGERDUTY_ROUTING_KEY=
LA_ANOMALY_ALERT_THRESHOLD=0.8
LA_ANOMALY_CRITICAL_THRESHOLD=0.95
LA_ALERT_DEDUP_TTL_SECONDS=300
LA_RULES_PATH=config/alert_rules.yaml
LA_SILENT_SERVICE_CHECK_SECONDS=60
LA_SILENT_SERVICE_AFTER_MINUTES=10

# ── ML / Spark ───────────────────────────────────────
LA_MODEL_REGISTRY_URI=./models
LA_SPARK_CHECKPOINT_DIR=./.checkpoints
LA_MODEL_REFRESH_SECONDS=300
LA_ANOMALY_MIN_EVENTS=10

# ── Query/Admin API ──────────────────────────────────
LA_RULES_BACKEND=auto
LA_CORS_ORIGINS=
```

(Full annotated template with gateway rate-limit and Kafka SASL settings: `.env.example`.)

## 3.5 Version Control Workflow

**Trunk-based development** — short-lived feature branches (≤ ~2 days) merged to `main` via PR;
`main` is always deployable; releases are tags (`v0.4.0`), and environment promotion is a
deploy action on an existing artifact, **not** a merge to another branch.

Why not Gitflow: this platform ships continuously and has streaming jobs where small,
frequent, individually-verifiable changes are dramatically safer than big release batches
(checkpoint/state compatibility is per-change reasoning). Long-lived `develop`/`release`
branches add merge ceremony and delay integration for zero benefit at this team size.
GitHub Flow ≈ what we do; "trunk-based" adds the explicit rules: feature flags over branches
for unfinished work, PRs small, CI required, `main` protected (no force-push, required checks,
1 review).

## 3.6 Common Pitfalls (this stack specifically)

1. **Elasticsearch client vs OpenSearch server.** `elasticsearch-py` ≥ 7.14 refuses to talk
   to OpenSearch (product check). Standardize on `opensearch-py` (done in `requirements.txt`).
   Same for the Spark connector: use the OpenSearch fork or a REST `foreachBatch` sink (we do
   the latter — no jar dependency, easier to reason about retries).
2. **`flattened` vs `flat_object`.** Elastic's `flattened` type is `flat_object` on OpenSearch.
   Our mappings use `flat_object`; if you run the Elastic profile locally, migrations 0002+
   need that one word swapped. This is exactly why local default = OpenSearch.
3. **Mapping explosion.** Never index arbitrary log fields dynamically — one team logging a
   10k-key JSON blob will brick the cluster mapping. We set `dynamic: false` and shove
   unknown structure into a `flat_object` `attributes` field. Keep it that way.
4. **Spark↔Kafka connector versioning.** `spark-sql-kafka-0-10_2.12:3.5.1` must match Spark
   3.5.x and Scala 2.12 exactly; a mismatch fails at runtime, not build time. Pinned in
   `scripts/submit_spark_job.sh` and `requirements-spark.txt` (pyspark==3.5.1).
5. **Checkpoint compatibility.** Changing a streaming query's aggregation schema, watermark,
   or output mode can make old checkpoints unreadable. Treat checkpoints as part of the
   deployed artifact; breaking changes need a new checkpoint dir + consumer-group strategy
   (see §3.3). Never point two job versions at one checkpoint.
6. **"Exactly-once" is a lie at the sink.** Kafka→Spark is at-least-once into `foreachBatch`.
   Idempotent writes (deterministic `_id`) are what make it *effectively* once. If someone
   removes the `doc_id` logic in `enrichment.py`, duplicates appear only during failures —
   i.e., in production. Guard it with tests.
7. **Rollover alias misconfiguration.** If `plugins.index_state_management.rollover_alias`
   doesn't match the write alias, ISM silently fails and one index grows forever. Migration
   0003 sets both; verify with `GET _plugins/_ism/explain/la-logs-000001` after first deploy.
8. **Watermark vs reality.** Agents buffer; clocks skew. A 2-minute watermark drops logs that
   arrive 10 minutes late *from the aggregations* (they still land in search). Monitor the
   late-drop metric before tightening watermarks.
9. **Isolation Forest gotchas.** `contamination` sets the decision threshold, not reality —
   prefer scoring (`score_samples`) + a threshold you control (we normalize to 0–1 and alert
   ≥ `LA_ANOMALY_ALERT_THRESHOLD`). Retrain on a schedule (weekly) or drift will turn either
   silent (misses) or noisy (pages at 3am). Train per service-tier if volumes differ by 100×.
10. **Backpressure after downtime.** Without `maxOffsetsPerTrigger`, a job restarted after an
    hour of lag pulls the entire backlog into one micro-batch and OOMs. Always bound it.
11. **Windows dev machines.** PySpark workers are unreliable on Windows (endpoint-protection
    kills them here). Run Spark jobs inside the Linux containers (`docker/spark.Dockerfile`)
    or WSL; everything else (API, tests, tooling) runs natively fine.
12. **Nested-repo caveat.** GitHub Actions only triggers on workflows at the repository root.
    This project currently lives in a subfolder of a bigger repo — the `.github/` here becomes
    active when the project is extracted into its own repository (assumed by the workflows).
13. **Kibana vs OpenSearch Dashboards saved objects.** Exports are not cross-compatible.
    We standardize on OpenSearch Dashboards NDJSON (matches AWS prod). Don't import Elastic
    Kibana exports into Dashboards or vice versa and expect visualizations to survive.
14. **aiokafka + system proxies (local quirk):** localhost websocket/broker connections can
    hang behind corporate proxies; ensure `NO_PROXY=localhost,127.0.0.1` in shells that run
    local integration tests.
15. **pyarrow: required by Spark, optional for services.** Two distinct needs: (a) any
    pandas-on-Spark op (`mapInPandas` in `anomaly_scoring`) serializes via Arrow — the job
    dies at startup without pyarrow, so the spark image and `requirements-spark.txt`
    install it (EMR Serverless ships it built-in); (b) `train.py --source parquet` uses
    `pandas.read_parquet`. It stays out of `requirements.txt` on purpose — the three
    Fargate services never need it, and it's a heavy wheel.
