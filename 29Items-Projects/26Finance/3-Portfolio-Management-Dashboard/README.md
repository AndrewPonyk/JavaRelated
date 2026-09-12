# Portfolio Management Dashboard

A full-stack portfolio optimizer and risk-analytics platform built on **Modern
Portfolio Theory**. It computes the efficient frontier, Value-at-Risk (VaR/CVaR),
Sharpe/Sortino ratios, performance attribution, walk-forward backtests, and
async Monte Carlo risk simulations — with constraint-aware mean-variance
optimization.

| Layer | Tech |
| --- | --- |
| Backend | Python 3.12 · FastAPI · SQLAlchemy 2 · Pandas · NumPy · SciPy |
| Database | PostgreSQL 16 (Alembic migrations) |
| Async compute | Celery · Redis (Monte Carlo jobs) |
| Frontend | TypeScript · React (Vite) · React Router · Plotly.js · D3.js |
| Deploy | Docker · AWS ECS (Fargate) · GitHub Actions |

## Features — what the app can do

**Accounts & security**
- Register / login with JWT (OAuth2 password flow); bcrypt-hashed passwords.
- Per-user data isolation — every portfolio, holding, and job is owner-scoped.

**Assets & market data**
- Maintain an asset catalog (symbol, name, class, currency).
- Load daily price history via a pluggable provider — a deterministic **stub**
  (offline) or **Alpha Vantage** (live) — or generate synthetic history for demos.

**Portfolios & holdings**
- Full CRUD for portfolios and their holdings (add / update / remove positions).
- Portfolio weights are derived from current market values of the holdings.

**Optimization (Modern Portfolio Theory)**
- **Maximum-Sharpe** (tangency) and **minimum-variance** mean-variance optimization.
- **Constraint-aware**: long-only or short-allowed, plus per-asset min/max weight
  caps (with feasibility validation).
- **Efficient frontier** tracing (Redis-cached), highlighting the tangency and
  min-variance portfolios.

**Risk analytics**
- Annualized return & volatility, **Sharpe** and **Sortino** ratios.
- **Value-at-Risk** (historical & parametric) and **CVaR / Expected Shortfall**.
- Maximum drawdown.

**Monte Carlo simulation**
- Correlated multi-asset **Geometric Brownian Motion** (Cholesky factorization),
  run as an **async job** (Celery worker, polled via `/jobs/{id}`).
- Returns horizon VaR/CVaR, probability of loss, expected/median terminal value,
  and percentile (p5/p50/p95) fan-chart bands.

**Performance attribution**
- Per-asset **return contribution** and Euler **risk-contribution** decomposition
  (component contributions sum to portfolio volatility).

**Backtesting**
- **Walk-forward** periodic rebalancing (re-optimized on a trailing window, no
  look-ahead) vs. an equal-weight benchmark, with CAGR / Sharpe / max-drawdown.

**Reporting & dashboard**
- **CSV export** of headline metrics + attribution.
- React SPA: auth, portfolio/holdings management, and a tabbed analytics
  dashboard — **Risk · Frontier · Attribution · Monte Carlo · Backtest** — with
  explicit loading/error states (Plotly + D3 charts).

**Operations**
- Health (`/healthz`) and readiness (`/readyz`, checks DB + Redis) probes.
- Structured JSON logging with per-request IDs, rate limiting, GZip compression,
  security headers, and an independently scalable async worker tier.

## Documentation

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — file structure + phased TODO (status-tracked)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, diagrams, data flow
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deploy, pitfalls

## Quickstart (Docker — full stack)

```bash
docker compose up --build
# Schema is migrated automatically by the `migrate` service before the API starts.

# Seed a demo user + assets (with prices) + a portfolio:
docker compose exec api python -m app.seed
```

- API:  http://localhost:8000  — interactive docs at **/docs**
- Web:  http://localhost:5173
- Demo login (after seeding): **demo@example.com** / **password123**

