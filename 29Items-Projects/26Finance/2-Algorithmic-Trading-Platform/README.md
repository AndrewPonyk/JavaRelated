# Algorithmic Trading Platform

A low-latency, event-driven platform for **research → backtest → paper → live** algorithmic trading.

- **Control plane** — Python / asyncio microservices that talk over a pluggable message bus
  (in-process for dev/tests, **Kafka** for production).
- **Data plane** — C++17 execution engine speaking **FIX 4.4** to brokers (the latency-critical
  hot-path); a Python **paper broker** stands in for paper/backtest modes.
- **State** — **PostgreSQL** system of record (SQLite for dev/tests), **Redis** hot positions.
- **Research** — an event-driven backtester sharing the **exact same** `Strategy` and indicator
  code as live trading (backtest/live parity). Zipline is an optional heavy backend.
- **ML** — a NumPy momentum predictor served over the bus, with a torch Transformer as the
  offline-trained alternative.
- **UI** — React + TypeScript dashboard: strategy CRUD, live PnL, positions, kill-switch.

> ⚠️ **Risk warning:** This software can place real orders. Never run against a live account
> without hard risk limits, a tested kill-switch, and Risk sign-off. See `docs/ARCHITECTURE.md` §2.5–2.6.

## What works today

The **entire Python control plane is implemented and tested** (102 tests, ~94% coverage), including a
real **end-to-end paper-trading pipeline**:

```
market-data ─ticks/bars─▶ strategy-engine ─signals─▶ risk-engine ─orders─▶ paper-broker
     │                                                                          │
     └───────────────── bars (marks) ──────────────────┐                     fills
                                                        ▼                       ▼
                                       api-gateway ◀── PostgreSQL ◀── pnl-projector
```

A single `correlation_id` is propagated from the originating bar through signal → order → fill →
position, so any fill is traceable back to the tick that caused it.

## Quickstart

### Run the tests (no external services needed)

```bash
make test          # or: python -m pytest
```

Uses in-memory SQLite + the in-process bus, so the full stack — including an end-to-end
integration test — runs with zero infrastructure.

### Run a paper-trading session locally (SQLite)

```bash
make install       # pip install -e "shared[dev]" (brings in trading_common's deps)
make run-paper     # python -m orchestrator.main  → trades, persists positions/PnL to SQLite
```

### Run the API and explore the OpenAPI docs

```bash
make run-api       # uvicorn api_gateway.asgi:app --port 8000  (SQLite by default)
# open http://localhost:8000/docs
```

### Full stack with Docker (Postgres + API + paper trader + UI)

```bash
docker compose up --build
# UI:   http://localhost:5173
# API:  http://localhost:8000/docs
```

`postgres → migrate → api-gateway + orchestrator → frontend`. The orchestrator runs the control
plane in one process and persists positions the API/UI then serve.

### Frontend dev

```bash
make frontend-install   # npm install
make frontend-dev       # vite dev server on :5173 (proxies /api to :8000)
make frontend-test      # vitest
```

## Documentation

| Doc | Purpose |
|-----|---------|
| [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) | File structure, phased TODO (status), milestones |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Pattern, components, data flow, scaling, security |
| [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) | CI/CD, testing, deploy, env, git workflow, pitfalls |

## Repository layout

```
docs/              architecture & engineering docs
shared/            trading_common: domain models, indicators, persistence (SQLAlchemy),
                   messaging bus (in-memory + Kafka), portfolio/PnL, paper matching engine
services/
  market-data/     simulated feed + tick→bar aggregator → bus
  strategy-engine/ asyncio host + Strategy contract + EMA/Bollinger strategies + DB registry
  risk-engine/     pre-trade limits + bus service (signals → orders / rejections), fail-closed
  pnl-projector/   folds fills into positions/PnL (Postgres + Redis)
  ml-models/       NumPy momentum predictor + inference service (torch Transformer optional)
  backtesting/     SimpleBacktester (parity) + metrics (Zipline optional)
  api-gateway/     FastAPI: strategy CRUD, positions/orders/PnL, WebSocket PnL feed, OpenAPI
  orchestrator/    single-process paper-trading composition of all services
execution-engine/  C++17 low-latency order router + FIX engine (live path) + GoogleTest
frontend/          React + TypeScript dashboard (Vite)
db/migrations/     forward-only PostgreSQL DDL (source of truth)
infra/             Dockerfile, Jenkins, Ansible, systemd, dev docker-compose
config/            per-environment non-secret config
scripts/           migrate.py, bootstrap.sh
```

