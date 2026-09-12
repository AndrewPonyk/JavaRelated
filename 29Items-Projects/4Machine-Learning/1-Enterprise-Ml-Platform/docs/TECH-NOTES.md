# Enterprise ML Platform — Technical Notes

Actionable engineering guidance for the platform's tech stack
(Python 3.12 · FastAPI · PyTorch/TF/sklearn/XGBoost · MLflow · Kubeflow ·
TPOT · React/TS · AWS SageMaker · GitHub Actions).

---

## 3.1 CI/CD Pipeline Design

Pipeline-as-code in **GitHub Actions**. Three workflows: `ci.yml` (every PR),
`cd-staging.yml` (merge to `main`), `cd-prod.yml` (tagged release, gated).

```text
        ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
 PR ──▶ │  Lint    │──▶│   Test   │──▶│  Build   │──▶│  Scan    │──▶│  Publish │
        │ ruff/    │   │ pytest + │   │ docker   │   │ trivy +  │   │  to ECR  │
        │ mypy/    │   │ vitest + │   │ images   │   │ pip-audit│   │ (sha tag)│
        │ eslint   │   │ coverage │   │          │   │          │   │          │
        └──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘
                                                                          │
   main ──────────────────────────────────────────────────▶ Deploy → STAGING
   tag  ────────────────────────── (manual approval) ──────▶ Deploy → PROD
```

**Key stages**
1. **Lint / static analysis** — `ruff` (lint+format), `mypy --strict` (backend),
   `eslint` + `tsc --noEmit` (frontend). Fast fail (< 2 min).
2. **Test** — `pytest` (unit + integration via service containers), `vitest`
   for frontend. Enforce coverage gate (see §3.2).
3. **Build** — multi-stage Docker images per service; tag with commit SHA.
4. **Security scan** — `trivy` (images), `pip-audit` / `npm audit` (deps),
   secret scan.
5. **Publish** — push to **Amazon ECR**; ML images cached per framework to
   avoid re-downloading torch/tf on every build.
6. **Deploy** — staging auto; prod manual-approval. Migrations run as a
   pre-deploy job; serving rollout is canary (see §3.3).

**ML-specific**: model retraining/promotion runs in **Kubeflow Pipelines**, not
GitHub Actions. CI validates *pipeline code*; the pipeline itself executes
training on cluster/SageMaker compute. Promote models by **MLflow stage
transition**, gated by evaluation metrics — never bake model weights into images.

---

## 3.2 Testing Strategy

| Layer | Tooling | Target |
|-------|---------|--------|
| **Unit** | `pytest`, `pytest-asyncio`, `unittest.mock` / `vitest` | ≥ 85% on `services/`, `core/`; ≥ 70% overall |
| **Integration** | `pytest` + `testcontainers` (Postgres, Redis, MLflow), `httpx.AsyncClient` | All API routes, DB migrations, MLflow round-trips |
| **E2E** | **Playwright** against ephemeral staging | Critical flows: experiment → train → register → serve |
| **Load** | **Locust / k6** on serving gateway | p95 latency & throughput SLOs |
| **ML validation** | `deepchecks` / custom | Data integrity, model regression vs. baseline |

**Principles**
- Keep `services/` pure and framework-free so business logic is unit-testable
  without spinning up FastAPI, SageMaker, or GPUs.
- **Mock external boundaries** (SageMaker, S3, MLflow) in unit tests via
  `moto` and recorded fixtures; exercise them for real in integration tests.
- **Model tests ≠ code tests.** Add *behavioral* model checks (invariances,
  slice metrics, no performance regression on a frozen eval set) as a separate
  gate before promotion — flaky stochastic tests must be seeded.
- Coverage is a floor, not a goal; require tests on every bug fix (regression
  test reproduces the bug first).

---

## 3.3 Deployment Strategy

- **Containerize everything.** Multi-stage Dockerfiles; slim runtime base
  (`python:3.12-slim`), non-root user, pinned deps. **Separate serving images
  per framework** (torch / tf / sklearn+xgboost) to keep each image lean and
  cold-starts fast.
- **Two compute planes**:
  - *Serving* — **AWS SageMaker endpoints** for large/managed models;
    **in-cluster predictors on EKS** for lightweight sklearn/XGBoost. Both behind
    the unified `serving_service` so callers don't care where a model runs.
  - *Batch/training* — **Kubeflow Pipelines on EKS** + **SageMaker training
    jobs** (managed spot for cost).
- **Progressive delivery**: new model versions roll out **shadow → canary (5–10%)
  → full** with automatic rollback on error-rate/latency/metric regression. App
  services use rolling deploys with readiness gates; consider Argo Rollouts.