The stack runs `db`, `redis`, a one-shot `migrate`, the `api`, a Celery `worker`
(processes Monte Carlo jobs), and the `web` SPA.

## Backend (local, without Docker)

```bash
cd backend
pip install -r requirements.txt
pip install -e ".[dev]"        # or: pip install pytest pytest-cov httpx ruff mypy
export DATABASE_URL=postgresql+psycopg://portfolio:portfolio@localhost:5432/portfolio
export SECRET_KEY=$(python -c "import secrets;print(secrets.token_hex(32))")
alembic upgrade head
uvicorn app.main:app --reload
celery -A app.workers.tasks.celery_app worker --loglevel=info   # in a second shell
```

## Frontend (local)

```bash
cd frontend
npm install
npm run dev          # proxies /api -> http://localhost:8000
```

## Tests & quality

```bash
# Backend (unit + integration; 87% coverage)
cd backend && pytest

# Lint + types (what CI enforces)
ruff check . && ruff format --check . && mypy app

# Frontend
cd frontend && npm run lint && npm run typecheck && npm run test
```

## API overview (`/api/v1`)

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register`, `/auth/login` | Create account, obtain JWT |
| GET | `/auth/me` | Current user |
| GET/POST | `/assets`, `/assets/{id}` | Asset catalog |
| POST | `/assets/{id}/seed-prices` | Generate synthetic price history |
| POST | `/assets/{id}/ingest` | Ingest prices via configured provider |
| CRUD | `/portfolios`, `/portfolios/{id}` | Portfolio management |
| CRUD | `/portfolios/{id}/holdings[/{hid}]` | Holdings sub-resource |
| POST | `/portfolios/{id}/optimize` | Max-Sharpe / min-variance (constraint-aware) |
| POST | `/portfolios/{id}/frontier` | Efficient frontier (Redis-cached) |
| GET | `/portfolios/{id}/risk` | VaR/CVaR, Sharpe/Sortino, drawdown |
| GET | `/portfolios/{id}/attribution` | Per-asset return + risk contribution |
| POST | `/portfolios/{id}/backtest` | Walk-forward rebalanced backtest |
| GET | `/portfolios/{id}/report.csv` | CSV export |
| POST | `/portfolios/{id}/monte-carlo` | Dispatch async simulation → `202 {job_id}` |
| GET | `/jobs/{job_id}` | Poll job status/result |

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `No price history available for the requested assets` (400) | The portfolio's assets have no price bars. Seed them: `POST /assets/{id}/seed-prices` (or run `python -m app.seed`). |
| Analytics tab shows an error in the UI | Same as above — add holdings whose assets have seeded prices. |
| Monte Carlo job stays `queued` | The Celery `worker` isn't running, or Redis is unreachable. Check `docker compose ps`; for single-process dev set `CELERY_TASK_ALWAYS_EAGER=true`. |
| `502/Connection refused` from the web container | The API isn't up yet. The `migrate` service must finish first; check `docker compose logs migrate api`. |
| Alembic can't connect on startup | Postgres not ready / wrong `DATABASE_URL`. The compose `migrate` service waits on the DB healthcheck; locally ensure Postgres is running. |
| `max_weight is infeasible for N assets` (400) | A long-only constraint can't sum to 1 (e.g. `max_weight=0.2` with 3 assets). Use `max_weight ≥ 1/N`. |
| `429 Too many requests` | Rate limit hit. Tune `RATE_LIMIT_PER_MINUTE` or set `RATE_LIMIT_ENABLED=false` for local load testing. |
| Redis down but app still serves | Expected — caching is fail-open and Monte Carlo falls back to eager when configured. `/readyz` reports `redis: unavailable`. |

## The quant core

[`backend/app/analytics/`](backend/app/analytics) is **pure** (NumPy/Pandas in,
numbers out) — no I/O, framework, or DB coupling — and is the most heavily
tested part of the system, validated against closed-form solutions and numerical
invariants (e.g. global-min-variance weights, VaR ≤ CVaR, Euler risk
decomposition summing to volatility).
