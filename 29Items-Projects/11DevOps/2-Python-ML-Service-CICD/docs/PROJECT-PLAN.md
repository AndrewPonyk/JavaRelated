# Project Plan — Python ML Service CI/CD (Fraud Detection API)

A production-grade Fraud Detection API serving an ML model, with A/B testing between
champion/challenger model versions, PSI-based drift detection triggering automated retraining
via Azure ML, blue-green deployments to AKS, and full observability via Prometheus/Grafana.

**Stack:** Python 3.12 · FastAPI · Poetry · pytest · MLflow · PostgreSQL (SQLAlchemy 2 + Alembic)
· Docker · Kubernetes (AKS, blue-green) · Azure ML · Azure DevOps pipelines · Terraform (azurerm)
· Prometheus + Grafana.

---

## 1.1 Project File Structure

```text
2-Python-ML-Service-CICD/
├── src/
│   └── fraud_detection/                    # Python package (src layout), importable as fraud_detection
│       ├── __init__.py                     # Package marker + version string
│       ├── main.py                         # create_app() factory; module-level `app` for uvicorn/gunicorn
│       ├── core/                           # Cross-cutting application concerns
│       │   ├── __init__.py
│       │   ├── config.py                   # pydantic-settings Settings (env prefix FRAUD_), cached get_settings()
│       │   ├── errors.py                   # AppError/NotFoundError/ConflictError -> RFC 7807 problem responses
│       │   └── logging.py                  # structlog JSON logging configuration, request-id binding
│       ├── api/                            # HTTP layer (FastAPI routers, dependencies)
│       │   ├── __init__.py
│       │   ├── deps.py                     # DI providers: DB session, services, settings
│       │   └── routes/
│       │       ├── __init__.py             # Router aggregation under settings.api_prefix (/api/v1)
│       │       ├── health.py               # GET /health/live, GET /health/ready with component detail (no prefix)
│       │       ├── predictions.py          # POST/GET /api/v1/predictions, GET /api/v1/predictions/{txn_id}
│       │       ├── models.py               # Model catalog CRUD, GET/PUT ab-config, POST promote, DELETE version
│       │       └── admin.py                # POST retrain + job polling, GET drift + drift report history
│       ├── schemas/                        # Pydantic request/response models
│       │   ├── __init__.py
│       │   ├── common.py                   # ProblemDetail (RFC 7807 error document)
│       │   ├── prediction.py               # PredictionRequest/Response/Record + paginated list schemas
│       │   └── model.py                    # Model catalog, A/B config, promote, drift, retraining schemas
│       ├── services/                       # Business logic (framework-free)
│       │   ├── __init__.py
│       │   ├── ab_config.py                # Thread-safe runtime A/B config store (PUT ab-config backing)
│       │   ├── ab_router.py                # Sticky DB-backed assignments + deterministic hash split fallback
│       │   ├── model_loader.py             # Two-tier resolution: MLflow -> local store -> heuristic fallback
│       │   ├── prediction_service.py       # Orchestrates: route -> load -> score -> persist -> metrics
│       │   ├── drift_detector.py           # PSI vs training baseline over recent traffic; auto-retrain trigger
│       │   └── retraining.py               # Async retraining: Azure ML when configured, local thread otherwise
│       ├── db/                             # Persistence layer
│       │   ├── __init__.py
│       │   ├── session.py                  # Lazy SQLAlchemy 2 engine/sessionmaker (no connection at import time)
│       │   ├── models.py                   # ORM: predictions, model_versions, ab_assignments, drift_reports
│       │   └── repositories.py             # Typed repository classes per table
│       ├── ml/                             # Model lifecycle code (training side)
│       │   ├── __init__.py
│       │   ├── features.py                 # Feature engineering shared by train + serve paths (skew-proof)
│       │   ├── train.py                    # Full training pipeline: data -> fit -> gates -> versioned artifacts
│       │   ├── evaluate.py                 # Evaluation metrics + promotion quality gates (AUC/recall)
│       │   └── registry.py                 # Two-tier registry: LocalModelStore (versioned) + MLflow wrapper
│       └── monitoring/                     # Observability instrumentation
│           ├── __init__.py
│           ├── metrics.py                  # Canonical prometheus_client metrics (fraud_predictions_total, ...)
│           └── middleware.py               # HTTP metrics middleware (http_requests_total, duration histogram)
├── tests/
│   ├── conftest.py                         # Per-test SQLite DB + per-test copy of a real trained model store
│   ├── unit/
│   │   ├── test_ab_config.py               # Runtime A/B config store seeding/validation
│   │   ├── test_ab_router.py               # Sticky assignments, hash determinism, split properties, DB-outage path
│   │   ├── test_drift_detector.py          # PSI math + DB-backed evaluation, auto-retrain trigger + debounce
│   │   ├── test_evaluate.py                # Hand-computed metrics, single-class guard, gate reasons
│   │   ├── test_model_loader.py            # Local-store resolution, fallback + counter, cache invalidation
│   │   ├── test_prediction_service.py      # Persistence incl. features, duplicate txn, DB-outage resilience
│   │   ├── test_repositories.py            # CRUD roundtrips for all four tables
│   │   ├── test_retraining.py              # Local retraining jobs, debounce, challenger registration
│   │   └── test_schemas.py                 # Pydantic validation edge cases
│   ├── integration/
│   │   ├── test_api_admin.py               # Retrain job lifecycle + drift over live traffic
│   │   ├── test_api_health.py              # Probes, /metrics content, request-id roundtrip
│   │   ├── test_api_models.py              # Catalog, ab-config, promote flow, delete guards
│   │   └── test_api_predictions.py         # Scoring + persistence + pagination + problem+json errors
│   └── model/
│       ├── test_model_quality.py           # Stored metrics gates + fresh-holdout generalization
│       ├── test_model_invariants.py        # Determinism/bounds fuzzing, fallback monotonicity
│       └── test_training_pipeline.py       # Artifacts, baseline schema, versioning, gate rejection
├── pipelines/                              # Azure DevOps reusable templates + per-env variables
│   ├── templates/
│   │   ├── lint-test.yml                   # ruff + pytest (unit/integration) + coverage stage template
│   │   ├── model-validation.yml            # tests/model gate against candidate model
│   │   ├── build-image.yml                 # Docker build + push to frauddetectacr.azurecr.io
│   │   ├── deploy-aks.yml                  # kustomize apply + blue-green switch + smoke test
│   │   └── terraform.yml                   # terraform init/plan/apply template (azurerm backend)
│   ├── variables/
│   │   ├── dev.yml                         # dev variable group (rg-fraud-detection-dev, fraud-aks-dev)
│   │   ├── staging.yml                     # staging variable group
│   │   └── prod.yml                        # prod variable group (approvals, prod gates)
│   └── ml-retraining.yml                   # Standalone pipeline: drift-triggered Azure ML retrain + register
├── mlops/
│   └── azureml/
│       ├── train-job.yml                   # Azure ML command job spec for training
│       ├── drift-monitor-job.yml           # Scheduled Azure ML job computing PSI on recent traffic
│       └── environment.yml                 # Azure ML environment (conda/docker) definition
├── k8s/
│   ├── base/                               # Kustomize base (namespace fraud-detection)
│   │   ├── kustomization.yaml              # Base resource list
│   │   ├── namespace.yaml                  # Namespace fraud-detection
│   │   ├── deployment-blue.yaml            # Deployment fraud-api-blue (labels app: fraud-api, color: blue)
│   │   ├── deployment-green.yaml           # Deployment fraud-api-green (labels app: fraud-api, color: green)
│   │   ├── service.yaml                    # Service fraud-api; selector app + active color = blue-green switch
│   │   ├── configmap.yaml                  # ConfigMap fraud-api-config (non-secret FRAUD_* vars)
│   │   ├── hpa.yaml                        # HorizontalPodAutoscaler on CPU/latency
│   │   ├── ingress.yaml                    # Ingress for external API traffic
│   │   ├── servicemonitor.yaml             # Prometheus Operator scrape config for /metrics
│   │   └── pdb.yaml                        # PodDisruptionBudget for safe node drains
│   └── overlays/
│       ├── dev/
│       │   ├── kustomization.yaml          # dev overlay (image tag, env-specific config)
│       │   └── patch-replicas.yaml         # dev replica count (1)
│       ├── staging/
│       │   ├── kustomization.yaml          # staging overlay
│       │   └── patch-replicas.yaml         # staging replica count (2)
│       └── prod/
│           ├── kustomization.yaml          # prod overlay
│           └── patch-replicas.yaml         # prod replica count (3+)
├── infra/
│   └── terraform/                          # azurerm IaC, one root module + env tfvars
│       ├── providers.tf                    # azurerm provider + required versions
│       ├── backend.tf                      # Remote state (Azure Storage backend)
│       ├── main.tf                         # Composes modules per env (rg-fraud-detection-<env>)
│       ├── variables.tf                    # Root input variables (env, location, sizing)
│       ├── outputs.tf                      # AKS kubeconfig hints, ACR login server, DB FQDN
│       ├── modules/
│       │   ├── aks/                        # AKS cluster fraud-aks-<env> (main/variables/outputs.tf)
│       │   ├── acr/                        # Container registry frauddetectacr (+ AKS pull role)
│       │   ├── postgres/                   # Azure Database for PostgreSQL Flexible Server
│       │   ├── azureml/                    # Azure ML workspace + compute for retraining jobs
│       │   └── keyvault/                   # Key Vault for secrets (DB creds, MLflow tokens)
│       └── environments/
│           ├── dev.tfvars                  # dev sizing/naming values
│           ├── staging.tfvars              # staging sizing/naming values
│           └── prod.tfvars                 # prod sizing/naming values
├── monitoring/
│   ├── prometheus/
│   │   ├── prometheus.yml                  # Local scrape config (api:8000/metrics) for docker-compose
│   │   └── alerts.yml                      # Alert rules: error rate, latency p99, drift PSI, fallbacks
│   └── grafana/
│       ├── provisioning/
│       │   ├── datasources/datasource.yml  # Prometheus datasource auto-provisioning
│       │   └── dashboards/dashboards.yml   # Dashboard file provider config
│       └── dashboards/
│           ├── fraud-api-overview.json     # RED metrics: traffic, errors, latency, per-variant volume
│           └── model-monitoring.json       # Online-learning view: PSI per feature, A/B outcomes, fallbacks
├── dashboard/                              # React 18 + Vite + TS online-learning monitoring UI
│   ├── package.json                        # Node deps + dev/build scripts
│   ├── tsconfig.json                       # Strict TypeScript compiler config
│   ├── vite.config.ts                      # Vite + dev proxy (/api, /health -> :8000)
│   ├── index.html                          # SPA entry document
│   └── src/
│       ├── main.tsx                        # React root
│       ├── App.tsx                         # Page shell
│       ├── styles.css                      # Responsive, dark-mode-aware styles
│       ├── api/
│       │   ├── client.ts                   # Typed fetch wrapper + problem+json ApiError
│       │   └── types.ts                    # Mirrors of the API schemas
│       └── components/
│           ├── ModelMonitoringDashboard.tsx # Composes panels; 30s polling, pause-on-hidden
│           ├── ModelsTable.tsx             # Catalog + promote action
│           ├── ABConfigPanel.tsx           # Runtime traffic-split control with validation
│           ├── DriftPanel.tsx              # PSI bars vs threshold + report history
│           └── PredictionForm.tsx          # Live scoring form with field validation
├── migrations/                             # Alembic (configured for src layout)
│   ├── alembic.ini                         # Alembic config; URL sourced from Settings at runtime
│   ├── env.py                              # Wires SQLAlchemy metadata; reads FRAUD_DATABASE_URL
│   ├── script.py.mako                      # Migration file template
│   └── versions/
│       ├── 0001_initial_schema.py          # predictions, model_versions, ab_assignments, drift_reports
│       ├── 0002_add_prediction_features.py # features JSON column (drift monitoring input)
│       └── 0003_performance_indexes.py     # read-path indexes (created_at ordering, version lookup)
├── scripts/
│   ├── entrypoint.sh                       # Container boot: migrations + optional model bootstrap + uvicorn
│   ├── blue_green_switch.sh                # Patch Service fraud-api selector color; verify + rollback
│   ├── smoke_test.py                       # Post-deploy checks: health, sample prediction
│   ├── seed_db.py                          # Seed local DB with realistic predictions incl. feature vectors
│   └── export_openapi.py                   # Regenerate docs/api/openapi.json from the app
├── docs/
│   ├── PROJECT-PLAN.md                     # This document: structure + phased TODO plan
│   ├── ARCHITECTURE.md                     # System diagram, A/B + blue-green + drift design decisions
│   ├── TECH-NOTES.md                       # Operational notes, gotchas, local-dev tips
│   └── api/openapi.json                    # Exported OpenAPI 3 specification (make openapi)
├── pyproject.toml                          # Poetry project: deps, ruff, pytest, package config
├── README.md                               # Quickstart, local run, deploy overview
├── .env.example                            # All FRAUD_* variables with local defaults
├── .gitignore                              # Python/Terraform/Node/IDE ignores
├── .dockerignore                           # Keep image context minimal
├── .pre-commit-config.yaml                 # ruff (lint+format), whitespace, yaml checks
├── Makefile                                # dev targets: install, lint, test, run, docker, migrate
├── Dockerfile                              # Multi-stage Poetry build -> slim runtime, port 8000, non-root
├── docker-compose.yml                      # api + postgres + mlflow + prometheus + grafana local stack
├── azure-pipelines.yml                     # Main CI/CD: lint-test -> model-validation -> build -> deploy dev/staging/prod
└── Claude-Fable-5.txt                      # Generation provenance note
```

