# Real-Time Trading System

Ultra-low-latency algorithmic trading engine in **C++20**: a FIX-driven order
pipeline (LMAX-style lock-free primitives, memory pools), an L2 order book,
pre-trade risk, a pluggable strategy framework, an event-driven backtester, and
an advisory LSTM quant model served over **gRPC**. Built with CMake/Ninja,
*optional* Docker packaging, CI via **Jenkins**.

> ## 🪟 **Docker is NOT required — runs natively on Windows.**
> **The trading engine is a self-contained native console binary.** Build it with
> CMake + a C++20 compiler and run `trading_engine.exe` **directly on Windows
> (MSVC) — no Docker, no WSL, no Linux.** (Linux/macOS build the exact same way.)
> Docker is an **optional** convenience for packaging the Python ML sidecar and for
> a reproducible Linux CI build — **you can ignore it entirely and lose nothing.**

> ⚠️ The sample `MeanReversionStrategy` is illustrative, **not** a profitable
> strategy (it crosses the spread on purpose to exercise the fill path, so it
> loses money by design). Trading involves substantial risk.

---

## What actually runs

The **default build is fully self-contained** — C++20 standard library only — and
runs the whole system end-to-end, no external services required:

```
tick ─▶ OrderBook ─▶ (ML prediction) ─▶ Strategy ─▶ Risk ─▶ OMS ─▶ FIX ─┐
                                                                        │  (in-process wire)
   Journal ◀─ PnL ◀─ ExecutionReport ◀─ FIX ◀─ SimulatedExchange ◀──────┘
```

- **Verified on Windows (MSVC) and Linux (GCC, via Docker):** 41 unit + integration
  tests pass on both; the `trading_engine` binary logs on over FIX, processes ~18k
  ticks, sends thousands of orders, matches them in an in-process FIX
  `SimulatedExchange`, journals every order/fill/prediction to `var/journal/*.jsonl`,
  and prints a PnL summary — with byte-identical results across the two platforms.
  The `backtest` binary replays the same strategy deterministically. The Python ML
  gRPC service answers `Predict`/`Health` for real.

Heavy **production integrations are optional CMake features** (real adapters, OFF
by default) so the project builds and tests anywhere with just a compiler:

| Feature | Flag | Default backend (always works) |
|---------|------|--------------------------------|
| Exchange transport | `-DRTS_ENABLE_BOOST=ON` (Boost.Asio TCP) | in-process FIX `SimulatedExchange` |
| Persistence | `-DRTS_ENABLE_ORACLE=ON` (Oracle OCCI) | `FileTradeRepository` (JSONL) |
| Quant model | `-DRTS_ENABLE_GRPC=ON` (remote LSTM) | `LocalPredictor` (in-process) |
| Dashboard | `-DRTS_ENABLE_QT=ON` (Qt6) | — |
| Analytics | `-DRTS_ENABLE_TBB=ON` (Intel TBB) | — |

This is exactly the "transport / persistence / advisory-ML seam" described in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): the engine depends on interfaces,
so swapping a real venue/DB/model in is a build flag, not a rewrite.

## Documentation

| Doc | Contents |
|-----|----------|
| [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) | Structure, phased TODO (now checked off), milestones |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Pattern, component interactions, data-flow diagrams, scaling, security |
| [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) | CI/CD, testing, deployment, environments, pitfalls |

## Build & test

### Linux / macOS (GCC 13+ / Clang 17+)

```bash
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release -DRTS_BUILD_TESTS=ON
cmake --build build --parallel
ctest --test-dir build --output-on-failure
# or simply:
ci/scripts/build.sh
```

GoogleTest is used if installed; otherwise a bundled header-only runner
(`tests/shim/`) is used automatically, so tests build with zero extra deps.

### Windows (MSVC / Visual Studio 2022)

A helper loads the VS dev environment + bundled cmake/ninja:

```bat
scripts\build_win.bat cmake -S . -B build -G Ninja -DRTS_BUILD_TESTS=ON
scripts\build_win.bat cmake --build build
scripts\build_win.bat ctest --test-dir build --output-on-failure
```

## Run

