# Portfolio Management Dashboard — Technical Notes

## 3.1 CI/CD Pipeline Design

Two GitHub Actions workflows (see `.github/workflows/`):

**`ci.yml` — runs on every PR and push to `main`/`develop`**

```text
lint ──► typecheck ──► test ──► build
```

1. **Lint** — `ruff check` + `ruff format --check` (backend), `eslint` (frontend).
2. **Typecheck** — `mypy app` (backend), `tsc --noEmit` (frontend).
3. **Test** — `pytest --cov` with a real Postgres service container;
   `vitest` for frontend. Coverage gate fails the build below threshold.
4. **Build** — `docker build` both images to validate Dockerfiles (not pushed).
5. **Security** — `pip-audit` + `npm audit --production` (non-blocking warn → blocking later).

**`deploy.yml` — runs on push to `main` (or tag) after CI passes**

```text
build & tag image ──► push to ECR ──► render task def ──► deploy to ECS ──► smoke test
```

- Authenticate to AWS via **OIDC** (no long-lived keys in GitHub).
- Image tagged with the git SHA (immutable, traceable, easy rollback).
- `aws-actions/amazon-ecs-deploy-task-definition` updates the service and
  **waits for stability**; run Alembic migrations as a one-off ECS task
  *before* shifting traffic.
- Environments: `develop` → **dev**, `main` → **staging** (auto) → **prod**
  (manual approval gate via GitHub Environments).

---

## 3.2 Testing Strategy

| Layer | Tool | Focus | Target |
| --- | --- | --- | --- |
| Quant core (`analytics/`) | pytest + numpy.testing | Correctness vs known closed-form results & invariants | **≥ 90%** |
| Services | pytest | Orchestration, ownership/authZ, error mapping | ≥ 80% |
| API | pytest + httpx `AsyncClient` | Status codes, validation, contracts | ≥ 75% |
| Frontend | Vitest + Testing Library | Component render, loading/error states | ≥ 70% |
| E2E | Playwright | Critical flows: login → build portfolio → frontier | Smoke set |

**Quant testing notes (the part that matters most):**
- Validate against **closed-form** answers: the global minimum-variance weights
  are `w = Σ⁻¹𝟙 / (𝟙ᵀΣ⁻¹𝟙)`; assert the optimizer matches when unconstrained.
- Test **invariants**: weights sum to 1; long-only weights ∈ [0,1]; the
  max-Sharpe portfolio's Sharpe ≥ every frontier point's Sharpe; VaR ≤ CVaR.
- Use **fixed RNG seeds** for Monte Carlo and assert convergence (simulated VaR
  → parametric VaR within tolerance as `n_sims` grows).
- Property-based tests (Hypothesis) for return/covariance estimators.
- **Integration:** spin Postgres via Testcontainers/Compose; never mock the DB
  for migration and query tests.

---

## 3.3 Deployment Strategy

- **Containerized**, two images (`backend`, `frontend`) in **Amazon ECR**.
  Frontend is built to static assets and served by nginx (or pushed to S3+CloudFront).
- **AWS ECS on Fargate**: three services off the same backend image —
  `api` (behind ALB), `worker` (Celery, no inbound), and an optional
  `beat`/scheduler. Distinct task definitions, independent autoscaling.
- **Multi-stage Dockerfiles**: build deps in one stage, copy only artifacts into
  a slim runtime; run as a **non-root** user; `HEALTHCHECK` hits `/healthz`.
- **Migrations**: run `alembic upgrade head` as a short-lived ECS run-task in
  the deploy pipeline *before* the new revision takes traffic. Migrations must
  be backward-compatible (expand/contract) to keep zero-downtime deploys safe.
- **Rollback**: redeploy the previous immutable image tag; ECS keeps prior
  task-def revisions. Aim for blue/green (CodeDeploy) in Phase 3.

---

## 3.4 Environment Management

- **12-factor**: all config via environment variables, read once through
  `core/config.py` (Pydantic `BaseSettings`). No `if ENV == "prod"` scattered
  in code — behavior is driven by typed settings.
