# Algorithmic Trading Platform — Project Plan

> **Status:** Architecture / Scaffolding
> **Owner:** Platform Engineering
> **Last updated:** 2026-06-29

A low-latency, event-driven platform for **research → backtest → paper → live** algorithmic
trading. Strategies are authored in Python (asyncio), validated against Zipline backtests,
and routed to market through a C++ execution engine speaking FIX 4.4 to brokers. Market data,
signals, and order events flow over Kafka; PostgreSQL is the system of record; Redis holds
hot state (latest quotes, positions, rate limits).

---

## 1. Guiding Principles

| Principle | Implication |
|-----------|-------------|
| **Backtest/live parity** | The same `Strategy` interface and indicator code path runs in Zipline and in production. No "research-only" forks of signal logic. |
| **Latency budget is sacred** | The order hot-path (signal → risk check → FIX out) lives in C++ and never blocks on Python, Postgres, or disk. Everything else is "control plane". |
| **Everything is an event** | Market ticks, signals, orders, fills, and risk decisions are immutable events on Kafka. State is a projection of the event log. |
| **Fail closed** | On ambiguity (stale data, risk-engine timeout, broker disconnect) the system halts trading rather than guessing. Money asymmetry > availability. |
| **Deterministic & replayable** | Any trading session can be replayed tick-for-tick from Kafka for debugging and post-mortems. |

---

## 2. Project File Structure

```text
2-Algorithmic-Trading-Platform/
├── docs/                          # Architecture & engineering docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── services/                      # Python control-plane microservices (asyncio)
│   ├── market-data/               #  Ingest broker/exchange feeds → normalize → Kafka
│   │   └── src/market_data/
│   ├── strategy-engine/           #  Hosts running strategies, emits signals
│   │   └── src/strategy_engine/
│   │       └── strategies/        #   Concrete strategies (momentum, mean-revert, ML)
│   ├── backtesting/               #  Zipline harness; backtest/live parity layer
│   │   └── src/backtesting/
│   ├── ml-models/                 #  Transformer price-prediction training + serving
│   │   └── src/ml_models/
│   │       └── transformers/
│   ├── risk-engine/               #  Pre-trade limits, kill-switch, exposure caps
│   │   └── src/risk_engine/
│   └── api-gateway/               #  FastAPI REST + WebSocket BFF for the frontend
│       └── src/api_gateway/
│           ├── routers/           #   HTTP route handlers (thin)
│           ├── services/          #   Business logic / orchestration
│           └── schemas/           #   Pydantic request/response models
│
├── execution-engine/             # C++17 low-latency order router + FIX engine
│   ├── include/execution/        #  Public headers
│   ├── src/                      #  Implementation
│   ├── tests/                    #  GoogleTest unit tests
│   └── CMakeLists.txt
│
├── shared/                       # Installable Python lib reused by all services
│   └── trading_common/
│       ├── indicators/           #  TA-Lib wrappers (single signal code path)
│       ├── models/               #  Shared Pydantic domain models
│       └── utils/                #  Structured logging, config, time
│
├── frontend/                     # React 18 + TypeScript trader dashboard (Vite)
│   ├── public/
│   └── src/
│       ├── components/           #  Presentational + container components
│       ├── hooks/                #  Data-fetching / WebSocket hooks
│       ├── services/             #  API client
│       ├── types/                #  Shared TS types (mirror Pydantic schemas)
│       └── pages/
│
├── db/                           # PostgreSQL schema as source of truth
│   ├── migrations/               #  Flyway-style versioned SQL migrations
│   └── schema/                   #  ERD / reference docs
│
├── infra/                        # Deployment for bare-metal + Jenkins CI/CD
│   ├── jenkins/                  #  Pipeline helper scripts (Jenkinsfile is at root)
│   ├── ansible/                  #  Bare-metal provisioning playbooks
│   ├── docker/                   #  docker-compose for LOCAL dev infra only
│   └── systemd/                  #  Production unit files (services run as systemd)
│
├── config/                       # Per-environment configuration
│   ├── dev/
│   ├── staging/
│   └── prod/
│
├── scripts/                      # Developer bootstrap / ops helpers
│
├── Jenkinsfile                   # CI/CD pipeline definition (root = Jenkins default)
├── pyproject.toml                # Python tooling: ruff, black, mypy, pytest
├── .pre-commit-config.yaml       # Local quality gate
├── .editorconfig
├── .env.example                  # Documented environment template
├── .gitignore
└── README.md
```

### 2.1 Why this layout

- **`services/` vs `execution-engine/`** — a hard boundary between the Python *control plane*
  (research velocity, rich libraries) and the C++ *data plane* (deterministic latency). They
  communicate only through Kafka and a small shared-memory/IPC command channel, never through
  in-process calls.