> **Platform support:** **Windows (MSVC) is the verified platform** — the engine,
> backtester, and all 41 tests build and run natively (no WSL/Docker needed).
> Linux/macOS build identically (GCC 13+ / Clang 17+); the Docker images target
> Linux for the containerised stack.

### Windows (PowerShell / cmd)

```bat
:: Trading engine: deterministic synthetic feed, journals to var\journal\
.\build\trading_engine.exe config\trading_engine.yaml

:: ...or replay a recorded CSV (exchangeTs,symbolId,side,price,qty):
.\build\trading_engine.exe config\trading_engine.yaml ticks.csv

:: Backtest the strategy (synthetic 8000 moves): PnL + max drawdown
.\build\backtest.exe
```

### Linux / macOS

```bash
./build/trading_engine config/trading_engine.yaml
./build/trading_engine config/trading_engine.yaml ticks.csv
./build/backtest
```

### ML quant service (any platform, separate process)

```bash
cd ml_service
pip install -r requirements.txt
python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/prediction.proto
python server.py          # serves :50051
python smoke_test.py      # asserts Health + Predict end-to-end
```

## Docker (full stack)

```bash
docker compose up --build      # builds images (the engine image runs ctest), starts ml + engine
```

- `ml` — Python LSTM gRPC service (long-running, health-checked).
- `engine` — builds & **runs the test suite during `docker build`**, then runs the
  simulation and writes the journal to `./var`.
- `--profile oracle` additionally starts Oracle XE for the production persistence
  backend.

## Repository layout

```
src/        core (lock-free, pools, time, clock) · common · fix · marketdata
            oms · risk · strategy · ml · persistence · exchange · engine · network · ui
apps/       trading_engine · backtest · dashboard (Qt, optional)
ml_service/ Python LSTM gRPC service (+ smoke test)
proto/      gRPC/protobuf contract
tests/      unit · integration · benchmark · shim (bundled GTest-compatible runner)
db/         Oracle DDL + Flyway migrations
config/     engine + FIX session config
ci/         Jenkinsfile + build/deploy scripts
docker-compose.yml · Dockerfile · ml_service/Dockerfile
```

## Interfaces / API

- **gRPC (ML service):** contract is [`proto/prediction.proto`](proto/prediction.proto)
  — `QuantPredictor.Predict`, `Health`, `PredictStream`. `Health` backs the
  compose health check. Smoke-test: `python ml_service/smoke_test.py`.
- **FIX 4.4 (engine ↔ venue):** `NewOrderSingle`, `ExecutionReport`, and the
  session admin set (Logon/Heartbeat/TestRequest/Logout) in `src/fix/`.

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `cmake`/`cl` not found (Windows) | Use `scripts\build_win.bat <cmd>` — it loads the VS2022 env + bundled cmake/ninja. |
| `engine failed to log on to exchange` | Wire both `setOrderWire` **and** `setWire` before `connect()`. |
| Engine runs but `orders=0` | Feed not volatile enough to cross the strategy threshold — pass more `steps` or a CSV with wider moves. |
| `ModuleNotFoundError: prediction_pb2` | Generate stubs: `python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/prediction.proto` (the Docker image does this automatically). |
| ML `Predict` → `FAILED_PRECONDITION` | Client `model_version` ≠ server `MODEL_VERSION`; align `ML_MODEL_VERSION`. |
| `docker compose up` fails on `engine` | The engine image runs the test suite during `docker build` — inspect the build log for a failing test. |
| "GoogleTest not found" at configure | Expected — the bundled `tests/shim` runner is used automatically; tests still build and run. |
| Journal not written | The process needs a writable CWD; `var/journal/` is created relative to where the binary runs. |

## Notes on scope

The default build, the simulation, the backtester, the FIX codec, and the ML
service are **fully implemented and tested**. The optional adapters
(`src/network/` Boost.Asio, `src/persistence/OracleConnection` OCCI,
`src/ui/` Qt, the gRPC wire in `ml/PredictionClient`) are real interface
scaffolding for SDKs that are licensed/heavy and not part of the self-contained
build; each is wired behind its `RTS_ENABLE_*` flag and documented at its call
site. The in-process `SimulatedExchange` speaks the same FIX codec a real venue
would, so the optional TCP transport is a drop-in.