## Testing

```bash
python -m pytest                 # full suite + coverage (gate: 70%, actual ~94%)
python -m pytest -m integration  # end-to-end paper-pipeline test
python -m pytest -m parity       # backtest⇄live signal parity
```

## API reference

Full interactive docs (OpenAPI/Swagger) at **`/docs`**; raw schema at `/openapi.json`.

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/health` | — | Liveness probe |
| GET | `/health/ready` | — | Readiness probe (checks DB) |
| GET | `/api/v1/strategies` | viewer | List strategies |
| GET | `/api/v1/strategies/{id}` | viewer | Get one strategy |
| POST | `/api/v1/strategies` | trader | Create a strategy |
| PATCH | `/api/v1/strategies/{id}` | trader | Update / transition state |
| POST | `/api/v1/strategies/{id}/halt` | trader | Kill-switch → HALTED |
| DELETE | `/api/v1/strategies/{id}` | admin | Delete (not while LIVE) |
| GET | `/api/v1/positions` | viewer | Current positions |
| GET | `/api/v1/orders?limit=&offset=` | viewer | Order blotter (paginated, limit ≤ 1000) |
| GET | `/api/v1/pnl` | viewer | Aggregate PnL |
| WS | `/api/v1/ws/pnl` | viewer | Live PnL stream (~1 Hz) |

```bash
# Create a strategy (auth disabled in dev)
curl -X POST localhost:8000/api/v1/strategies -H 'Content-Type: application/json' -d '{
  "name": "EMA Crossover",
  "class": "strategy_engine.strategies.momentum.EmaCrossoverStrategy",
  "symbols": ["AAPL", "MSFT"],
  "params": {"fast": 12, "slow": 26},
  "max_position_qty": 1000,
  "max_order_notional": 250000
}'

curl localhost:8000/api/v1/pnl
```

**Auth:** set `AUTH_ENABLED=true` and `JWT_SECRET=...` to require a Bearer JWT. The token's
`role` claim (`viewer` < `trader` < `admin`) drives RBAC. Signature, expiry, and (optional)
audience are verified. In non-dev environments the gateway refuses to start with the default secret.

## Implementation notes

- **Indicators** are pure NumPy by default (run anywhere); set `TRADING_USE_TALIB=1` to route
  through native TA-Lib. Same import surface either way (backtest/live parity preserved).
- **Backtesting** defaults to the pure-Python `SimpleBacktester`; the Zipline backend is optional.
- **Messaging** is the in-process bus by default; set `KAFKA_BOOTSTRAP_SERVERS` to use Kafka.
- **DB** is SQLite for dev/tests and PostgreSQL (asyncpg) in production, via one SQLAlchemy layer.
- The **C++ execution engine** and **torch Transformer** are the heavy production/research paths;
  the platform runs and is fully tested without them.

## Troubleshooting

| Symptom | Cause / Fix |
|---------|-------------|
| `make test` can't import `trading_common` / a service | Run from the repo root; pytest sets `pythonpath`. For app runs use `make` targets (they export `PYTHONPATH`) or `pip install -e "shared[dev]"`. |
| `401 invalid token` / `token expired` | `AUTH_ENABLED=true` but the JWT is unsigned with `JWT_SECRET`, malformed, or past `exp`. Mint a token signed with the same `JWT_SECRET` (HS256) and a future `exp`. |
| Gateway refuses to start: "JWT_SECRET must be set…" | `AUTH_ENABLED=true` in a non-dev env with the default secret. Set a strong `JWT_SECRET` (≥ 32 bytes). |
| `/health/ready` returns 503 | Database unreachable. Check `DATABASE_URL` and that Postgres is up / migrations applied. |
| `docker compose up` fails on `migrate` | Postgres not healthy yet (compose waits) or bad `DATABASE_URL`. Re-run; `migrate` is idempotent (tracks `schema_migrations`). |
| `ImportError: TA-Lib …` | You set `TRADING_USE_TALIB=1` without the native lib. Unset it to use the NumPy path. |
| Zipline/torch import errors | Optional heavy backends — not needed for the default `SimpleBacktester` / NumPy predictor. |
| Frontend can't reach the API in dev | Start the gateway on `:8000`; Vite proxies `/api` (see `vite.config.ts`). |

## License

Proprietary — internal use only.