- **`shared/trading_common`** is a real installable package (`pip install -e shared`). Indicator
  math lives here exactly once so a backtest and a live strategy compute RSI identically.
- **`db/` is the source of truth for schema**, not an ORM's autogenerated migrations. Finance
  audits demand reviewable, hand-checked DDL.
- **`config/<env>/`** holds non-secret config; secrets come from Vault/env at runtime (never
  committed). `.env.example` documents the contract.

---

## 3. Implementation TODO List

### Phase 1 — Foundation (High Priority) 🔴 — ✅ complete

- [x] Local infra: `docker-compose` with PostgreSQL, Redis, Kafka, Schema Registry (`infra/docker`).
- [x] `db/migrations` baseline (instruments, accounts, orders, fills, strategies, signals, positions) + idempotent runner (`scripts/migrate.py`).
- [x] `shared/trading_common` (domain models, indicators, persistence, messaging, structured logging, config loader).
- [x] Canonical topics + JSON schemas (`trading_common.messaging.topics`; models are the schema).
- [x] `market-data`: simulated feed → normalize → tick/bar publish to the bus.
- [x] `strategy-engine`: asyncio host loading strategies (DB registry), consuming bars, emitting `signals`.
- [x] C++ `execution-engine`: order router (idempotent, fail-closed) + QuickFIX session interface + GoogleTest. *(Live path; paper mode uses the Python paper-broker.)*
- [x] `risk-engine`: pre-trade check (max position, notional cap, daily loss, kill-switch) as a bus service.
- [x] CI: `Jenkinsfile` with parallel Python / C++ / frontend lint + test lanes.

### Phase 2 — Core Features (Medium Priority) 🟡 — ✅ mostly complete

- [x] `backtesting`: event-driven `SimpleBacktester` sharing the **same** `Strategy`/indicator/fill code; parity test enforced. *(Zipline retained as an optional heavy backend.)*
- [x] Indicators in `trading_common/indicators` (SMA/EMA/RSI/ATR/MACD/Bollinger) with property-based tests. *(Pure NumPy by default; TA-Lib via `TRADING_USE_TALIB=1`.)*
- [x] `api-gateway` (FastAPI): strategy CRUD, positions/orders/PnL endpoints, WebSocket PnL feed, OpenAPI, RBAC.
- [x] Frontend: strategy dashboard + create form, live PnL, positions table, kill-switch.
- [x] `ml-models`: NumPy momentum predictor + inference service publishing `predictions`. *(torch Transformer defined for offline training.)*
- [x] Position & PnL projector: bus consumer folding `fills` into Postgres (+ Redis store).
- [x] Paper-trading mode: orders route to the simulated matching engine (`PaperBroker`) end-to-end.
- [ ] Observability: Prometheus metrics, Grafana dashboards, OpenTelemetry traces *(structured logging in place; metrics/traces are next)*.

### Phase 3 — Polish & Optimization (Lower Priority) 🟢

- [ ] Latency hardening: pin C++ threads to cores, busy-poll FIX socket, kernel-bypass (Solarflare/DPDK) evaluation.
- [ ] Strategy hot-reload without restarting the engine.
- [ ] Multi-broker smart order routing (SOR) with venue cost model.
- [ ] Backtest result store + research notebook integration (Jupyter against the data lake).
- [ ] Canary / blue-green strategy rollout with automated rollback on drawdown.
- [ ] Chaos drills: broker disconnect, Kafka lag, stale-data halt, clock skew.
- [ ] Compliance reporting exports (trade blotter, best-execution, MiFID/SEC audit trails).

---

## 4. Milestones

| Milestone | Definition of Done |
|-----------|--------------------|
| **M1 — Tick to log** | Sim feed → Kafka → strategy logs a signal. End-to-end wiring proven. |
| **M2 — Paper loop** | Signal → risk → simulated fill → PnL projected to dashboard. |
| **M3 — Backtest parity** | A strategy backtested in Zipline and paper-traded produce identical signals on the same data. |
| **M4 — Live (1 lot)** | Real broker, real money, hard risk caps, kill-switch, full audit trail. |
| **M5 — ML in the loop** | Transformer prediction consumed by a live strategy with measured edge. |

---

## 5. Team & Ownership (RACI sketch)

| Area | Primary | Notes |
|------|---------|-------|
| Execution engine (C++/FIX) | Low-latency team | Owns the hot path & latency budget |
| Strategies / ML | Quant research | Owns alpha; must respect the `Strategy` contract |
| Control plane (Python services) | Platform | Owns Kafka topology, schemas, deploy |
| Frontend | App team | Trader UX, kill-switch ergonomics |
| Risk & compliance | Risk | Sign-off required before M4 (live) |
