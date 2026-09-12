# Scientific Computing Platform — Project Plan

**Status:** v2 — Phases 1–2 implemented · **Owner:** Platform team · **Last updated:** 2026-07-08

A multi-purpose scientific computing engine for education: a numerical-methods
library with symbolic math (SymPy), interactive Jupyter computations, a web UI
with LaTeX rendering, and ML-assisted equation solving / derivation pattern
recognition deployed on AWS SageMaker.

Companion documents:

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — system design, diagrams, security, error handling
- [`TECH-NOTES.md`](./TECH-NOTES.md) — CI/CD, testing, deployment, environments, pitfalls
- [`adr/0001-modular-monolith.md`](./adr/0001-modular-monolith.md) — key architecture decision record

---

## 1.1 Project File Structure

Guiding principles:

1. **The math kernel is a library, not a service.** Everything scientific lives in
   `libs/sciengine` — a pure Python package with **no** web-framework imports. The
   FastAPI backend, Celery workers, Jupyter notebooks, and the SageMaker training
   code all consume the *same* library, so a numerical method is written and
   tested exactly once.
2. **One repo, several deployables.** Backend API, compute worker, frontend SPA,
   and ML artifacts are built independently but versioned together (uv + npm
   workspaces), which keeps education-semester releases atomic.
3. **Configs live next to what they configure**; cross-cutting tool config lives
   at the root.

