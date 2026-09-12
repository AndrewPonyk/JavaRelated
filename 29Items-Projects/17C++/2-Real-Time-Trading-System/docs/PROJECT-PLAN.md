# Real-Time Trading System — Project Plan

> Ultra-low-latency algorithmic trading platform. C++20 hot path, FIX exchange
> connectivity, LMAX-style lock-free pipeline, Intel TBB for parallel analytics,
> Qt operator dashboard, Oracle for the system of record, and an LSTM quant model
> served over gRPC. Deployed on bare metal, built and shipped via Jenkins.

---

## 1. Document Map

| Document | Purpose |
|----------|---------|
| `docs/PROJECT-PLAN.md` | This file — structure, scope, phased TODO list |
| `docs/ARCHITECTURE.md` | Architectural pattern, component interactions, data flow, scaling, security |
| `docs/TECH-NOTES.md` | CI/CD, testing, deployment, environments, branching, pitfalls |

---

## 2. Design Goals & Non-Goals

**Goals**

- **Deterministic low latency.** Tick-to-trade p99 budget of **≤ 5 µs** inside the
  process boundary (parse → strategy → risk → encode), excluding NIC/wire time.
- **Zero allocation on the hot path.** All steady-state work uses pre-allocated
  memory pools and lock-free queues. `new`/`delete`/`malloc` are forbidden after
  warm-up on threads pinned to trading cores.
- **Mechanical sympathy.** Cache-line awareness, NUMA-local data, busy-spin on
  isolated cores, kernel-bypass-ready network abstraction.
- **Correctness under contention.** Single-writer principle per sequence; risk
  checks are mandatory and synchronous before any order leaves the building.
- **Operational visibility.** Every order, fill, and risk decision is observable
  in the Qt dashboard and durably journaled.

**Non-Goals**

- Not a multi-tenant SaaS. Single firm, co-located deployment.
- The hot path is **not** a microservice mesh — network hops are the enemy.
- ML inference is **advisory**, off the critical path, and must never block a trade.

---

## 3. Project File Structure

