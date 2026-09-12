# Portfolio Management Dashboard — Project Plan

> Portfolio optimizer built on Modern Portfolio Theory (MPT). Computes the
> efficient frontier, Value-at-Risk (VaR/CVaR), Sharpe/Sortino ratios, and
> Monte Carlo risk simulations for investment portfolios, with performance
> attribution and mean-variance asset-allocation optimization.

- **Backend:** Python 3.12, FastAPI, SQLAlchemy 2.x, Pandas, NumPy, SciPy
- **Database:** PostgreSQL 16 (Alembic migrations)
- **Frontend:** TypeScript, React (Vite), Plotly.js, D3.js
- **Async compute:** Celery + Redis (Monte Carlo / heavy optimization)
- **Deployment:** Docker → AWS ECS (Fargate), GitHub Actions CI/CD

---

## 1.1 Project File Structure

```text
3-Portfolio-Management-Dashboard/
├── docs/
│   ├── PROJECT-PLAN.md            # This document
│   ├── ARCHITECTURE.md            # Architecture, diagrams, decisions
│   └── TECH-NOTES.md              # CI/CD, testing, deployment, pitfalls
│
├── backend/
│   ├── app/
│   │   ├── main.py                # FastAPI app factory + ASGI entrypoint
│   │   ├── core/
│   │   │   ├── config.py          # Pydantic Settings (12-factor env config)
│   │   │   ├── security.py        # JWT auth, password hashing
│   │   │   └── logging.py         # Structured JSON logging setup
│   │   ├── api/
│   │   │   ├── deps.py            # Shared FastAPI dependencies (db, auth)
│   │   │   └── v1/
│   │   │       ├── router.py      # Aggregates all v1 routers
│   │   │       └── endpoints/
│   │   │           ├── portfolios.py    # Portfolio CRUD
│   │   │           ├── assets.py        # Asset / price-series endpoints
│   │   │           ├── optimization.py  # MPT optimization endpoints
│   │   │           └── risk.py          # VaR / Sharpe / Monte Carlo
│   │   ├── models/                # SQLAlchemy ORM models (DB tables)
│   │   │   ├── base.py
│   │   │   ├── user.py
│   │   │   ├── portfolio.py
│   │   │   ├── asset.py
│   │   │   └── holding.py
│   │   ├── schemas/               # Pydantic request/response DTOs
│   │   │   ├── portfolio.py
│   │   │   ├── optimization.py
│   │   │   └── risk.py
│   │   ├── services/              # Business logic / orchestration layer
│   │   │   ├── portfolio_service.py
│   │   │   └── market_data_service.py
│   │   ├── analytics/             # Pure quant domain (no I/O) — the core IP
│   │   │   ├── returns.py         # Return + covariance estimation
│   │   │   ├── mpt.py             # Mean-variance optimization, frontier
│   │   │   ├── risk_metrics.py    # VaR, CVaR, Sharpe, Sortino, drawdown
│   │   │   └── monte_carlo.py     # Correlated GBM simulation engine
│   │   ├── workers/               # Celery async tasks
│   │   │   └── tasks.py
│   │   └── db/
│   │       ├── session.py         # Engine + session factory
│   │       └── base.py            # Declarative base + metadata
│   ├── alembic/                   # DB migrations
│   │   ├── env.py
│   │   └── versions/0001_initial.py
│   ├── tests/
│   │   ├── conftest.py
│   │   ├── test_returns.py
│   │   ├── test_mpt.py
│   │   ├── test_risk_metrics.py
│   │   └── test_monte_carlo.py
│   ├── alembic.ini
│   ├── pyproject.toml             # Tooling config (ruff, mypy, pytest)
│   ├── requirements.txt
│   ├── Dockerfile
│   └── .env.example
│
├── frontend/
│   ├── src/
│   │   ├── main.tsx               # React entrypoint
│   │   ├── App.tsx
│   │   ├── api/client.ts          # Typed API client (fetch wrapper)
│   │   ├── types/portfolio.ts     # Shared TS types (mirror backend DTOs)
│   │   ├── hooks/usePortfolio.ts  # Data-fetching hook (loading/error)
│   │   └── components/
│   │       ├── PortfolioDashboard.tsx
│   │       ├── EfficientFrontierChart.tsx  # Plotly scatter of frontier
│   │       └── RiskMetricsPanel.tsx        # D3 gauge / KPI cards
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── .eslintrc.cjs
│   ├── Dockerfile
│   └── .env.example
│
├── infrastructure/
│   ├── ecs/task-definition.json   # ECS Fargate task definition template
│   └── README.md                  # IaC notes (ECR, ALB, RDS, Secrets Mgr)
│
├── .github/workflows/
│   ├── ci.yml                     # Lint → typecheck → test → build
│   └── deploy.yml                 # Build image → push ECR → deploy ECS
│
├── docker-compose.yml             # Local dev: api + worker + db + redis + web
├── .env.example                   # Root compose env template
├── .gitignore
└── README.md
```