```text
1-Scientific-Computing-Platform/
├── README.md                          # Quickstart + repo map
├── Makefile                           # One-word entry points: install/lint/test/dev/up
├── pyproject.toml                     # uv workspace root + shared ruff/mypy config
├── uv.lock                            # Committed lockfile (one resolver for all packages)
├── docker-compose.yml                 # Full local stack incl. one-shot migrate service
├── .env.example                       # Template of every runtime setting (never commit real .env)
├── .gitignore / .dockerignore / .editorconfig
├── .pre-commit-config.yaml            # ruff, ruff-format, nbstripout, hygiene hooks
│
├── .github/                           # CI/CD (must sit at REPO root once split into its own repo)
│   ├── workflows/
│   │   ├── ci.yml                     # lint → typecheck → tests (Postgres+Redis services) → notebooks → build
│   │   ├── deploy.yml                 # Images → ECR, one-off migration task, ECS rollout, SPA → S3/CloudFront
│   │   └── ml-pipeline.yml            # SageMaker pipeline runs + gated endpoint promotion
│   ├── dependabot.yml
│   └── PULL_REQUEST_TEMPLATE.md
│
├── docs/                              # This plan + architecture + tech notes + ADRs
│
├── libs/
│   └── sciengine/                     # ★ Shared scientific kernel (pure Python, framework-free)
│       ├── pyproject.toml             # numpy/scipy/sympy/matplotlib (+ [ml] extra: sklearn)
│       ├── src/sciengine/
│       │   ├── exceptions.py          # Error taxonomy shared by API/workers/notebooks
│       │   ├── runtime.py             # ★ Killable sandbox subprocess w/ hard timeouts
│       │   ├── numerical/
│       │   │   ├── roots.py           # bisection, Newton (+traces), Brent
│       │   │   ├── integration.py     # trapezoid/Simpson (+Richardson errors), adaptive, improper
│       │   │   ├── ode.py             # solve_ivp wrapper + Euler/RK4 + text-expression IVPs
│       │   │   ├── interpolation.py   # barycentric Lagrange, cubic splines
│       │   │   └── linalg.py          # linear solve, eigendecomposition (+char poly LaTeX)
│       │   ├── symbolic/
│       │   │   ├── parsing.py         # ★ THE ONLY sanctioned parser of untrusted math input
│       │   │   ├── solver.py          # equations + systems, JSON-ready results
│       │   │   ├── steps.py           # step-by-step derivations (linear/quadratic/generic)
│       │   │   ├── calculus.py        # diff / integrate / limits / Taylor series
│       │   │   └── latex.py           # LaTeX rendering + aligned derivation blocks
│       │   ├── plotting/function_plot.py  # Headless Matplotlib (Agg, OO API) → SVG/PNG bytes
│       │   └── ml/
│       │       ├── features.py        # Featurization + heuristic classifier (serving parity)
│       │       ├── corpus.py          # Synthetic labeled corpus generator
│       │       └── model.py           # Trainable sklearn baseline + artifact format
│       └── tests/                     # 103 tests, 91% coverage (golden, property-based, security)
│
├── backend/                           # ★ FastAPI application + async compute workers
│   ├── pyproject.toml / Dockerfile / alembic.ini
│   ├── app/
│   │   ├── main.py                    # App factory, problem+json handlers, request IDs, lifespan
│   │   ├── core/                      # config (pydantic-settings) · JSON logging · JWT+scrypt
│   │   ├── api/
│   │   │   ├── deps.py                # Settings/session DI, CurrentUser + OptionalUser
│   │   │   └── v1/endpoints/
│   │   │       ├── auth.py            # register/login/refresh(rotating)/logout/me
│   │   │       ├── symbolic.py        # solve (202-escalation), diff/integrate/limit/series, render
│   │   │       ├── computations.py    # CRUD + artifact serving (owner-scoped)
│   │   │       ├── plots.py           # interactive SVG renders (sandboxed)
│   │   │       ├── ml.py              # SageMaker classify w/ heuristic fallback
│   │   │       └── health.py          # /healthz + real /readyz (DB+Redis probes)
│   │   ├── schemas/                   # Pydantic models incl. per-kind payload validation
│   │   ├── services/
│   │   │   ├── symbolic_service.py    # sandbox calls + canonical-form result cache
│   │   │   ├── computation_service.py # persistence + queue dispatch (idempotency = row id)
│   │   │   ├── auth_service.py        # registration, login, refresh rotation, revocation
│   │   │   ├── cache.py               # Redis result cache w/ in-memory fallback
│   │   │   ├── artifact_store.py      # local-dir or S3 artifact backends
│   │   │   ├── plot_service.py
│   │   │   └── sagemaker_client.py    # InvokeEndpoint + fail-soft heuristic path
│   │   ├── db/
│   │   │   ├── models.py              # User, RefreshToken, Computation, Notebook (portable types)
│   │   │   ├── session.py             # async engine (API)
│   │   │   └── sync_session.py        # sync engine (workers, Alembic)
│   │   └── workers/
│   │       ├── celery_app.py          # threads pool, acks_late, budgets
│   │       ├── executors.py           # per-kind execution (unit-testable, sandboxed)
│   │       └── tasks.py               # compute.run_computation — terminal-status guarantee
│   ├── migrations/versions/           # 0001 initial schema · 0002 refresh_tokens
│   └── tests/                         # 68 tests, 93% coverage (unit + full-stack integration)
│
├── frontend/                          # ★ React 19 + TypeScript + Vite SPA
│   ├── package.json / package-lock.json / tsconfig.json / vite.config.ts
│   ├── eslint.config.js / .prettierrc.json / Dockerfile / nginx.conf
│   └── src/
│       ├── api/                       # typed client (auth header, single-flight refresh), endpoints
│       ├── auth/AuthContext.tsx       # session provider
│       ├── hooks/useEquationSolver.ts # cancellable state machine incl. 202-queued state
│       ├── components/
│       │   ├── EquationSolver.tsx     # solve + LaTeX solutions + expandable derivation steps
│       │   ├── AuthPanel.tsx          # login/register with validation
│       │   ├── ComputationsPage.tsx   # job submit/list/poll/delete + artifact viewing
│       │   ├── LatexBlock.tsx / PlotViewer.tsx
│       │   └── *.test.tsx             # vitest + Testing Library (10 tests)
│       └── App.tsx / main.tsx / index.css / setupTests.ts
│
├── notebooks/                         # Jupyter teaching material — executed by nbmake in CI
│   ├── 01-getting-started.ipynb
│   └── templates/lesson-template.ipynb
│
├── ml/                                # SageMaker training & inference (thin shims over sciengine.ml)
│   ├── README.md / requirements.txt
│   ├── training/train.py              # SM_* contract; CSV or synthetic corpus; runs locally too
│   ├── inference/inference.py         # model_fn/input_fn/predict_fn/output_fn (skew-guarded)
│   └── pipelines/
│       ├── sagemaker_pipeline.py      # train → register (PendingManualApproval)
│       └── promote_endpoint.py        # Approved package → serverless endpoint (blue/green)
│
└── infrastructure/
    ├── README.md                      # IaC strategy (Terraform planned)
    └── sagemaker/endpoint-config.example.json
```

### Why this shape (quick rationale)

| Decision | Rationale |
| --- | --- |
| `libs/sciengine` separate from `backend/` | Notebooks and SageMaker jobs import the math kernel **without** FastAPI/SQLAlchemy; 90%+ coverage stays tractable. |
| `sciengine/runtime.py` sandbox in the kernel | Both the API fast path and workers need the killable-subprocess guard; it belongs beside the math it guards. |
| `services/` layer between endpoints and engine/DB | Endpoints stay ~20 lines; business rules are unit-testable without HTTP. |
| Portable model types (Uuid/JSON with JSONB variant) | The identical models run on per-test SQLite files locally and Postgres in CI/prod. |
| `ml/` outside `backend/` | Training containers have a different dependency set and lifecycle; logic still lives in `sciengine.ml` for parity. |
| Root `pyproject.toml` as uv workspace | One committed lockfile, one `uv sync`, consistent resolver across backend + library. |

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation ✅ complete