### Source Code (`src/fraud_detection/`)

- **`main.py` / `core/`** — `create_app()` assembles routers, middleware, logging, and metrics.
  `core/config.py` is the single source of truth for configuration (`Settings`, env prefix
  `FRAUD_`, cached `get_settings()`); everything else consumes it via DI, never `os.environ`.
- **`api/`** — thin HTTP layer. `deps.py` provides sessions/services so routes stay declarative.
  Health endpoints live outside `api_prefix` so probes are stable across API versions.
- **`services/`** — the core domain logic. `ab_router.py` (deterministic hash of `account_id`
  against `ab_traffic_split`), `drift_detector.py` (real PSI math), and `model_loader.py`
  (lazy MLflow import with a working local fallback model so the app runs without MLflow).
- **`db/`** — SQLAlchemy 2 typed ORM models matching the canonical schema exactly; engine is
  created lazily so importing the app never opens a connection.
- **`ml/`** — training-side code kept in the same package so feature engineering
  (`features.py`) is shared between training and serving, preventing skew.
- **`monitoring/`** — canonical metric definitions in one module (`metrics.py`) so names/labels
  never drift between code, alerts, and dashboards.

### Tests (`tests/`)

- **`unit/`** — pure-logic tests (A/B determinism, PSI math, service orchestration with mocks);
  fast, no I/O.