### Layering rationale

The backend follows a strict **dependency direction**:

```text
api  →  services  →  analytics  +  db
```

- `analytics/` is **pure** (NumPy/Pandas in, numbers out) — no DB, no FastAPI,
  no network. This makes the quant core trivially unit-testable and reusable
  (CLI, notebooks, batch jobs) and keeps the math independent of the web layer.
- `services/` orchestrates: load data via `db`/market-data, call `analytics`,
  persist results.
- `api/` only handles HTTP concerns: validation, auth, serialization, status
  codes. No business logic lives here.

---

## 1.2 Implementation TODO List

> Status legend: `[x]` implemented & tested · `[ ]` roadmap. The Phase 1 + 2
> feature set is fully implemented (backend at 89% test coverage); most of
> Phase 3 is implemented, with the remaining items being operational/infra work.

### Phase 1 — Foundation (high priority)
- [x] Scaffold repo, `docker-compose` (Postgres + Redis + migrate + api + worker + web)
- [x] `core/config.py` settings + `.env` wiring; structured JSON logging
- [x] SQLAlchemy models: `User`, `Asset`, `Portfolio`, `Holding`, `PriceBar`, `SimulationJob`
- [x] Alembic migrations (`0001_initial`, `0002_simulation_jobs`)
- [x] JWT auth (register/login/me), password hashing, `get_current_user` dep
- [x] Portfolio + holdings CRUD endpoints with Pydantic validation
- [x] CI pipeline: ruff + mypy + pytest on every PR
- [x] Health/readiness endpoints (`/healthz`, `/readyz` with DB+Redis checks)

### Phase 2 — Core features (medium priority)
- [x] `analytics/returns.py` — log/simple returns, annualized μ and Σ, shrinkage
- [x] `analytics/risk_metrics.py` — historical & parametric VaR, CVaR, Sharpe,
      Sortino, volatility, max drawdown
- [x] `analytics/mpt.py` — min-variance, max-Sharpe (tangency), efficient frontier
- [x] `analytics/monte_carlo.py` — correlated GBM paths via Cholesky, VaR/ES
- [x] Optimization + risk API endpoints wired to services
- [x] Market-data ingestion service (provider adapter: stub + Alpha Vantage)
- [x] Celery worker + Redis broker; offload Monte Carlo as async jobs (DB-persisted)
- [x] Performance attribution (return + risk contribution by asset)
- [x] Frontend: auth, portfolios/holdings UI, frontier (Plotly), risk panel (D3)
- [x] Typed API client + data hooks (loading/error states)
- [x] Test coverage ≥ 85% on `analytics/`, ≥ 70% overall (achieved: 89% overall)

### Phase 3 — Polish & optimization (lower priority)
- [x] Result caching (Redis) for expensive frontier requests (fail-open)
- [x] Rate limiting (per-IP sliding window middleware)
- [x] Backtesting module (walk-forward rebalancing vs. benchmark)
- [x] Constraint-aware optimization (min/max weight bounds)
- [x] CSV report export (`/portfolios/{id}/report.csv`)
- [ ] WebSocket push for long-running Monte Carlo job progress (roadmap)
- [ ] Observability: OpenTelemetry traces, CloudWatch dashboards, alerts (roadmap)
- [ ] Blue/green (or canary) ECS deploys via CodeDeploy (roadmap)
- [ ] Load testing (Locust) + autoscaling tuning (roadmap)
- [ ] PDF report export; scheduled email summaries (roadmap)
