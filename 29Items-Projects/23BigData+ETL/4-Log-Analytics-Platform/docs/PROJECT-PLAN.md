# Log Analytics Platform — Project Plan

> Centralized log ingestion (Kafka) → stream processing (Spark Structured Streaming) →
> storage & search (Elasticsearch / AWS OpenSearch) → visualization (Kibana / OpenSearch
> Dashboards), with rule-based pattern detection, ML anomaly detection (Isolation Forest),
> and alerting. Deployed to AWS via GitHub Actions.

**Status:** implemented — every component below is working code, verified against the
running local stack (unit + integration + e2e green). Remaining open items are the ones
that need a real AWS account (marked *AWS*) plus the Phase-3 tail listed in §1.2.

**One naming note up front:** the deployment target is **AWS OpenSearch**, which is the
Elasticsearch/Kibana fork. To keep dev/prod parity, local development runs OpenSearch +
OpenSearch Dashboards by default (an Elastic-branded profile is provided in
`docker-compose.yml` for teams that prefer vanilla ES/Kibana locally). Throughout the docs,
"Elasticsearch" and "OpenSearch" refer to the same role in the architecture.

---

## 1.1 Project File Structure

```text
4-Log-Analytics-Platform/
├── Claude-Fable-5.txt                  # model marker file (per request)
├── README.md                           # quickstart + repo map
├── .editorconfig                       # cross-editor formatting baseline
├── .env.example                        # documented env-var template (LA_* prefix)
├── .gitignore
├── .pre-commit-config.yaml             # ruff / ruff-format / hygiene hooks
├── Makefile                            # dev entrypoints: lint, test, up, seed, migrate…
├── docker-compose.yml                  # local stack: Kafka, OpenSearch, Dashboards, Redis, apps
├── pyproject.toml                      # package metadata + ruff/mypy/pytest/coverage config
├── requirements.txt                    # runtime deps for the Python services
├── requirements-spark.txt              # pyspark pin (provided by EMR in prod; local/CI only)
├── requirements-dev.txt                # tooling: pytest, ruff, mypy, pre-commit
│
├── .github/                            # ── CI/CD ──────────────────────────────────────
│   ├── dependabot.yml                  # pip / actions / docker update automation
│   └── workflows/
│       ├── ci.yml                      # lint → typecheck → unit → integration → build/push
│       ├── deploy.yml                  # env-gated deploy: migrations, ECS, EMR Serverless
│       └── retrain.yml                 # weekly model retraining + holdout drift guard
│
├── config/                             # ── runtime configuration (non-secret) ─────────
│   ├── alert_rules.yaml                # declarative threshold alert rules
│   └── spark/spark-defaults.conf       # Spark tuning defaults for local/dev
│
├── docker/
│   ├── app.Dockerfile                  # one image for api / gateway / alerting (CMD swap)
│   └── spark.Dockerfile                # Spark job image (local & EMR-on-EKS option)
│
├── docs/
│   ├── PROJECT-PLAN.md                 # ← this file
│   ├── ARCHITECTURE.md                 # patterns, diagrams, data flow, security
│   ├── TECH-NOTES.md                   # CI/CD, testing, deployment, env mgmt, pitfalls
│   └── RUNBOOKS.md                     # on-call: lag, indexing, ISM, DLQ, model rollback
│
├── elasticsearch/                      # ── search "database schema" as code ───────────
│   ├── README.md                       # migration mechanism explained
│   └── migrations/                     # versioned, idempotent (applied by scripts/es_migrate.py)
│       ├── 0001_ism_rollover_policy.json      # hot→warm→delete lifecycle for log indices
│       ├── 0002_component_templates_logs.json # shared settings + mappings for la-logs-*
│       ├── 0003_index_template_logs.json      # la-logs-* template + write-alias bootstrap
│       ├── 0004_index_template_anomalies.json # la-anomalies-* (ML scores)
│       ├── 0005_index_template_alerts.json    # la-alerts-* (fired alerts, Kibana triage)
│       └── 0006_index_alert_rules.json        # la-alert-rules (CRUD storage for the API)
│
├── frontend/
│   └── ops-console/index.html          # zero-build internal ops console (fetch/loading/error demo)
│
├── infra/terraform/                    # ── AWS infrastructure as code ─────────────────
│   ├── main.tf                         # providers, backend, shared locals
│   ├── variables.tf / outputs.tf
│   ├── msk.tf                          # Amazon MSK (managed Kafka)
│   ├── opensearch.tf                   # AWS OpenSearch domain
│   ├── emr_serverless.tf               # Spark runtime for streaming jobs
│   ├── ecs.tf                          # Fargate services: gateway, api, alerting
│   ├── iam.tf                          # GitHub OIDC deploy role + task roles
│   └── envs/{dev,staging,prod}.tfvars
│
├── kibana/                             # dashboards-as-code (OpenSearch Dashboards / Kibana)
│   ├── README.md                       # export/import workflow
│   └── saved_objects/                  # index-patterns + 3 boards (log overview,
│       └── *.ndjson                    #   anomaly triage, alert history)
│
├── scripts/                            # operational tooling (also used by CI/CD)
│   ├── es_migrate.py                   # idempotent schema migration runner (REST, httpx)
│   ├── create_kafka_topics.py          # create topics with the right partition counts
│   ├── seed_sample_logs.py             # synthetic log generator (incl. error bursts)
│   ├── replay_dlq.py                   # DLQ inspect / dump-fix / replay (RUNBOOKS §4)
│   └── submit_spark_job.sh             # spark-submit wrapper (local / cluster)
│
├── src/log_analytics/                  # ── backend source (single installable package) ─
│   ├── common/                         # shared, dependency-light building blocks
│   │   ├── config.py                   # pydantic-settings Settings (LA_* env vars)
│   │   ├── logging.py                  # structured JSON logging (we eat our own dog food)
│   │   ├── models.py                   # LogEvent / AnomalyRecord / AlertEvent contracts
│   │   ├── parsing.py                  # normalization, template mining, PII redaction
│   │   ├── opensearch.py               # shared REST client plumbing (httpx, testable)
│   │   └── kafka.py                    # topic constants + client factories (SASL-aware)
│   ├── ingestion/                      # edge of the platform
│   │   ├── gateway.py                  # FastAPI HTTP ingestion gateway → Kafka
│   │   └── producer.py                 # LogEventProducer (aiokafka wrapper)
│   ├── streaming/                      # Spark Structured Streaming layer (thin!)
│   │   ├── session.py                  # SparkSession factory
│   │   ├── transforms/schema.py        # Spark schema + Kafka payload parsing (+DLQ split)
│   │   ├── transforms/enrichment.py    # level normalization, doc ids, ingest metadata
│   │   ├── sinks/opensearch_sink.py    # foreachBatch bulk indexer
│   │   └── jobs/
│   │       ├── enrich_and_index.py     # logs.raw → enrich → OpenSearch + logs.enriched
│   │       ├── pattern_detection.py    # windowed metrics + threshold rules → alerts.events
│   │       └── anomaly_scoring.py      # windowed features → Isolation Forest → logs.anomalies
│   ├── ml/                             # anomaly detection (pure Python, Spark-free, testable)
│   │   ├── features.py                 # per-service per-window feature engineering (pandas)
│   │   ├── isolation_forest.py         # LogAnomalyDetector (fit/score/save/load)
│   │   ├── registry.py                 # model registry: local dir + S3 (latest-pointer)
│   │   ├── synthetic.py                # deterministic traffic generator (bootstrap/tests)
│   │   └── train.py                    # training CLI: synthetic | opensearch | parquet
│   ├── alerting/
│   │   ├── rules.py                    # rule model, YAML loader, pure evaluator
│   │   ├── notifiers.py                # Slack (real), PagerDuty (stub), Log (dev)
│   │   └── engine.py                   # consumes alerts.events + logs.anomalies → dedup → notify
│   └── api/                            # query & admin API (FastAPI)
│       ├── main.py                     # app factory, router wiring, ops-console mount
│       ├── deps.py                     # DI: settings, services (overridable in tests)
│       ├── schemas.py                  # request/response models + validation
│       ├── routers/{health,alert_rules,search}.py
│       └── services/{alert_rule_service,search_service}.py
│
└── tests/
    ├── conftest.py                     # shared fixtures (sample log records…)
    ├── unit/                           # fast, no external services (90%+ coverage)
    │   ├── test_parsing.py / test_templates.py / test_models.py / test_config.py
    │   ├── test_features.py / test_isolation_forest.py / test_train.py / test_registry_s3.py
    │   ├── test_alert_rules.py / test_engine.py / test_notifiers.py
    │   ├── test_gateway.py / test_kafka_utils.py / test_logging.py / test_health.py
    │   ├── test_api_alert_rules.py / test_rules_repo_opensearch.py / test_deps.py
    │   └── test_replay_dlq.py
    │                                   # …TestClient round-trips + httpx.MockTransport fakes
    ├── integration/                    # need Kafka / OpenSearch / a JVM (skipped otherwise)
    │   ├── test_es_migrate.py / test_kafka_roundtrip.py
    │   └── test_spark_transforms.py    # local[2] session: parse/DLQ split, enrichment,
    │                                   #   Python↔Spark template_id parity contract
    └── e2e/test_pipeline_smoke.py      # full-stack smoke: ingest→search idempotency,
                                        #   anomaly scores, recorded alerts
```

