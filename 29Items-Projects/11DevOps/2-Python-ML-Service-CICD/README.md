# Python ML Service CI/CD — Fraud Detection API

A production-style FastAPI service that serves a fraud-detection ML model with:

- **Fraud scoring** — `POST /api/v1/predictions`, persisted with the served feature
  vector for auditing and drift monitoring
- **A/B testing** between `champion` and `challenger` model versions — sticky,
  DB-backed assignments with a deterministic hash fallback, tunable at runtime
- **Drift detection (PSI)** over recent production traffic against the training
  baseline, with automated (debounced) retraining when drift is detected
- **Two-tier model registry** — MLflow when available, an always-on versioned
  local model store otherwise, and a monitored heuristic fallback as last resort
- **Online-learning monitoring** — Prometheus metrics, Grafana dashboards, and a
  React monitoring UI (`dashboard/`)
- **Blue-green deployments** to AKS driven by Azure DevOps pipelines

**Architecture in one line:** Azure DevOps lints/tests/validates the model, builds
the image, pushes it to ACR (`frauddetectacr.azurecr.io/fraud-detection-api`), and
rolls it out to AKS as `fraud-api-blue` / `fraud-api-green` Deployments behind a
color-switching `fraud-api` Service, while the model registry tracks versions and
Prometheus/Grafana watch predictions and drift. See [`docs/`](docs/) for details.

## Feature catalog

**Scoring & audit (API)**

1. Score a transaction for fraud — returns probability, fraud/legit verdict, model
   version, latency (`POST /api/v1/predictions`)
2. Save every scored prediction automatically as an audit record, including the
   exact feature vector used
3. List saved predictions with pagination (`limit`/`offset`)
4. Filter saved predictions by account id
5. Fetch one saved prediction by transaction id
6. Reject invalid input (negative/zero amount, empty ids, bad timestamp) with a
   structured 422 error

**A/B testing**

7. Assign each account to `champion` or `challenger` variant and save the
   assignment (sticky — an account keeps its variant)
8. Route a configured percentage of traffic to the challenger model
9. View the current A/B configuration (`GET /models/ab-config`)
10. Change the traffic split at runtime without redeploy (`PUT /models/ab-config`)
11. Enable/disable the A/B experiment at runtime

**Model management**

12. Train a new model from synthetic demo data or a CSV file (`make train`)
13. Enforce quality gates on every trained model — reject it if AUC < 0.85 or
    recall < 0.70
14. Save each trained model as a new immutable version (artifacts + metrics + PSI
    baseline) in the local store, plus MLflow when available
15. List all registered model versions with their metrics and stage
16. View one model version
17. Promote a version to champion (or challenger) — previous champion is archived,
    traffic switches instantly
18. Delete an archived model version (deleting the current champion is blocked)
19. Serve a deterministic fallback model when no trained model exists, instead of
    failing

**Drift & retraining**

20. Calculate PSI drift per feature over recent live traffic vs. the training
    baseline (`GET /admin/drift`)
21. Save a drift report for every evaluation; list the report history
22. Auto-trigger retraining when drift exceeds the threshold (debounced to at most
    once per interval)
23. Trigger retraining manually (`POST /admin/retrain`) — runs as a background job
24. Check a retraining job's status and result (`GET /admin/retrain/{job_id}`)
25. Register a successful retrain as the new challenger automatically

**Monitoring & ops**

26. Report liveness and readiness, including database and model-tier status
    (`/health/live`, `/health/ready`)
27. Expose Prometheus metrics: predictions by variant/version/outcome, latency
    histograms, PSI per feature, fallback count, DB errors, retraining runs
    (`/metrics`)
28. Correlate every request via `X-Request-ID` header + JSON structured logs
29. Compress large responses (gzip)
30. Fire alerts on error rate, p99 latency, drift, and fallback usage
    (Prometheus rules + Grafana dashboards)

**Web dashboard (UI)**

31. View the model catalog with alias badges and metrics
32. Promote a model to champion from the UI (with confirmation)
33. Adjust the A/B traffic split from the UI (validated 0–100)
34. View drift status with per-feature PSI bars and report history; trigger
    "evaluate now"
35. Score a test transaction from a form (field validation, result card with
    probability/variant/version)
36. Auto-refresh all panels every 30 s (paused when tab hidden) + manual refresh

**Tooling & deployment**

37. Seed the database with realistic demo predictions (`make seed`)
38. Apply database migrations (`make migrate`; automatic on container start)
39. Train a first model automatically on container first boot
    (`FRAUD_BOOTSTRAP_MODEL=true`)
40. Start the full local stack with one command — API, PostgreSQL, MLflow,
    Prometheus, Grafana (`docker compose up`)
41. Export the OpenAPI spec to a file (`make openapi`)
42. Switch live traffic blue↔green on AKS with one script (and switch back to
    roll back)
43. Run a post-deploy smoke test (health + real prediction) before traffic cutover

## Stack

Python 3.12 · FastAPI · Poetry · SQLAlchemy 2 + Alembic (SQLite locally,
PostgreSQL in compose/K8s) · scikit-learn · MLflow (optional tier) ·
Prometheus + Grafana · React 18 + Vite (dashboard) · Docker · Kubernetes
(AKS, blue-green) · Azure ML · Azure DevOps · Terraform (azurerm)

## Quickstart (local, zero external services)

```bash
# 1. Install dependencies (creates the virtualenv).
#    poetry.lock is intentionally not committed - `poetry install` resolves and
#    writes it on first run; commit it in your fork/CI.
poetry install

# 2. Configure environment (local default is a SQLite file - no DB server needed)
cp .env.example .env

# 3. Train and register the first model (writes models/v1 + registry.json)
make train

# 4. Run the API with hot reload
make run            # http://localhost:8000/docs
```

