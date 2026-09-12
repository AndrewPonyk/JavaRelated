# Enterprise ML Platform

A centralized platform for data scientists: **experiment tracking**, a
**model registry**, a **unified serving layer**, **A/B testing**, **AutoML**
feature engineering, and production **drift detection**.

> **Tech:** Python 3.12 · FastAPI · PyTorch / TensorFlow / scikit-learn / XGBoost ·
> MLflow · Kubeflow · TPOT · React + TypeScript · AWS SageMaker · GitHub Actions

---

## Documentation

| Doc | What's inside |
|-----|---------------|
| [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) | File structure, phased implementation TODO, risk register |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Pattern, component interactions, data flow, scalability, security |
| [docs/TECH-NOTES.md](docs/TECH-NOTES.md) | CI/CD, testing, deployment, env management, pitfalls |

## Repository layout

```text
backend/         FastAPI services (api → services → repository → DB; ML adapters)
frontend/        React + TypeScript data-scientist console
pipelines/       Kubeflow training & drift pipelines (KFP DSL)
infrastructure/  Terraform (AWS) + Kubeflow/SageMaker/K8s manifests
.github/workflows/  CI + staging/prod CD
docs/            Architecture & engineering docs
```

Durable state (experiments, runs, model versions, A/B tests, drift reports) is
persisted via **async SQLAlchemy** — **SQLite by default** (zero infra, survives
restart) and **PostgreSQL** in prod, selected by `DATABASE_URL`. High-volume
telemetry (live inference window, raw A/B samples) stays in a fast in-memory
buffer (Redis/S3 in prod). MLflow/SageMaker/Kubeflow remain adapter seams.

## Quick start (local)

```bash
cp .env.example .env

# Option A — full stack (API + MLflow + Postgres + Redis)
docker compose up --build
#   API docs   -> http://localhost:8000/docs
#   MLflow UI  -> http://localhost:5000

# Option B — backend only (SQLite, no infra needed; tables auto-created)
cd backend
pip install -r requirements-dev.txt
uvicorn src.api.main:app --reload      # http://localhost:8000/docs
#   → data persists in backend/mlplatform.db across restarts

# Frontend dev loop
cd frontend
npm install
npm run dev        # http://localhost:5173 (proxies /api -> :8000)
```

## Authentication (local)

All endpoints except `/health` and `/ready` require a Bearer token. In dev,
`AUTH_MODE=hs256` verifies a shared-secret JWT. Mint a token for local calls:

```bash
cd backend
python -c "import time; from src.core.security import encode_hs256; \
print(encode_hs256({'sub':'me','email':'me@example.com','aud':'enterprise-ml-platform', \
'scope':'experiments:read experiments:write models:promote serving:invoke', \
'exp':int(time.time())+3600}, 'dev-insecure-secret-change-me'))"
```

```bash
TOKEN=...   # paste the value above
curl -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"name":"Churn Model"}' \
     http://localhost:8000/experiments
```

For throwaway local experimentation only, set `AUTH_MODE=disabled` in `.env`
(rejected at startup when `APP_ENV=prod`).

## API endpoints

| Method | Path | Scope | Description |
|--------|------|-------|-------------|
| `GET`  | `/health`, `/ready` | — | Liveness / readiness probes |
| `POST` | `/experiments` | `experiments:write` | Create an experiment |
| `GET`  | `/experiments?limit=&offset=` | `experiments:read` | List experiments (paginated) |
| `GET`  | `/experiments/{id}` | `experiments:read` | Get one experiment |
| `POST` | `/experiments/{id}/train` | `experiments:write` | Submit a training run → registers a model version |
| `GET`  | `/models/{name}/versions` | `experiments:read` | List registered versions |
| `POST` | `/models/{name}/versions/{v}/stage` | `models:promote` (to Production) | Stage transition |
| `POST` | `/serving/{model}/predict` | `serving:invoke` | Inference via the unified layer (A/B aware) |
| `POST` | `/ab-tests` | `experiments:write` | Create a champion/challenger test |
| `GET`  | `/ab-tests/{id}/results` | `experiments:read` | Significance evaluation |
| `POST` | `/drift/{model}/check` | `experiments:read` | Run a PSI drift check |
| `GET`  | `/drift/{model}/latest` | `experiments:read` | Latest drift report |

Full interactive docs (request/response schemas, examples): **`/docs`** (Swagger)
and **`/redoc`**.

## Tests & quality

```bash
# Backend  (pytest + coverage gate ≥ 70%; currently ~88%)
cd backend && pytest

# Backend lint / types (CI)
ruff check . && mypy src

# Frontend
cd frontend && npm run typecheck && npm run lint && npm run build
```

## Troubleshooting

| Symptom | Cause / Fix |
|---------|-------------|
| `401 Unauthorized` on every call | Missing/expired Bearer token — mint a fresh one (see above) or set `AUTH_MODE=disabled` for local. |
| `403 Forbidden` | Token lacks the required scope (e.g. promoting to Production needs `models:promote`). |
| Startup error: *"jwt_secret is required…"* | `APP_ENV=prod` with `AUTH_MODE=hs256` and no `JWT_SECRET`. Provide a secret or use `oidc`. |
| `404` on `/serving/{model}/predict` | No model version registered yet — run `POST /experiments/{id}/train` first. |
| `409 Conflict` creating an experiment / A/B test | Duplicate experiment name, or an active A/B test already exists for that model. |
| Drift check returns `evaluated_features: 0` | No baseline set or no inferences logged yet — expected early-life behavior, not an error. |
| Want a clean local DB | Stop the app and delete `backend/mlplatform.db`; it's recreated empty on next startup. |
| Prod startup needs Postgres | Set `DATABASE_URL=postgresql+asyncpg://…` and run `alembic upgrade head` (the SQLite auto-create is for dev). |
| `docker compose` MLflow can't reach DB | Postgres still starting; the API waits on its healthcheck — re-run if MLflow raced ahead. |
| Frontend `import.meta.env` type error | Ensure `src/vite-env.d.ts` exists (provides Vite client types). |

## Status

Functional MVP: **persistent** metadata (async SQLAlchemy, survives restart),
JWT auth + RBAC, the full experiment → train → register → promote → serve flow,
real statistics (PSI/KS drift, Welch's t-test for A/B), pagination, and a
consistent RFC 7807 error surface — verified by 59 tests (~86% coverage) and a
live end-to-end HTTP run.

Still adapter seams (marked `TODO`): real model training (the trainer adapters
and predictions currently use a deterministic stand-in, not trained weights),
MLflow tracking/registry, SageMaker endpoints, and Kubeflow job submission. See
the phased checklist in [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md).