- **`integration/`** — FastAPI TestClient tests against the real app wiring with stubbed
  model/DB; verify routes, status codes, response schemas.
- **`model/`** — the model-quality gate run by the `model-validation.yml` pipeline stage:
  metric thresholds (`test_model_quality.py`) and behavioral invariants
  (`test_model_invariants.py`). Failing this blocks image build/promotion.

### CI/CD (`azure-pipelines.yml`, `pipelines/`, `mlops/`)

- **`azure-pipelines.yml`** — the main multi-stage pipeline: lint+test → model validation →
  image build/push to `frauddetectacr.azurecr.io` → terraform → deploy to dev → staging →
  prod (with approvals), each deploy ending in `scripts/smoke_test.py` and a blue-green switch.
- **`pipelines/templates/`** — reusable stage templates; **`pipelines/variables/`** — per-env
  values so envs differ only by data, not pipeline logic.
- **`pipelines/ml-retraining.yml`** + **`mlops/azureml/`** — the automated retraining path:
  drift monitor job → retrain job → evaluate → register as `challenger` alias in MLflow.

### Infrastructure (`infra/terraform/`, `k8s/`, `scripts/`)

- **Terraform** — root module composes five modules (aks, acr, postgres, azureml, keyvault)
  per environment via `environments/*.tfvars`; naming follows `rg-fraud-detection-<env>`,
  `fraud-aks-<env>`, shared ACR `frauddetectacr`.
