# Drug Interaction Checker — Project Plan

> Neo4j graph database for drug–drug interactions, RxNorm-based drug
> normalization, pharmacy-system integration, and ML severity prediction over a
> drug-property network. Python/FastAPI backend, React frontend, deployed to
> AWS EKS via GitHub Actions.

---

## 1. Overview

| Aspect | Decision |
| --- | --- |
| **Backend** | Python 3.12 + FastAPI (async), served by Uvicorn |
| **Graph DB** | Neo4j 5.x (Bolt protocol, async driver) |
| **Drug normalization** | RxNorm / RxNav public REST API (`rxnav.nlm.nih.gov`) |
| **ML** | Severity prediction microservice (graph features → classifier) |
| **Frontend** | React 18 + TypeScript + Vite + TanStack Query |
| **Packaging** | Docker (multi-stage), Kustomize for K8s manifests |
| **Cloud** | AWS EKS, ECR, ALB Ingress, Secrets Manager, S3 (model artifacts) |
| **CI/CD** | GitHub Actions (lint → test → build → deploy) with OIDC to AWS |

The system exposes two primary capabilities:

1. **Interaction check** — given a set of drugs (by name, NDC, or RxCUI),
   normalize each to RxNorm ingredients, then query the Neo4j graph for known
   pairwise interactions.
2. **Severity prediction** — for ingredient pairs with *no* curated
   interaction, an ML model scores the likelihood/severity of an interaction
   using features derived from the drug-property graph (shared classes, pathway
   proximity, node embeddings). Predictions are clearly flagged as advisory.

---

## 2. Project File Structure

```text
4-Drug-Interaction-Checker/
├── docs/                              # Architecture & engineering docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── backend/                          # FastAPI service (API + ML serving)
│   ├── app/
│   │   ├── __init__.py               # package version
│   │   ├── main.py                   # API app factory + lifespan
│   │   ├── ml_main.py                # ML severity microservice entrypoint
│   │   ├── core/                     # cross-cutting concerns
│   │   │   ├── config.py             # pydantic-settings configuration
│   │   │   ├── security.py           # JWT helpers
│   │   │   ├── logging.py            # structured logging (structlog)
│   │   │   └── exceptions.py         # domain errors + handlers
│   │   ├── api/
│   │   │   ├── deps.py               # DI: repos, services, auth
│   │   │   └── v1/
│   │   │       ├── router.py         # aggregates v1 routers
│   │   │       └── endpoints/        # health, drugs, interactions, pharmacy
│   │   ├── models/                   # Pydantic domain schemas
│   │   ├── services/                 # business logic (orchestration)
│   │   ├── db/
│   │   │   ├── neo4j_client.py       # async driver lifecycle
│   │   │   └── repositories/         # Cypher data access
│   │   ├── integrations/
│   │   │   └── rxnorm_client.py      # external RxNorm/RxNav client
│   │   └── ml/                       # in-process inference (features+model)
│   │       ├── features.py
│   │       ├── model.py
│   │       └── predict.py
│   ├── tests/                        # unit + integration
│   ├── pyproject.toml                # ruff/black/mypy/pytest config
│   ├── requirements*.txt
│   ├── Dockerfile
│   └── .dockerignore
│
├── migrations/neo4j/                 # versioned Cypher: constraints + seed
│
├── ml/                               # OFFLINE training pipeline
│   ├── training/                     # train.py, dataset.py, graph_features.py
│   ├── requirements.txt
│   └── README.md
│
├── frontend/                         # React + TS SPA
│   ├── src/
│   │   ├── components/               # InteractionChecker, DrugSearch, ...
│   │   ├── hooks/                    # data-fetching hooks
│   │   ├── api/                      # axios client
│   │   └── types/
│   ├── package.json / tsconfig.json / vite.config.ts
│   ├── Dockerfile / nginx.conf
│   └── .eslintrc.cjs / .prettierrc
│
├── infra/
│   ├── k8s/
│   │   ├── base/                     # deployments, services, ingress, hpa
│   │   └── overlays/{dev,staging,prod}
│   └── terraform/                    # EKS cluster skeleton (IaC)
│
├── .github/workflows/                # backend-ci, frontend-ci, deploy
├── scripts/                          # seed_neo4j.py, dev_up.sh
├── docker-compose.yml                # local dev stack (neo4j+api+ml+web)
├── .env.example
├── .pre-commit-config.yaml
├── Makefile
└── README.md
```