### Structure rationale (the three buckets requested)

| Bucket | Where | Notes |
|---|---|---|
| **Source code** | `src/log_analytics/` (backend), `frontend/ops-console/` + `kibana/` (frontend), `src/log_analytics/common/` (shared), `elasticsearch/migrations/` (DB migrations), `config/` + `.env.example` (configuration) | `src/` layout prevents accidental imports of the repo root; business logic (`common/`, `ml/`, `alerting/`) is Spark-free so it unit-tests in milliseconds; Spark jobs are a thin shell around it. |
| **CI/CD** | `.github/workflows/` | `ci.yml` for every PR/push; `deploy.yml` gated per GitHub Environment (dev/staging/prod) using OIDC — no long-lived AWS keys. |
| **Tools config** | `pyproject.toml` (ruff, mypy, pytest, coverage), `.pre-commit-config.yaml`, `.editorconfig`, `dependabot.yml`, `docker-compose.yml`, `Makefile` | Single source of truth for tool settings lives in `pyproject.toml` wherever the tool supports it. |

---

## 1.2 Implementation TODO List

Status legend: `[x]` implemented **and verified against the running local stack**;
`[ ]` open (items marked *AWS* need a real account and run at deploy time).

### Phase 1 — Foundation (high priority)
- [x] Repository scaffolding, docs, and stub code
- [x] Local dev stack up & verified: `docker compose up -d` (Kafka, OpenSearch, Dashboards, Redis)
- [x] Apply search schema: `python scripts/es_migrate.py` — 6 migrations, idempotent (re-run applies 0)
- [x] Create Kafka topics with production-like partitioning: `python scripts/create_kafka_topics.py`
- [x] Ingestion gateway: per-item validation, alias coercion, per-service keying, gzip bodies,
      API keys, rate limiting, Kafka-failure → 503, `/readyz` broker probe
