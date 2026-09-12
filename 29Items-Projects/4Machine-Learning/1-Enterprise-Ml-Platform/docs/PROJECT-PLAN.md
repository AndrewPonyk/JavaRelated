# Enterprise ML Platform — Project Plan

> Centralized ML platform for data scientists: experiment tracking, model
> registry, unified serving, A/B testing, AutoML feature engineering, and
> production drift detection.

| | |
|---|---|
| **Tech Stack** | Python 3.12 · FastAPI · PyTorch · TensorFlow · scikit-learn · XGBoost · MLflow · Kubeflow · TPOT · React · TypeScript |
| **Deployment** | AWS SageMaker · Kubeflow (EKS) · GitHub Actions |
| **Datastores** | PostgreSQL (metadata) · S3 (artifacts) · Redis (serving cache / feature lookups) |
| **Status** | Scaffolding / architecture phase |

---

## 1.1 Project File Structure

The repository is a **polyglot monorepo**. ML platform concerns (training,
serving, registry, drift) live under a Python backend; the data-scientist UI is
a separate React/TypeScript app; orchestration and infrastructure are isolated
so platform engineers can iterate without touching application code.

```text
1-Enterprise-Ml-Platform/
│
├── docs/                              # Architecture & engineering docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── backend/                          # Python 3.12 platform services
│   ├── src/
│   │   ├── api/                       # FastAPI presentation layer
│   │   │   ├── main.py                # App factory, middleware, lifespan
│   │   │   ├── dependencies.py        # Shared DI (auth, db session, mlflow)
│   │   │   ├── routers/               # HTTP route handlers (thin)
│   │   │   │   ├── experiments.py
│   │   │   │   ├── models.py          # Model registry endpoints
│   │   │   │   ├── serving.py         # Unified inference gateway
│   │   │   │   ├── ab_testing.py
│   │   │   │   └── drift.py
│   │   │   └── schemas/               # Pydantic request/response contracts
│   │   │       ├── experiment.py
│   │   │       └── model.py
│   │   │
│   │   ├── core/                      # Cross-cutting infrastructure
│   │   │   ├── config.py              # Pydantic Settings (12-factor)
│   │   │   ├── security.py            # JWT / OIDC, RBAC, scopes
│   │   │   └── logging.py             # Structured JSON logging
│   │   │
│   │   ├── services/                  # Business logic (framework-agnostic)
│   │   │   ├── experiment_service.py
│   │   │   ├── model_registry_service.py
│   │   │   ├── serving_service.py
│   │   │   ├── ab_testing_service.py
│   │   │   ├── drift_service.py
│   │   │   └── automl_service.py      # TPOT-driven feature engineering
│   │   │
│   │   ├── ml/                        # ML framework adapters
│   │   │   ├── training/              # Trainer abstractions (torch/tf/sklearn)
│   │   │   ├── serving/               # Predictor loaders + warm pool
│   │   │   ├── drift/                 # Statistical drift detectors
│   │   │   └── automl/                # TPOT pipeline wrappers
│   │   │
│   │   └── db/                        # Persistence layer
│   │       ├── session.py             # Async SQLAlchemy engine/session
│   │       └── models.py              # ORM models
│   │
│   ├── migrations/                    # Alembic schema migrations
│   │   └── versions/
│   ├── tests/
│   │   ├── unit/
│   │   └── integration/
│   ├── requirements.txt
│   ├── pyproject.toml                 # Tooling: ruff, mypy, pytest
│   └── Dockerfile
│
├── frontend/                         # React + TypeScript SPA
│   ├── src/
│   │   ├── components/                # Presentational + container components
│   │   ├── api/                       # Typed API client (fetch wrappers)
│   │   ├── hooks/                     # Data-fetching hooks (React Query)
│   │   ├── types/                     # Shared TS domain types
│   │   └── App.tsx
│   ├── package.json
│   ├── tsconfig.json
│   ├── .eslintrc.cjs
│   └── Dockerfile
│
├── pipelines/                        # Kubeflow Pipelines (KFP DSL)
│   ├── training_pipeline.py
│   └── components/                    # Reusable KFP components
│
├── infrastructure/                   # Infrastructure-as-Code
│   ├── terraform/                     # AWS (EKS, RDS, S3, IAM, SageMaker)
│   ├── kubeflow/                      # Kubeflow manifests / kustomize
│   ├── sagemaker/                     # Endpoint / model package configs
│   └── k8s/                           # Platform service Helm/manifests
│
├── config/                           # Static, non-secret config (per-env)
├── scripts/                          # Dev/ops helper scripts
│
├── .github/workflows/                # CI/CD pipelines
│   ├── ci.yml                         # Lint → test → build
│   ├── cd-staging.yml
│   └── cd-prod.yml
│
├── .env.example
├── docker-compose.yml                # Local dev: api + mlflow + postgres + redis
├── .pre-commit-config.yaml
├── .gitignore
└── README.md
```