### Rationale

- **`backend/` as a modular monolith with a second entrypoint** (`ml_main.py`)
  lets the API and ML inference share one codebase and image while deploying as
  **two independently scalable workloads** on EKS. This avoids premature
  microservice sprawl while keeping the ML tier separable (different CPU/replica
  sizing, blast-radius isolation).
- **`ml/` (offline) is separate from `backend/app/ml/` (online)**: training is a
  batch/CI concern producing artifacts to S3; inference is a request-path
  concern that loads those artifacts.
- **Repositories isolate Cypher** so business logic in `services/` never embeds
  query strings — this keeps the graph schema changeable in one place.
- **Kustomize overlays** keep environment differences (replicas, image tags,
  resource limits) declarative and reviewable.

---

## 3. Implementation TODO List

> Status: **Phases 1 & 2 implemented**; Phase 3 partially implemented (see notes).
> Backend: 129 unit tests (+3 integration), ~98% coverage, ruff/black/mypy clean.
> Frontend: 12 tests, eslint/tsc/vite-build clean.

### Phase 1 — Foundation (high priority) ✅

- [x] Initialize repo, pre-commit, ruff/black/mypy, EditorConfig.
- [x] Scaffold FastAPI app factory, settings, structured logging, error handlers.
- [x] Stand up Neo4j via `docker-compose`; apply constraints/indexes migration.
- [x] Implement RxNorm client (`/rxcui`, `/properties`, `/related`) + service.
- [x] Define graph schema (`Drug`, `Ingredient`, `DrugClass`, `INTERACTS_WITH`).
- [x] Implement `interaction_repository` + `interaction_service` (known pairs).
- [x] `POST /interactions/check` end-to-end with seed data.
- [x] React SPA skeleton: drug entry, check button, results list, loading/error.
- [x] Backend + frontend CI (lint, type-check, unit tests).
- [x] Health/readiness/liveness endpoints wired to probes.

### Phase 2 — Core features (medium priority) ✅

- [x] NDC → RxCUI resolution and ingredient expansion for combination drugs.
- [x] Pharmacy batch endpoint with auth (JWT/OIDC) + role/scope checks.
- [x] ML severity service: feature extractor over the graph + model wrapper (+ batch).
- [x] Offline training pipeline (`ml/training`) + artifact publishing to S3/local.
- [x] Node embeddings (Neo4j GDS) materialized onto `Ingredient.embedding` (training).
- [x] RxNorm lookup caching (TTL). *In-process `TTLCache`; swap for Redis when shared.*
- [x] Containerize all services; Kustomize base + overlays; HPA.
- [x] Terraform EKS, ECR, IRSA, ALB controller, External Secrets. *(ECR/S3 live; EKS module skeleton.)*
- [x] Deploy workflow (OIDC → ECR build/push → `kubectl`/kustomize rollout).
- [x] Integration tests against ephemeral Neo4j in CI.
- [x] Admin/ingestion CRUD for drugs, classes, and interactions.

### Phase 3 — Polish & optimization (lower priority) — partial

- [x] Rate limiting (in-memory sliding window) for API protection.
- [x] Audit logging of interaction checks (no PHI) for compliance.
- [x] Correlation IDs + structured access logs (request-context middleware).
- [ ] Full observability: OpenTelemetry traces, Prometheus metrics, dashboards.
- [ ] API keys / request signing for pharmacy partners.
- [ ] Model monitoring: drift detection, periodic retraining schedule.
- [ ] Graph data ETL from a curated interaction source (governance + provenance).
- [ ] Blue/green or canary deploys (Argo Rollouts) on EKS.
- [ ] Full accessibility (WCAG) audit + i18n on the frontend.
- [ ] Load/perf testing (k6) and capacity planning.

---

## 4. Definition of Done (per feature)

1. Code + types + docstrings; passes `ruff`, `black`, `mypy`.
2. Unit tests for logic; integration test where it crosses a boundary.
3. OpenAPI documented; example request/response.
4. Observable: structured logs + metrics on the hot path.
5. Deployed via overlay to `dev`; smoke-tested; rollback plan known.
