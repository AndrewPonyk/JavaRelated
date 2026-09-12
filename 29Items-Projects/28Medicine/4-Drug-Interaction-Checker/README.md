# Drug Interaction Checker

A drug–drug interaction checker built on a **Neo4j** graph of drugs, ingredients,
and drug classes, with **RxNorm** normalization, **pharmacy-system integration**,
and an **ML severity-prediction** tier over the drug-property network.

- **Backend:** Python 3.12 · FastAPI (async) · Neo4j async driver · httpx · structlog
- **Frontend:** React 18 · TypeScript · Vite · TanStack Query
- **ML:** graph-feature severity model served as a separate workload
- **Infra:** Docker · Kustomize · AWS EKS · GitHub Actions

> ⚕️ **Clinical safety:** ML predictions are *advisory* and flagged as such.
> Curated, sourced interactions are authoritative. Not a substitute for
> professional clinical judgment.

## Documentation

| Doc | Contents |
| --- | --- |
| [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) | File structure, rationale, phased TODO |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Pattern, diagrams, data flow, scaling, security |
| [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) | CI/CD, testing, deploy, env, workflow, pitfalls |

## Quickstart (Docker — full stack)

```bash
cp .env.example .env
docker compose up --build
```

`docker compose` starts Neo4j, runs the **migrate** job (applies constraints +
seed data, then exits), and brings up the API, ML service, and frontend:

| Service   | URL                              |
| --------- | -------------------------------- |
| Frontend  | http://localhost:5173            |
| API docs  | http://localhost:8000/docs       |
| ML health | http://localhost:8001/health     |
| Neo4j     | http://localhost:7474 (neo4j/password) |

### Try it

```bash
# Known interaction (seeded): warfarin + aspirin -> major
curl -s http://localhost:8000/api/v1/interactions/check \
  -H 'content-type: application/json' \
  -d '{"drugs":[{"name":"warfarin"},{"name":"aspirin"}],"include_ml_prediction":true}'
```

## API

| Method & path | Auth | Purpose |
| --- | --- | --- |
| `GET /api/v1/health` · `/health/live` · `/health/ready` | – | Liveness/readiness probes |
| `POST /api/v1/auth/token` | – (dev only) | Issue a JWT with scopes for testing |
| `GET /api/v1/drugs/search?q=` | – | Normalize a drug name (graph-first, RxNorm fallback) |
| `GET /api/v1/drugs/{rxcui}` | – | Drug detail (graph, RxNorm fallback) |
| `POST /api/v1/interactions/check` | – | Check a drug list (+ optional ML prediction) |
| `GET /api/v1/interactions/{a}/{b}` | – | Pairwise check by RxCUI |
| `POST /api/v1/pharmacy/check-batch` | `pharmacy:check` | Pharmacy batch check (opaque `patient_ref`, no PHI) |
| `POST/GET/DELETE /api/v1/admin/drugs` | `admin` | Drug catalog CRUD |
| `POST /api/v1/admin/classes` | `admin` | Upsert a drug class |
| `POST/GET/DELETE /api/v1/admin/interactions` | `admin` | Interaction catalog CRUD |

Full interactive spec at `/docs` (Swagger) and `/openapi.json`.

### Authenticated calls (dev)

```bash
TOKEN=$(curl -s http://localhost:8000/api/v1/auth/token \
  -H 'content-type: application/json' \
  -d '{"scopes":["pharmacy:check","admin"]}' | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

curl -s http://localhost:8000/api/v1/pharmacy/check-batch \
  -H "authorization: Bearer $TOKEN" -H 'content-type: application/json' \
  -d '{"patient_ref":"p-001","medications":[{"name":"warfarin"},{"name":"aspirin"}]}'
```

## Local development (without Docker)

```bash
# Backend
cd backend
python -m venv .venv && . .venv/Scripts/activate   # (Linux/mac: source .venv/bin/activate)
pip install -r requirements-dev.txt
uvicorn app.main:app --reload          # API on :8000
uvicorn app.ml_main:app --port 8001    # ML service on :8001

# Frontend
cd frontend
npm install
npm run dev                            # Vite dev server on :5173
```

Apply Neo4j schema + seed data against a running Neo4j:

```bash
python scripts/seed_neo4j.py            # schema + seed
python scripts/seed_neo4j.py --schema-only
```

## Testing

```bash
# Backend — unit tests (no external services), with coverage
cd backend && pytest -m "not integration" --cov=app

# Backend — integration tests (needs a running, seeded Neo4j)
pytest -m integration

# Frontend — unit/component tests
cd frontend && npm run test
```

Quality gates (run in CI): `ruff`, `black --check`, `mypy app`, `pytest`
(backend); `eslint`, `tsc`, `vitest`, `vite build` (frontend). The `Makefile`
wraps the common tasks (`make test`, `make lint`, `make dev`).

## ML severity model

The ML service (`app.ml_main`) serves a severity prediction per ingredient pair
from **drug-property-graph features** (shared classes, graph distance, node-embedding
similarity). It runs a deterministic **rule-based** baseline by default, and loads a
**trained joblib artifact** when `ML_MODEL_LOCAL_PATH` points to one. Train a model
offline with `ml/training/train.py` (see [`ml/README.md`](ml/README.md)).

## Deployment

Containerized images deploy to **AWS EKS** via Kustomize overlays
(`infra/k8s/overlays/{dev,staging,prod}`) and the `deploy` GitHub Actions
workflow (OIDC → ECR → `kubectl apply -k`). See
[`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) and [`infra/terraform/`](infra/terraform/).

## License

For demonstration / educational use.