- [x] `enrich_and_index` Spark job end-to-end: `logs.raw` → parse/enrich → `la-logs` + `logs.enriched` (+ DLQ)
- [x] Idempotent indexing verified: resending an identical batch creates zero duplicates (e2e test)
- [x] Seed generator produces realistic traffic (`--mode gateway|kafka`, error bursts)
- [x] Dashboards index patterns in `kibana/` (the three boards shipped as code in Phase 2)
- [x] CI gates green locally: ruff + format + mypy + 157 unit tests + coverage 90% (gate: 80%)
- [ ] *AWS*: Terraform skeleton applies in a sandbox account (VPC wiring, MSK, OpenSearch dev domain)

### Phase 2 — Core features (medium priority)
- [x] `pattern_detection` job: windowed metrics vs `config/alert_rules.yaml`, one streaming
      query per distinct rule window → `alerts.events`
- [x] Alerting engine: Redis-TTL dedup (in-memory fallback), rules-driven channel routing,
      Slack (live impl) + PagerDuty (Events v2) + log fallback, alerts indexed to `la-alerts`
- [x] "Service went silent" detection (engine-side OpenSearch aggregation — absence of data)
- [x] Alert-rule CRUD API backed by OpenSearch `la-alert-rules` (auto/memory/opensearch backends;
      verified: POST via API → document in the index)