```text
2-Real-Time-Trading-System/
├── docs/                              # Architecture & engineering docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── src/                               # C++20 source (the trading engine)
│   ├── core/                          # Latency-critical primitives (header-only)
│   │   ├── lockfree/
│   │   │   ├── SPSCQueue.hpp          # Single-producer/single-consumer ring
│   │   │   └── Disruptor.hpp          # LMAX-style multi-consumer pipeline
│   │   ├── memory/
│   │   │   ├── MemoryPool.hpp         # Fixed-block arena allocator
│   │   │   └── ObjectPool.hpp         # Typed, recyclable object pool
│   │   ├── time/
│   │   │   └── Clock.hpp              # TSC + steady clock, ns timestamps
│   │   └── concurrency/
│   │       └── ThreadAffinity.hpp     # Core pinning / NUMA helpers
│   │
│   ├── common/                        # Shared types & infra
│   │   ├── Types.hpp                  # Price, Qty, Symbol, ids, enums
│   │   ├── Logger.hpp                 # Async, lock-free logging façade
│   │   └── Config.hpp                 # Typed config loaded from YAML
│   │
│   ├── fix/                           # FIX 4.4/5.0 engine
│   │   ├── FixMessage.hpp             # Tag/value field container
│   │   ├── FixParser.hpp             # Zero-copy inbound parser
│   │   ├── FixSession.hpp            # Session state machine (logon/seq/heartbeat)
│   │   └── FixSession.cpp
│   │
│   ├── network/                       # Boost.Asio transport
│   │   ├── OrderGateway.hpp           # Outbound order session to exchange
│   │   └── MarketDataFeed.hpp         # Inbound multicast/TCP feed handler
│   │
│   ├── marketdata/                    # Book building
│   │   ├── Tick.hpp                   # POD market data event
│   │   └── OrderBook.hpp / .cpp       # L2 limit order book
│   │
│   ├── oms/                           # Order Management System
│   │   ├── Order.hpp                  # Order + ExecutionReport POD
│   │   └── OrderManager.hpp / .cpp    # Lifecycle, state, idempotency
│   │
│   ├── risk/                          # Pre-trade risk
│   │   └── RiskEngine.hpp / .cpp      # Synchronous limit checks (fat-finger, pos)
│   │
│   ├── strategy/                      # Alpha logic
│   │   ├── IStrategy.hpp              # Strategy interface (event callbacks)
│   │   ├── StrategyEngine.hpp         # Dispatcher / scheduler
│   │   └── strategies/
│   │       └── MeanReversionStrategy.hpp
│   │
│   ├── backtest/                      # Historical simulation
│   │   └── BacktestEngine.hpp / .cpp  # Event-driven replay harness
│   │
│   ├── ml/                            # Quant model client
│   │   └── PredictionClient.hpp / .cpp# Async gRPC client to LSTM service
│   │
│   ├── persistence/                   # Oracle system-of-record
│   │   ├── OracleConnection.hpp       # OCCI/ODPI connection pool wrapper
│   │   └── TradeRepository.hpp        # DAO for trades/orders/positions
│   │
│   └── ui/                            # Qt dashboard widgets
│       ├── MainWindow.hpp / .cpp
│       └── (OrderBookWidget, BlotterWidget — TODO)
│
├── apps/                              # Executable entry points
│   ├── trading_engine/main.cpp        # The engine daemon
│   └── dashboard/main.cpp             # The Qt operator app
│
├── ml_service/                        # Python LSTM inference microservice
│   ├── server.py                      # gRPC server
│   ├── model/lstm_model.py            # Keras/PyTorch LSTM wrapper
│   ├── proto/prediction.proto         # (shared with C++ client)
│   └── requirements.txt
│
├── proto/                             # gRPC/protobuf IDL (C++ side)
│   └── prediction.proto
│
├── tests/                             # Test suites
│   ├── unit/                          # GoogleTest unit tests
│   │   ├── test_spsc_queue.cpp
│   │   └── test_order_book.cpp
│   ├── integration/                   # End-to-end order-flow tests
│   │   └── test_order_flow.cpp
│   └── benchmark/                     # Google Benchmark microbenchmarks
│       └── bench_lockfree.cpp
│
├── db/                                # Oracle DDL & migrations
│   ├── schema.sql
│   └── migrations/
│       ├── V001__initial_schema.sql
│       └── V002__add_ml_predictions.sql
│
├── config/                            # Runtime configuration
│   ├── trading_engine.yaml
│   └── fix_session.cfg
│
├── ci/                                # CI/CD assets
│   ├── Jenkinsfile                    # Declarative pipeline
│   └── scripts/
│       ├── build.sh
│       └── deploy.sh
│
├── cmake/                             # CMake helper modules
│   └── FindOracle.cmake
│
├── scripts/                           # Dev/ops helper scripts
│   └── tune_host.sh                   # CPU isolation, hugepages, IRQ affinity
│
├── CMakeLists.txt                     # Top-level build
├── conanfile.txt                      # Third-party dependency manifest
├── .clang-format                      # Code style
├── .clang-tidy                        # Static analysis ruleset
├── .gitignore
├── .env.example                       # Environment variable template
└── README.md
```

### 3.1 Layering rules (enforced at review time)

```
ui ─┐
    ├─► oms ─► risk ─► network/fix ─► (exchange)
strategy ─► oms
strategy ◄─ marketdata ◄─ network/fix
ml (advisory) ─► strategy
persistence ◄─ oms / risk (async, off hot path)
core/* and common/* : depended upon by everyone, depend on nobody
```

- **`core/` and `common/` never depend upward.** They are the foundation.
- **Strategy never talks to the network directly** — it emits intents to the OMS.
- **Nothing on the hot path calls into `persistence/` or `ml/` synchronously.**

---

## 4. Implementation TODO List

> **Status:** Phase 1 and the core of Phase 2 are **implemented and verified**
> (33 passing unit/integration tests; the engine and backtester run end-to-end;
> the ML gRPC service answers `Predict`/`Health`). The default build is
> dependency-free; items needing licensed/heavy SDKs (Boost, Oracle, Qt, gRPC
> wire) are built behind `RTS_ENABLE_*` flags with a working default backend.

### Phase 1 — Foundation (High Priority)

- [x] **Build system**: top-level `CMakeLists.txt` (CMake/Ninja, C++20).
      Default is dependency-free; optional SDKs behind `RTS_ENABLE_*`. *(Conan
      manifest retained for the optional-feature builds.)*
- [x] **Core primitives**: `SPSCQueue`, `Disruptor`, `MemoryPool`, `ObjectPool`,
      `Clock` (TSC), `ThreadAffinity`. SPSC unit-tested + benchmark target.
- [x] **Common types**: fixed-point `Price`/`Qty`, strong-typedef ids, enums.
- [x] **Logging**: async ring-buffer logger; hot path only enqueues.
- [x] **Config**: YAML-subset loader → typed `EngineConfig` + env overrides + validation.
- [x] **FIX engine**: message view, zero-copy parser **with checksum/framing**,
      `FixBuilder`, `FixCodec`, session state machine (logon, seq nums, heartbeat,
      test request, gap detection, persisted sequences).