- **Local:** `.env` (git-ignored) consumed by `docker-compose`.
- **Cloud:** non-secret config as ECS task-def environment vars; secrets pulled
  from **Secrets Manager / SSM** via task `secrets` (never baked into images).
- Keep `.env.example` current — it is the contract for required config.

**`.env.example` template** (also created at repo root and `backend/`):

```dotenv
# --- App ---
APP_ENV=development           # development | staging | production
LOG_LEVEL=INFO
SECRET_KEY=change-me-32-bytes-min
ACCESS_TOKEN_EXPIRE_MINUTES=30
BACKEND_CORS_ORIGINS=http://localhost:5173

# --- Database ---
POSTGRES_HOST=db
POSTGRES_PORT=5432
POSTGRES_USER=portfolio
POSTGRES_PASSWORD=portfolio
POSTGRES_DB=portfolio
# DATABASE_URL overrides the parts above if set:
# DATABASE_URL=postgresql+psycopg://user:pass@host:5432/portfolio

# --- Redis / Celery ---
REDIS_URL=redis://redis:6379/0
CELERY_BROKER_URL=redis://redis:6379/1
CELERY_RESULT_BACKEND=redis://redis:6379/2

# --- Market data ---
MARKET_DATA_PROVIDER=stub     # stub | alphavantage | polygon
MARKET_DATA_API_KEY=

# --- Quant defaults ---
RISK_FREE_RATE=0.02
TRADING_DAYS_PER_YEAR=252
```

---

## 3.5 Version Control Workflow

**Trunk-based with short-lived feature branches** (a light GitHub Flow).

- `main` is always deployable; protected (PR + green CI + 1 review required).
- Branch naming: `feat/…`, `fix/…`, `chore/…`; rebase to keep history linear.
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`…) to drive changelogs
  and clarify intent.
- Optional `develop` integration branch only if a staging soak is required.
- **Rationale:** the team is small and deploys are frequent; long-lived release
  branches (full Gitflow) would add merge overhead without payoff. Trunk-based
  keeps integration continuous and the efficient-frontier/feature flags handle
  in-progress work behind toggles.

---

## 3.6 Common Pitfalls (this stack)

**Quant / numerical**
- **Non-PSD covariance:** sample covariance from short/aligned-poorly series can
  be near-singular → unstable optimizer weights. Mitigate with Ledoit-Wolf
  shrinkage and validate Σ is positive semi-definite before inversion.
- **Annualization mistakes:** mixing daily and annual μ/σ silently corrupts
  Sharpe and the frontier. Centralize the `√252` / `×252` conventions in
  `analytics/returns.py` and never annualize twice.
- **Look-ahead bias** in backtests; **survivorship bias** in the asset universe.
- **Float money:** never store currency as `float`. Use `NUMERIC`/`Decimal` for
  monetary amounts (returns/weights as float is fine).

**Python / FastAPI**
- **Blocking the event loop:** running NumPy-heavy work in an `async def` route
  stalls the server. Keep heavy compute in Celery, or use `run_in_threadpool`.
- **SQLAlchemy N+1** when loading holdings → prices; use `selectinload`/joins.
- **Pydantic v2** validation differences vs v1 (config, validators) — pin and
  follow v2 idioms.

**Frontend / viz**
- **Plotly bundle size:** import `plotly.js-dist-min` or a custom partial bundle;
  the full build is multi-MB.
- **Mixing React and D3 DOM control:** let React own the DOM and use D3 only for
  math/scales/layout (or render into a ref D3 fully owns) — fighting over the
  same nodes causes subtle bugs.
- **Float formatting:** present returns/VaR with explicit precision and locale;
  don't show raw `0.07321999…`.

**AWS / ECS**
- **Fargate task too small:** NumPy/BLAS needs RAM; OOM-killed tasks restart
  silently. Size worker tasks generously and set CloudWatch OOM alarms.
- **Migrations racing deploys:** run them as a gated pre-deploy task, never
  on app startup across N replicas (they'd race).
- **Secrets in env at build time:** keep them in task `secrets`, not image layers.