Try it:

```bash
curl -X POST http://localhost:8000/api/v1/predictions \
  -H "Content-Type: application/json" \
  -d '{"transaction_id":"txn-1","account_id":"acct-1","amount":250.0,
       "merchant_category":"electronics","timestamp":"2026-07-12T10:00:00Z"}'
```

Useful targets: `make seed` (demo rows), `make migrate` (Alembic),
`make openapi` (regenerate `docs/api/openapi.json`).

### Monitoring dashboard (dev)

```bash
cd dashboard
npm install
npm run dev         # http://localhost:5173, proxies /api + /health to :8000
```

### Full stack via Docker

```bash
docker compose up --build
```

The API container applies migrations on boot (`scripts/entrypoint.sh`) and — with
`FRAUD_BOOTSTRAP_MODEL=true` (compose default) — trains an initial champion when
the model volume is empty.

| Service    | URL                    |
|------------|------------------------|
| API        | http://localhost:8000  |
| MLflow     | http://localhost:5000  |
| Prometheus | http://localhost:9090  |
| Grafana    | http://localhost:3000  |

## API surface

| Method | Path                              | Purpose                                    |
|--------|-----------------------------------|--------------------------------------------|
| POST   | `/api/v1/predictions`             | Score a transaction for fraud              |
| GET    | `/api/v1/predictions`             | List persisted predictions (paginated)     |
| GET    | `/api/v1/predictions/{txn_id}`    | One persisted prediction with features     |
| GET    | `/api/v1/models`                  | Model catalog + alias mapping              |
| GET    | `/api/v1/models/ab-config`        | Effective A/B configuration                |
| PUT    | `/api/v1/models/ab-config`        | Update traffic split / toggle at runtime   |
| POST   | `/api/v1/models/promote`          | Point an alias at a version                |
| GET    | `/api/v1/models/{version}`        | One model version                          |
| DELETE | `/api/v1/models/{version}`        | Delete a version (champion is protected)   |
| POST   | `/api/v1/admin/retrain`           | Trigger retraining (202 + job id)          |
| GET    | `/api/v1/admin/retrain/{job_id}`  | Retraining job status/result               |
| GET    | `/api/v1/admin/drift`             | Evaluate drift over recent traffic         |
| GET    | `/api/v1/admin/drift/reports`     | Persisted drift report history             |
| GET    | `/health/live` · `/health/ready`  | Probes (ready reports component detail)    |
| GET    | `/metrics`                        | Prometheus exposition                      |

Errors use RFC 7807 `application/problem+json`. The full OpenAPI spec lives at
[`docs/api/openapi.json`](docs/api/openapi.json) (and `/docs` at runtime).

## Test & lint

```bash
make test        # pytest (unit + integration + model markers)
make coverage    # pytest with coverage, gate at 70% (suite sits ~89%)
make lint        # ruff check
make format      # ruff format + autofix
make typecheck   # mypy
pre-commit install   # optional: run hooks on every commit
```

The suite is hermetic: per-test SQLite databases, a real model trained once per
session into a temp store, no network. MLflow/psycopg2/Azure SDKs are not needed.

## Deployment

CI/CD is `azure-pipelines.yml` + `pipelines/`, infrastructure is `infra/terraform/`:

1. **CI** — ruff + mypy + pytest (70% coverage gate) + dashboard build, then a
   **model validation** stage that trains a candidate and enforces the quality
   gates (AUC ≥ 0.85, recall ≥ 0.7) before any image is built.
2. **Build** — Docker image pushed to ACR
   `frauddetectacr.azurecr.io/fraud-detection-api:<tag>`.
3. **CD** — deploy the new tag to the *inactive* color (blue/green) in the
   `fraud-detection` namespace on `fraud-aks-<env>`, smoke test
   (`scripts/smoke_test.py`), then flip the `fraud-api` Service selector
   (`scripts/blue_green_switch.sh`); rollback = flip back.
4. **Environments** — `dev` → `staging` (approval) → `prod`, each in
   `rg-fraud-detection-<env>`, provisioned by Terraform.
5. **Model lifecycle** — drift (PSI > threshold) triggers retraining
   (`pipelines/ml-retraining.yml` / in-process local backend); the new version
   registers as `challenger`, is A/B tested, and is promoted via
   `POST /api/v1/models/promote` when it wins.

## Repository layout

```
src/fraud_detection/   application package (api, services, ml, db, monitoring)
tests/                 pytest suite (unit / integration / model markers)
migrations/            Alembic migrations (0001 schema, 0002 features, 0003 indexes)
dashboard/             React + Vite monitoring UI
scripts/               entrypoint, seed, smoke test, blue-green switch, openapi export
pipelines/             Azure DevOps stage/job templates + retraining pipeline
mlops/azureml/         Azure ML job specs (training, drift monitor)
k8s/                   Kubernetes manifests, kustomize overlays (blue-green)
infra/terraform/       Terraform (azurerm) IaC
monitoring/            Prometheus + Grafana config and dashboards
docs/                  architecture, plan, tech notes, OpenAPI spec
```

## Troubleshooting

- **`model: fallback` in `/health/ready`** — no model registered yet: run
  `make train` (local) or set `FRAUD_BOOTSTRAP_MODEL=true` (containers).
- **MLflow is down/absent** — expected to be non-fatal: the local model store
  serves; `fraud_model_fallback_total` only grows when *both* tiers are empty.
- **Windows** — the app and suite run natively (SQLite default); Docker builds
  use Linux images, and `scripts/entrypoint.sh` is LF-terminated on purpose.