- **Migrations** run as an init/job step *before* app rollout; keep them
  backward-compatible (expand/contract) so rollbacks are safe.
- **IaC**: Terraform for AWS (EKS, RDS, S3, IAM/IRSA, ECR, SageMaker);
  kustomize/Helm for in-cluster platform services.

---

## 3.4 Environment Management

- **12-factor config** via environment variables, loaded by Pydantic `Settings`
  (`backend/src/core/config.py`). No environment branching in code — only config
  values differ.
- **Three environments**: `dev` (local docker-compose), `staging` (prod-like,
  auto-deployed), `prod` (gated). Each has its own AWS account or VPC + isolated
  MLflow/registry and S3 buckets.
- **Secrets** come from AWS Secrets Manager / SSM at runtime; only non-secret
  config lives in `config/`. Local dev uses a git-ignored `.env`.

See `.env.example` (repo root) for the full, documented template. Highlights:

```dotenv
# Core
APP_ENV=dev
LOG_LEVEL=INFO
API_PORT=8000

# Database
DATABASE_URL=postgresql+asyncpg://mluser:changeme@localhost:5432/mlplatform

# MLflow
MLFLOW_TRACKING_URI=http://localhost:5000
MLFLOW_S3_ENDPOINT_URL=
MLFLOW_ARTIFACT_BUCKET=s3://enterprise-ml-artifacts

# AWS / SageMaker
AWS_REGION=us-east-1
SAGEMAKER_EXECUTION_ROLE_ARN=
SAGEMAKER_DEFAULT_INSTANCE_TYPE=ml.m5.large

# Cache / features
REDIS_URL=redis://localhost:6379/0

# Auth (OIDC)
OIDC_ISSUER=https://example.auth.us-east-1.amazoncognito.com
OIDC_AUDIENCE=enterprise-ml-platform
JWT_ALGORITHMS=RS256
```

---

## 3.5 Version Control Workflow

**Trunk-based with short-lived feature branches** and PR review.

- `main` is always deployable; protected (required checks, ≥1 review, no direct
  pushes). Feature branches live < 2 days and merge via squash.
- **Releases** are cut by **semver tags** (`v1.4.0`) → triggers prod CD with
  manual approval.
- **Rationale**: a platform consumed by many data scientists needs a continuously
  releasable trunk and small, reviewable changes. Full Gitflow's long-lived
  `develop`/`release` branches add merge overhead without payoff here; trunk-based
  pairs naturally with feature flags + progressive model rollout.
- ML artifacts are **versioned in MLflow/S3, not git**. Use **DVC** or dataset
  pointers for large data; never commit weights or datasets to the repo.
- Conventional Commits drive automated changelogs.

---

## 3.6 Common Pitfalls (this stack)

- **Dependency hell across frameworks.** PyTorch, TensorFlow, and TPOT pin
  conflicting `numpy`/`protobuf`/CUDA versions. *Mitigation*: isolate per-purpose
  envs/images; don't force one mega-environment. Use a lockfile (`uv` / `pip-tools`).
- **Train/serve skew.** Feature transforms diverge between training and inference.
  *Mitigation*: share one transform module (or feature store) across both paths;
  log feature schemas and assert them at serve time.
- **Giant Docker images.** A naive `pip install torch tensorflow` yields multi-GB
  images and minute-long cold starts. *Mitigation*: framework-specific slim
  images, CPU-only wheels where GPUs aren't needed, layer caching in CI.
- **MLflow registry as a bottleneck/SPOF.** *Mitigation*: HA tracking server,
  RDS multi-AZ backend, cache reads, treat stage transitions as the only
  promotion mechanism.
- **Silent model drift.** Models degrade without code changes. *Mitigation*:
  the drift service is not optional — baseline snapshots + scheduled PSI/KS with
  alerting from day one.
- **SageMaker cost surprises.** Always-on GPU endpoints and forgotten training
  jobs burn budget. *Mitigation*: managed spot for training, autoscaling +
  scale-to-zero where latency allows, cost alerts, endpoint TTLs.
- **Stochastic test flakiness.** Unseeded model tests fail randomly in CI.
  *Mitigation*: seed everything; assert on tolerances/slices, not exact values.
- **Async footguns.** Blocking calls (sync boto3, heavy CPU inference) inside
  FastAPI's event loop stall the gateway. *Mitigation*: run blocking/CPU work in
  thread/process pools or offload to the batch plane.
- **Kubeflow learning curve.** KFP DSL + artifact passing is non-obvious.
  *Mitigation*: standardize reusable components, keep pipelines thin, test
  component logic as plain Python.