- **Kubernetes** — kustomize base with both color Deployments (`fraud-api-blue`,
  `fraud-api-green`) always defined; the `fraud-api` Service selector's `color` label is the
  only thing that changes during a cutover (`scripts/blue_green_switch.sh`). Overlays patch
  replicas/config per environment.

### Monitoring (`monitoring/`, `dashboard/`)

- **Prometheus** — local scrape config for docker-compose plus alert rules keyed to the
  canonical metric names (`fraud_drift_psi`, `fraud_model_fallback_total`, latency histograms).
- **Grafana** — provisioned datasource + two dashboards: API RED overview and the
  model-monitoring (online-learning) dashboard comparing champion vs challenger.
- **`dashboard/`** — optional React/TS component for a richer model-monitoring UI backed by
  the API's admin endpoints.

### Docs (`docs/`, `README.md`)

- **`PROJECT-PLAN.md`** (this file) — structure and phased execution plan.
- **`ARCHITECTURE.md`** — component diagram and the A/B, blue-green, and drift/retraining
  design decisions. **`TECH-NOTES.md`** — operational gotchas and local-dev workflow.

---

## 1.2 Implementation TODO List

> **Status:** every code deliverable below is implemented and verified in this
> repository (checked items). The three unchecked items are operational rollout
> work that requires a live Azure subscription (load-test baselining, final
> security review against real infrastructure, and the production cutover) —
> the code and pipeline hooks for them are already in place.