- [x] **Repo bootstrap**: `uv.lock` committed; `package-lock.json` committed; pre-commit config in place; CI defined end-to-end.
- [x] **sciengine hardening**: two-pass parser (guard before evaluate), complexity guards (op count, exponent, integer bits, depth, digit runs); Hypothesis property tests; 91% coverage.
- [x] **Symbolic MVP**: `solve_equation` (+ systems), `differentiate`, `integrate_symbolic`, `limit`, `taylor_series` — all with LaTeX output and golden tests.
- [x] **API skeleton**: `/healthz`, real `/readyz` (DB+Redis probes), `POST /symbolic/*`, problem+json error mapping, request-ID middleware.
- [x] **Database**: Alembic 0001+0002 (validated offline and executed in Docker); computations CRUD wired end-to-end with ownership scoping tests.
- [x] **Auth MVP**: register/login/refresh/logout/me; JWT access + rotating single-use refresh tokens with server-side revocation; scrypt hashing; anti-enumeration login.
- [x] **Local dev stack**: `docker compose up` runs db/redis/migrate/api/worker (+frontend/jupyter services); verified end-to-end against the containerized stack.
- [x] **CI**: ruff + mypy + eslint + tsc; pytest with coverage gates (sciengine ≥90%, backend ≥80%); vitest; notebook execution (nbmake); Docker build check.

### Phase 2 — Core features ✅ complete (except the two AWS-account items)

- [x] **Async compute plane**: Celery worker (threads pool), `compute.run_computation` with terminal-status guarantee + idempotency; sandbox subprocess with hard kill (Windows-safe); solve auto-escalates to 202+job for authenticated users past the sync budget.
- [x] **Plot pipeline**: interactive SVG endpoint + queued plot jobs → artifact store (local dir or S3 backends) → authenticated artifact serving / pre-signed URLs.
- [x] **Frontend v1**: EquationSolver (with derivation steps), LatexBlock, PlotViewer, ComputationsPage (submit/poll/delete/artifacts), auth screens, token refresh single-flight.
- [x] **Jupyter integration (CI + templates)**: `nbmake` executes all notebooks in CI; lesson template shipped.
- [x] **ML v1**: synthetic corpus generator, sklearn baseline in `sciengine.ml.model` (validation accuracy 1.0 on synthetic corpus), SageMaker training entrypoint + pipeline definition (train → register PendingManualApproval).
- [x] **ML serving path**: `/ml/classify` with SageMaker `InvokeEndpoint` + always-on heuristic fallback; inference handlers with feature-order skew guard; endpoint promotion script (blue/green).
- [ ] **SageMaker Studio kernel image**: build & publish the custom kernel image to ECR *(needs an AWS account; all code/config it packages is ready)*.
- [ ] **Dev environment on AWS**: create ECR/ECS/RDS/ElastiCache/CloudFront and set the per-environment GitHub variables consumed by `deploy.yml` *(workflow logic is complete; only account-specific values remain)*.

### Phase 3 — Polish & optimization (partially done; rest is roadmap)

- [x] **Step-by-step derivations (v1)**: linear isolation, quadratic factoring / quadratic-formula steps, generic canonical-form fallback; rendered as KaTeX `aligned` blocks with a steps toggle in the UI.
- [x] **Result caching**: canonical-form (`srepr`) SHA-256 keys → Redis with in-process LRU fallback; `cached` flag surfaced to the UI.
- [x] **Rate limiting**: per-identity fixed-window limiter (Redis with in-process fallback) on all `POST /api/v1/*` incl. login; 429 problem+json with `Retry-After`; configurable via `RATE_LIMIT_PER_MINUTE`.
- [ ] **Observability**: CloudWatch dashboards (queue depth, solve latency p95, SageMaker invocations), alarms, Sentry release tracking, structlog processors.
- [ ] **Derivation engine v2**: rule-tracing across SymPy rewrites for arbitrary equation classes.
- [ ] **Handwritten-equation OCR (stretch)**: image → LaTeX model evaluation on SageMaker.
- [ ] **Load & soak tests**: Locust scenario "300 students submit within 60 s"; tune worker autoscaling + SageMaker concurrency.
- [ ] **Hardening pass**: make `pip-audit`/`npm audit`/image scanning blocking CI gates; sandbox audit; secrets rotation runbook.
- [ ] **Instructor tooling**: notebook template gallery, assignment export (LaTeX → PDF via sandboxed TeX service).

### Exit criteria

- **P1 done** ✅ — a student can register, type `x^2 - 4 = 0`, get LaTeX-rendered roots (with steps), and the result is persisted — locally via docker compose, with the test suite green.
- **P2 done** ✅ (code-complete) — heavy jobs run async through the real broker/worker, notebooks are CI-executed, `/ml/classify` has a trained-model path; the two remaining checklist items are AWS-account provisioning, not code.
- **P3 done** — production environment with dashboards, quotas, and a documented incident runbook (roadmap).