### Layer boundaries (enforced)

- **`api/` never imports `db/` directly** — it goes through `services/`.
- **`services/` is framework-agnostic** — no FastAPI types leak in; this keeps
  business logic testable and reusable by Kubeflow components.
- **`ml/` adapters hide framework specifics** (PyTorch vs TF vs sklearn) behind
  a common `Trainer` / `Predictor` interface so the serving layer is unified.

---

## 1.2 Implementation TODO List

### ☐ Phase 1 — Foundation (High Priority)

- [ ] Stand up `core/config.py` (Pydantic Settings) + `.env` loading.
- [ ] Wire FastAPI app factory, health/readiness probes, structured logging.
- [ ] Provision PostgreSQL + Alembic baseline migration (experiments, models,
      deployments, ab_tests, drift_reports).
- [ ] Deploy a managed **MLflow tracking server** (S3 artifact store, RDS backend).
- [ ] Implement `experiment_service` + `model_registry_service` over MLflow APIs.
- [ ] AuthN/AuthZ: OIDC (Cognito/Okta) → JWT validation → RBAC scopes.
- [ ] CI pipeline: ruff + mypy + pytest + docker build on every PR.
- [ ] Terraform skeleton: VPC, EKS, RDS, S3 buckets, IAM roles (OIDC for CI).

### ☐ Phase 2 — Core Features (Medium Priority)

- [ ] **Unified serving layer**: model loader registry, warm pool, batch +
      real-time inference, SageMaker endpoint adapter.
- [ ] **A/B testing**: traffic splitting, experiment assignment, metric capture,
      statistical significance evaluation.
- [ ] **Drift detection API**: PSI / KS / KL detectors, baseline snapshots,
      scheduled jobs, alerting hooks.
- [ ] **AutoML**: TPOT feature-engineering + model-search service, exporting
      candidate pipelines into the registry.
- [ ] **Kubeflow training pipeline**: data-prep → train → evaluate → register.
- [ ] Frontend: experiments dashboard, model registry browser, A/B test console,
      drift monitoring views (React Query + typed client).
- [ ] CD pipelines: staging auto-deploy, prod gated/manual approval.

### ☐ Phase 3 — Polish & Optimization (Lower Priority)

- [ ] Model serving autoscaling (KEDA / HPA on request latency & queue depth).
- [ ] Canary + shadow deployments for new model versions.
- [ ] Feature store integration (offline/online parity).
- [ ] Cost dashboards (GPU utilization, SageMaker endpoint spend).
- [ ] Lineage / reproducibility graph (data → run → model → deployment).
- [ ] E2E tests (Playwright), load tests (Locust) on serving gateway.
- [ ] Observability: OpenTelemetry tracing across API → serving → SageMaker.
- [ ] Documentation site + onboarding runbooks for data scientists.

---

## Risk Register (top items)

| Risk | Impact | Mitigation |
|------|--------|------------|
| Framework sprawl (torch/tf/sklearn/xgboost) bloats images | Slow cold starts | Per-framework serving images; common predictor interface |
| MLflow registry as single point of failure | Platform-wide outage | HA tracking server, RDS multi-AZ, artifact store in S3 |
| Drift jobs are compute-heavy | Cost / contention | Run on Kubeflow batch nodes, not serving cluster |
| GPU scarcity for training | Pipeline queueing | SageMaker managed spot + Karpenter GPU node pools |
| Model/data PII leakage | Compliance breach | Tenant isolation, encrypted S3, scoped IAM, audit logging |