### Phase 1 — Foundation (high priority)

Goal: a runnable, tested, containerized skeleton with schema, health checks, CI lint/test, and dev infra.

- [x] **Repo + Poetry setup** — author `pyproject.toml` (Poetry, src layout packaging, deps:
  fastapi, pydantic-settings, sqlalchemy, alembic, prometheus-client, structlog; dev: pytest,
  httpx, ruff), plus `.gitignore`, `.dockerignore`, `.pre-commit-config.yaml`, `Makefile`
  targets (`install`, `lint`, `test`, `run`, `migrate`), and `README.md` quickstart.
- [x] **Settings & logging** — implement `src/fraud_detection/core/config.py` (`Settings`,
  `FRAUD_` prefix, exact canonical fields/defaults, cached `get_settings()`) and
  `core/logging.py` (structlog JSON config); commit `.env.example` listing every `FRAUD_*` var.
- [x] **App factory + health endpoints** — `src/fraud_detection/main.py` `create_app()` and
  `api/routes/health.py` (`/health/live`, `/health/ready`); readiness checks DB reachability
  lazily and never fails at import time.
- [x] **DB schema + migrations** — SQLAlchemy 2 models in `src/fraud_detection/db/models.py`
  (predictions, model_versions, ab_assignments, drift_reports — canonical columns exactly),
  lazy engine in `db/session.py`, repositories in `db/repositories.py`; Alembic scaffold in
  `migrations/` with `versions/0001_initial_schema.py` matching the ORM 1:1; `scripts/seed_db.py`.
- [x] **Baseline tests** — `tests/conftest.py` (app + TestClient + in-memory DB fixtures),
  `tests/integration/test_api_health.py`, `tests/unit/test_schemas.py`; suite passes with no
  external services and without mlflow/psycopg2 installed.
- [x] **Dockerfile + local stack** — multi-stage `Dockerfile` (Poetry export → slim 3.12
  runtime, non-root, uvicorn on port 8000) and `docker-compose.yml` (api, postgres, mlflow,
  prometheus, grafana) wired to the `FRAUD_*` env vars.
- [x] **CI lint + test stages** — `azure-pipelines.yml` skeleton consuming
  `pipelines/templates/lint-test.yml` (ruff check/format + pytest with coverage) and
  `pipelines/variables/dev.yml`; pipeline green on every push.
- [x] **Dev infra via Terraform** — `infra/terraform/` root (providers/backend/main/variables/
  outputs) + modules `aks`, `acr`, `postgres`, `azureml`, `keyvault`; apply
  `environments/dev.tfvars` through `pipelines/templates/terraform.yml` to create
  `rg-fraud-detection-dev`, `fraud-aks-dev`, `frauddetectacr`.