- [~] **Boost.Asio transport**: `OrderGateway`/`MarketDataFeed` interfaces present;
      real TCP impl behind `RTS_ENABLE_BOOST`. Default uses the in-process wire.
- [x] **CI**: Jenkinsfile (lint → build → test → smoke → ML → docker → deploy);
      `ci/scripts/build.sh`.

### Phase 2 — Core Features (Medium Priority)

- [x] **Order book**: L2 build, BBO, depth, top-of-book change signalling. *(tested)*
- [x] **OMS**: lifecycle + explicit state machine, ClOrdID↔OrderId mapping via FIX,
      illegal-transition rejection, terminal retirement. *(tested)*
- [x] **Risk engine**: synchronous max-order / max-position / max-notional /
      price-collar checks + kill-switch. *(tested)*
- [x] **Strategy framework**: `IStrategy` callbacks, `StrategyEngine` dispatch,
      sample `MeanReversionStrategy` (folds in the advisory ML signal).
- [x] **Persistence**: `ITradeRepository` + `FileTradeRepository` (JSONL audit
      trail). Oracle OCCI async adapter + Flyway migrations behind `RTS_ENABLE_ORACLE`.
- [x] **ML client/predictor**: `IPredictor` + `LocalPredictor`; `PredictionClient`
      with circuit breaker + fallback (gRPC wire behind `RTS_ENABLE_GRPC`). Advisory,
      never blocks.
- [x] **ML service**: Python gRPC server, batched inference, model versioning,
      analytic-momentum default model, ONNX artifact path, smoke test.
- [x] **Backtest engine**: deterministic event replay reusing the book + strategy
      code; PnL + max-drawdown. *(tested for determinism)*
- [x] **Simulated exchange + integration tests**: in-process FIX acceptor matches
      orders at the touch; full tick→fill→journal round-trip covered.
- [~] **Qt dashboard**: widgets + control wiring scaffolded; built with `RTS_ENABLE_QT`.

### Phase 3 — Polish & Optimization (Lower Priority)

- [ ] **Latency hardening**: huge pages, NUMA pinning, IRQ affinity, `isolcpus`,
      busy-poll, `mlockall`, false-sharing audit. *(host tuning in `scripts/tune_host.sh`)*
- [ ] **Kernel bypass**: pluggable transport for Solarflare/ef_vi or DPDK.
- [ ] **TBB analytics**: parallel post-trade analytics, VWAP/TWAP, risk aggregation.
- [ ] **Observability**: latency histograms (HdrHistogram), per-stage timestamps,
      hardware perf counters, structured journals → time-series store.
- [ ] **Resilience**: warm/hot standby engine, sequence-number recovery, failover,
      drop-copy reconciliation.
- [ ] **Capacity & soak**: 24h soak tests, market-replay storms, feed chaos.
- [ ] **Compliance**: order audit trail (CAT/MiFID-style), clock sync (PTP), reports.

*Legend: `[x]` done & verified · `[~]` interface/scaffold present, full impl behind an optional flag · `[ ]` future work.*

---

## 5. Milestones & Definition of Done

| Milestone | Exit Criteria |
|-----------|---------------|
| **M1 — Plumbing** | Engine connects to a FIX simulator, logs on, sends/cancels a single order, persists it to Oracle. |
| **M2 — Live book** | Book builds from a recorded feed; BBO drives a strategy that emits risk-checked orders. |
| **M3 — Backtest parity** | A strategy produces identical decisions in backtest and live-replay; PnL reconciles. |
| **M4 — Quant loop** | LSTM predictions flow into a strategy; advisory signal demonstrably shifts behavior; never stalls the hot path. |
| **M5 — Production hardening** | p99 tick-to-trade ≤ 5 µs on tuned hardware; 24h soak with zero hot-path allocations; failover verified. |

---

## 6. Risk Register (project-level)

| Risk | Impact | Mitigation |
|------|--------|------------|
| GC-style latency spikes from allocation | Missed trades | Pools + `mlockall` + no-alloc audit in CI |
| FIX session desync (seq gaps) | Rejected orders, halts | Robust resend/gap-fill; persisted seq nums |
| ML service latency/outage | Stale signals | Advisory-only; circuit breaker; last-good cache |
| Oracle write back-pressure | Journaling falls behind | Async, batched writes off the hot path; bounded queue with spill |
| Operator error (fat finger) | Large loss | Mandatory pre-trade risk + dashboard kill-switch |
| Clock skew across hosts | Bad audit/ordering | PTP/PPS sync; TSC calibration on boot |