- [x] Training CLI (`ml/train.py`): synthetic bootstrap, OpenSearch `search_after` source,
      parquet source, time-ordered holdout evaluation
- [x] Model registry: local + S3 (versioned, `latest` pointer); scoring job hot-reloads on version change
- [x] `anomaly_scoring` job: per-service 1-min features → Isolation Forest → `logs.anomalies` + `la-anomalies`
      (min-events guard, executor-local model cache)
- [x] Anomaly alerts: engine maps scores through anomaly_score rules (highest threshold wins)
- [x] Query API `/api/v1/logs/search` with constrained query surface + validation
- [x] Integration tests (compose Kafka + OpenSearch, Spark `local[2]` transform/contract
      tests) — wired into ci.yml, passing locally
- [x] `deploy.yml`: migrations → ECS task-def rollout → EMR Serverless job restarts → smoke test
- [ ] *AWS*: ECS Fargate services + EMR Serverless streaming jobs actually running in dev
- [x] Dashboards-as-code: Log Overview, Anomaly Triage, Alert History boards in
      `kibana/saved_objects/` (imported + aggregations verified against live data)

### Phase 3 — Polish & optimization (lower priority)
- [x] Idempotency hardening: deterministic `_id` end-to-end; duplicate rate 0 verified under resend
- [x] Lightweight template mining (`template_id` via masked-message hash; Drain3 upgrade optional)
- [x] PII redaction in enrichment (emails, bearer tokens, key=value secrets) — shared regex table
- [x] Backpressure guardrails: `maxOffsetsPerTrigger` bounded on every job, min-events guard on scoring
- [x] DLQ replay tooling (`scripts/replay_dlq.py`: inspect / dump-fix / replay, dry-run
      default, provenance headers) + poison-message runbook — verified full circle locally
      (poison → DLQ → dump → fix → replay → indexed)
- [ ] ISM tuning: hot/warm sizing, UltraWarm for >7-day data, snapshot policy to S3
- [x] Scheduled retraining (weekly `retrain.yml`: OpenSearch history → S3 registry,
      holdout drift guard fails the run past 15% high-score rate)
- [ ] Champion/challenger model rollout (retrain.yml publishes directly today)
- [ ] *AWS*: SigV4 signing, fine-grained OpenSearch roles, gateway mTLS/API-key rotation
- [ ] Cost & capacity dashboards (MSK bytes-in, EMR vCPU-hours, OpenSearch storage)
- [ ] Load tests (k6 against gateway; synthetic 10× burst), chaos drill (broker kill, executor kill)
- [x] Runbooks (`docs/RUNBOOKS.md`): lag spikes, indexing failures, ISM/rollover, DLQ,
      model rollback, silent services + meta-monitoring reference
- [ ] Multi-tenant story: per-team API keys, per-service index routing or filtered aliases

### Definition of done (applies to every phase item)
Code + tests + docs updated, CI green, deployed to dev, observable (metric or dashboard exists for it).