**Definition of done (Phase 1):** `poetry install; make test` green locally with zero optional
deps; `docker compose up` serves `/health/live` on :8000; `alembic upgrade head` creates all
four tables; Azure DevOps runs lint+test on PRs; `terraform apply` for dev succeeds and outputs
AKS/ACR/DB endpoints.

### Phase 2 — Core (medium priority)

Goal: real scoring path with A/B routing, metrics, dashboards, and blue-green delivery to dev + staging.

- [x] **Prediction endpoint + MLflow model loading** — `schemas/prediction.py`,
  `services/model_loader.py` (lazy `import mlflow` inside functions, alias-based load of
  `fraud-detection@champion|challenger`, deterministic fallback stub + increment
  `fraud_model_fallback_total` when MLflow unavailable), `services/prediction_service.py`
  (score → persist to `predictions` → record latency), `api/routes/predictions.py`
  (`POST /api/v1/predictions`); tests in `tests/unit/test_prediction_service.py` and
  `tests/integration/test_api_predictions.py`.
- [x] **A/B router** — implement deterministic hashing in `services/ab_router.py`
  (stable `account_id` hash mod 100 vs `ab_traffic_split`, honoring `ab_test_enabled`),
  persist assignments to `ab_assignments`; expose `GET /api/v1/models/ab-config` and model
  listing/promotion in `api/routes/models.py` + `ml/registry.py`; property tests in
  `tests/unit/test_ab_router.py` (determinism + split within tolerance).
- [x] **Prometheus metrics + dashboards** — `monitoring/metrics.py` defining the canonical
  metrics exactly (`fraud_predictions_total{variant,model_version,outcome}`,
  `fraud_prediction_latency_seconds`, `fraud_drift_psi{feature}`,
  `fraud_model_fallback_total`, `http_requests_total`, `http_request_duration_seconds`),
  `monitoring/middleware.py`, `GET /metrics` in `main.py`; `monitoring/prometheus/prometheus.yml`,
  Grafana provisioning + `monitoring/grafana/dashboards/fraud-api-overview.json`.
- [x] **Blue-green AKS deploy** — author `k8s/base/*` (namespace, both color deployments,
  `fraud-api` Service with color selector, `fraud-api-config` ConfigMap referencing Secret
  `fraud-api-secrets`, hpa, ingress, servicemonitor, pdb) + `k8s/overlays/dev` and `staging`;
  implement `scripts/blue_green_switch.sh` and `scripts/smoke_test.py`; wire
  `pipelines/templates/build-image.yml` (push
  `frauddetectacr.azurecr.io/fraud-detection-api:<tag>`) and
  `pipelines/templates/deploy-aks.yml` (kustomize apply inactive color → smoke → switch).
- [x] **Model validation stage** — `ml/train.py` + `ml/evaluate.py` (real metric-threshold
  gates) + `ml/features.py`; `tests/model/test_model_quality.py` and
  `test_model_invariants.py`; `pipelines/templates/model-validation.yml` stage between
  lint-test and build-image in `azure-pipelines.yml`.
- [x] **Staging environment** — `infra/terraform/environments/staging.tfvars` +
  `pipelines/variables/staging.yml`; extend `azure-pipelines.yml` with a staging stage
  (auto-deploy after dev smoke passes, pre-prod approval gate defined).

**Definition of done (Phase 2):** a scored prediction round-trips through A/B routing and is
persisted with variant + model_version; `/metrics` exposes all canonical series and Grafana
overview renders them; pipeline builds/pushes the image and performs a zero-downtime blue-green
cutover on dev and staging with automatic rollback if `smoke_test.py` fails; a model failing
`tests/model` blocks the build stage.

### Phase 3 — Polish (lower priority)

Goal: automated drift-driven retraining, richer monitoring UI, operational hardening, prod cutover.

- [x] **Drift detection + retraining automation** — finish `services/drift_detector.py`
  (PSI vs training baseline, write `drift_reports`, set `fraud_drift_psi` gauge) and
  `services/retraining.py` (guarded azure-* imports, trigger Azure ML job); expose
  `GET /api/v1/admin/drift` and `POST /api/v1/admin/retrain` in `api/routes/admin.py`;
  author `mlops/azureml/train-job.yml`, `drift-monitor-job.yml`, `environment.yml` and
  `pipelines/ml-retraining.yml` (drift > `drift_psi_threshold` → retrain → evaluate →
  register as `challenger`); tests in `tests/unit/test_drift_detector.py`.
- [x] **Online-learning dashboard UI** — `monitoring/grafana/dashboards/model-monitoring.json`
  (PSI per feature, champion vs challenger volume/outcomes, fallback rate) and the React
  component `dashboard/src/components/ModelMonitoringDashboard.tsx` with
  `dashboard/package.json` + `tsconfig.json`.
- [x] **Alerting + runbooks** — `monitoring/prometheus/alerts.yml` (5xx rate, p99 latency,
  sustained PSI breach, fallback spike, pod restarts) and matching runbook sections in
  `docs/TECH-NOTES.md` (alert → diagnosis → action, incl. manual blue-green rollback).
- [ ] **Load testing** — add a load-test target to `Makefile` and a pipeline-invocable profile
  against staging (baseline RPS/latency documented in `docs/TECH-NOTES.md`); size
  `k8s/base/hpa.yaml` limits from results.
- [ ] **Security hardening** — non-root/read-only-fs securityContext in
  `k8s/base/deployment-*.yaml`, image scan step in `pipelines/templates/build-image.yml`,
  Key Vault-sourced secrets via `infra/terraform/modules/keyvault` (Secret `fraud-api-secrets`
  never committed), dependency audit in `pipelines/templates/lint-test.yml`, NetworkPolicy
  review noted in `docs/ARCHITECTURE.md`.
- [ ] **Prod cutover** — apply `infra/terraform/environments/prod.tfvars`, add
  `pipelines/variables/prod.yml` + manual-approval prod stage in `azure-pipelines.yml`,
  `k8s/overlays/prod` replica/resource patches; execute first blue-green prod release and
  record the procedure + rollback drill in `docs/TECH-NOTES.md`.

**Definition of done (Phase 3):** simulated drift produces a `drift_reports` row, fires the
alert, and kicks off the Azure ML retraining pipeline ending with a new `challenger` alias;
model-monitoring dashboard live in Grafana; every alert has a runbook; load-test baseline
documented and HPA tuned; prod serves traffic via approved pipeline with a rehearsed rollback.

---

## Milestones & sequencing

| # | Milestone | Phase | Depends on | Key deliverables | Exit signal |
|---|-----------|-------|------------|------------------|-------------|
| M1 | Skeleton runs & tests green | 1 | — | pyproject, Settings, health routes, conftest | `make test` green; `/health/live` 200 |
| M2 | Data layer ready | 1 | M1 | db/models, repositories, Alembic 0001, seed | `alembic upgrade head` on local postgres |
| M3 | Containerized + CI | 1 | M1 | Dockerfile, docker-compose, lint-test pipeline | CI green on PR; compose stack up |
| M4 | Dev infra provisioned | 1 | M3 | Terraform root + 5 modules, dev.tfvars | `terraform apply` dev succeeds |
| M5 | Scoring + A/B live | 2 | M2, M3 | predictions route, model_loader, ab_router | Prediction persisted with variant |
| M6 | Observability | 2 | M5 | metrics.py, middleware, Prometheus, overview dashboard | Canonical series visible in Grafana |
| M7 | Blue-green delivery | 2 | M4, M5 | k8s base+overlays, switch script, deploy-aks stage | Zero-downtime cutover on dev+staging |
| M8 | Model quality gate | 2 | M5 | train/evaluate, tests/model, model-validation stage | Bad model blocks pipeline |
| M9 | Drift → retrain loop | 3 | M6, M8 | drift_detector, retraining, azureml jobs, ml-retraining.yml | Simulated drift triggers retrain |
| M10 | Ops hardening + prod | 3 | M7, M9 | alerts, runbooks, load test, security, prod stage | Approved prod release + rollback drill |

**Sequencing notes:** M1–M4 can proceed largely in parallel after M1; M5 is the critical path
for everything in Phase 2/3; M7 (delivery) and M8 (quality gate) are independent of each other
and can be parallelized; M9 requires both metrics (M6) and the validation gate (M8) so that
retrained challengers are automatically vetted before entering the A/B split.
