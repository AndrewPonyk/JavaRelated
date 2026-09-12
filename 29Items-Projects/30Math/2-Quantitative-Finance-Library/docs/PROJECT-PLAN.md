# Quantitative Finance Library — Project Plan

**Package name:** `quantfinlib` (PyPI) / `qfcore` (C++ core + pybind11 extension)
**Stack:** C++17 core → pybind11 bindings → Python (NumPy/SciPy/Pandas) → FastAPI service
**Distribution:** PyPI (binary wheels via cibuildwheel), GitHub Actions CI/CD

---

## 1.1 Project File Structure

The repository is a **single monorepo** containing three deployable/publishable units that share
one version number:

1. **`qfcore`** — C++17 numerical core (Black-Scholes closed form, Monte Carlo engine, VaR),
   exposed to Python via pybind11.
2. **`quantfinlib`** — the Python library published to PyPI. Pure-Python orchestration,
   NumPy/SciPy/Pandas ergonomics, ML volatility-surface fitting. Falls back to pure-Python
   reference implementations when the compiled extension is unavailable.
3. **`quantfinlib-api`** — a FastAPI service wrapping the library for HTTP consumers
   (internal dashboards, notebooks, downstream services). Deployed as a Docker container;
   **not** published to PyPI.

```
2-Quantitative-Finance-Library/
├── Claude-Fable-5.txt              # model marker file (per project brief)
├── README.md
├── LICENSE
├── pyproject.toml                  # scikit-build-core + pybind11 build; ruff/pytest/mypy config
├── CMakeLists.txt                  # top-level CMake — delegates to cpp/
├── .gitignore
├── .clang-format                   # C++ formatting
├── .pre-commit-config.yaml         # ruff, clang-format, mypy hooks
├── .env.example                    # template for API/service configuration
│
├── docs/
│   ├── PROJECT-PLAN.md             # ← this file
│   ├── ARCHITECTURE.md             # architecture + Mermaid diagrams
│   ├── TECH-NOTES.md               # CI/CD, testing, deployment, env management
│   └── adr/                        # Architecture Decision Records (one file per decision)
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                  # lint → build → unit/integration tests (Linux/macOS/Windows)
│   │   ├── release.yml             # tag-triggered: cibuildwheel → sdist → PyPI (trusted publishing)
│   │   └── docker.yml              # build & push API container image on release
│   ├── ISSUE_TEMPLATE/
│   │   └── bug_report.md
│   └── dependabot.yml
│
├── cpp/                            # C++ CORE (no Python knowledge except bindings/)
│   ├── CMakeLists.txt
│   ├── include/qfcore/             # public headers (installed)
│   │   ├── exceptions.hpp          # convergence_error + shared input guard
│   │   ├── black_scholes.hpp       # closed-form pricing + Greeks + implied vol
│   │   ├── monte_carlo.hpp         # MC engine (European/Asian/barrier, OpenMP)
│   │   ├── american.hpp            # CRR binomial lattice
│   │   └── var.hpp                 # historical / parametric VaR & Expected Shortfall
│   ├── src/
│   │   ├── black_scholes.cpp
│   │   ├── monte_carlo.cpp
│   │   ├── american.cpp
│   │   └── var.cpp
│   ├── bindings/
│   │   └── bindings.cpp            # pybind11 module `quantfinlib._qfcore`
│   └── tests/
│       ├── CMakeLists.txt
│       └── test_black_scholes.cpp  # Catch2 unit tests for the core
│
├── python/                         # PYTHON LIBRARY (PyPI artifact)
│   └── quantfinlib/
│       ├── __init__.py             # public API surface, extension loading & fallback
│       ├── _version.py
│       ├── _compat.py              # chooses C++ ext vs pure-Python reference impls
│       ├── _pure.py                # NumPy/SciPy reference backend (fallback + test oracle)
│       ├── _qfcore.pyi             # type stubs for the compiled extension
│       ├── options/
│       │   ├── __init__.py
│       │   ├── black_scholes.py    # vectorised BS pricing/Greeks/implied vol (NumPy)
│       │   ├── monte_carlo.py      # MC façades: European (pathwise Greeks)/Asian/barrier
│       │   └── american.py         # CRR binomial American pricing
│       ├── risk/
│       │   ├── __init__.py
│       │   └── var.py              # VaR / ES on Pandas return series & portfolios
│       ├── ml/
│       │   ├── __init__.py
│       │   ├── vol_surface.py      # NN surface fitting, serialization, fit budget
│       │   └── models.py           # MLP + no-arbitrage penalties (torch, optional extra)
│       └── utils/
│           ├── __init__.py
│           └── validation.py       # error hierarchy + validation/broadcasting helpers
│
├── api/                            # FASTAPI SERVICE (Docker artifact, not on PyPI)
│   └── app/
│       ├── main.py                 # app factory, middleware, lifespan, error handlers
│       ├── config.py               # pydantic-settings, reads .env
│       ├── dependencies.py         # DI: DB session, auth, rate limiting
│       ├── routers/
│       │   ├── __init__.py
│       │   ├── options.py          # /v1/options: price, greeks, implied-vol, mc-price
│       │   ├── risk.py             # /v1/risk: var, portfolio-var
│       │   ├── volatility.py       # /v1/vol-surface: fit → jobs/{id} → {id}(+/info)
│       │   └── admin.py            # /v1/admin/audit (admin scope)
│       ├── schemas/
│       │   ├── __init__.py
│       │   ├── options.py          # pydantic request/response models
│       │   └── common.py
│       ├── services/
│       │   ├── __init__.py
│       │   ├── pricing_service.py  # thin orchestration over quantfinlib
│       │   ├── audit.py            # calculation audit trail (background task)
│       │   └── job_runner.py       # in-process background jobs + shutdown drain
│       └── db/
│           ├── __init__.py
│           ├── models.py           # SQLAlchemy: audit log, fit jobs, saved surfaces
│           └── session.py
│
├── migrations/                     # Alembic (API database)
│   ├── alembic.ini
│   ├── env.py
│   └── versions/
│       └── 0001_initial_schema.py
│
├── web/                            # minimal static demo UI served by FastAPI (no framework)
│   └── index.html                  # option-pricer form: fetch → loading → result/error
│
├── tests/
│   ├── python/                     # unit tests (no network, no DB)
│   │   ├── conftest.py
│   │   ├── test_black_scholes.py   # parity, FD Greeks, limits, implied-vol conditioning
│   │   ├── test_monte_carlo.py     # convergence bounds, pathwise Greeks, Asian, barrier
│   │   ├── test_american.py        # CRR lattice: premia, convergence, degenerate cases
│   │   ├── test_var.py             # golden values, numpy-quantile parity, portfolios
│   │   ├── test_vol_surface.py     # fitting, penalties, serialization, budget
│   │   ├── test_pure_backend.py    # fallback backend exercised directly
│   │   ├── test_properties.py      # Hypothesis: invariants over random inputs
│   │   └── test_packaging.py       # version sync, public API surface
│   └── integration/
│       ├── conftest.py             # app factory + per-scope API keys
│       ├── test_api.py             # pricing/risk/job-lifecycle/audit round trips
│       ├── test_auth.py            # 401/403 auth, scopes, 429 rate limiting
│       └── test_config.py          # .env.example ↔ Settings drift gate
│
├── benchmarks/
│   └── bench_pricing.py            # pytest-benchmark: C++ vs pure-Python paths
│
├── examples/
│   └── quickstart.py               # end-to-end library usage example
│
├── scripts/
│   ├── dev_setup.ps1               # Windows dev bootstrap (venv, editable install)
│   ├── dev_setup.sh                # Linux/macOS equivalent
│   └── build_wheels.sh             # local cibuildwheel invocation
│
└── docker/
    ├── Dockerfile.api              # multi-stage: build wheel → slim runtime image
    └── docker-compose.yml          # api + postgres for local development
```

### Key structural decisions

| Decision | Rationale |
|---|---|
| Monorepo, single version | Core, bindings and library evolve in lockstep; ABI drift between C++ and Python halves is the #1 failure mode this prevents. |
| `python/` layout (src-style) | Prevents accidentally importing the uninstalled package from the repo root; forces tests to run against the built/installed artifact. |
| API outside the PyPI package | Library users should not pull FastAPI/SQLAlchemy/uvicorn as dependencies. The API is a *consumer* of the library. |
| Pure-Python fallbacks (`_compat.py`) | `pip install quantfinlib` must work even where no wheel exists (new Python versions, exotic platforms); also gives a reference implementation for testing the C++ core against. |
| ML as optional extra (`quantfinlib[ml]`) | Torch is a ~2 GB dependency; option pricing users shouldn't pay for it. |

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority) — ✅ complete

- [x] **P1.1** Repo bootstrap: `pyproject.toml` (scikit-build-core + pybind11), top-level CMake, `.gitignore`, ruff/mypy/pre-commit config
- [x] **P1.2** C++ core: `black_scholes.{hpp,cpp}` with price + Greeks (call/put, incl. expiry→0 / vol→0 limit values), unit-tested with Catch2
- [x] **P1.3** pybind11 module `quantfinlib._qfcore` (zero-copy `py::array_t`, GIL released, `.pyi` stubs, version injected from CMake)
- [x] **P1.4** Python façades with validation/broadcasting + pure-Python fallback; implied vol via bracketed Newton (native) / Brent (fallback), `ConvergenceError` diagnostics; vectorised batch Greeks
- [x] **P1.5** Test harness: put-call parity, finite-difference Greek checks, native-vs-pure parity to 1e-12
- [x] **P1.6** CI workflow `ci.yml`: ruff + clang-format + mypy (blocking) → cmake build + ctest → pytest (coverage gate ≥ 70%) on 3 OS; Alembic migration check; blocking pip-audit
- [x] **P1.7** Release workflow `release.yml`: cibuildwheel matrix, sdist, PyPI **trusted publishing** (OIDC), TestPyPI dry-run + smoke install

### Phase 2 — Core features (medium priority) — ✅ complete

- [x] **P2.1** Monte Carlo engine in C++: GBM, antithetic variates, seeded; European (exact terminal scheme) + arithmetic Asian + discretely monitored barrier payoffs; OpenMP over deterministic per-chunk RNG streams (thread-count-independent results)
- [x] **P2.2** Risk module: historical (interpolated quantile, numpy-compatible) & parametric VaR + ES; portfolio aggregation (≡ w'Σw for the parametric method)
- [x] **P2.3** ML volatility surface: Pandas→tensor pipeline, MLP with calendar + butterfly (Durrleman) no-arbitrage penalties via autograd, early stopping, wall-clock budget, versioned serialization (`to_bytes`/`from_bytes`)
- [x] **P2.4** FastAPI service: pricing/greeks/implied-vol/MC/VaR/portfolio-VaR routers, pydantic v2 schemas, API-key auth **with scopes** (price/fit/admin), calculation audit log + admin read endpoint (SQLAlchemy + Alembic)
- [x] **P2.5** Long-running fits as background jobs: job table, in-process runner with graceful shutdown drain, 202→poll→query lifecycle
- [x] **P2.6** Docker: multi-stage image, compose stack (Postgres + one-shot Alembic migrate job + API), `docker.yml` pushing to GHCR with boot smoke test
- [x] **P2.7** Integration tests: TestClient suite (auth/scopes/rate-limit/audit/job lifecycle), MC statistical bounds, benchmark suite

### Phase 3 — Polish & optimization (lower priority) — partially complete

- [x] **P3.1a** Pathwise MC Greeks (delta/vega) for European options
- [ ] **P3.1b** Variance reduction beyond antithetic: control variates, Sobol QMC
- [x] **P3.2a** American options: CRR binomial lattice in C++
- [ ] **P3.2b** Longstaff-Schwartz LSM for path-dependent American exercise
- [x] **P3.3** Batch pricing API (whole chains in one call); OpenMP parallel MC
- [ ] **P3.4** Docs site (mkdocs-material) with API reference; publish to GitHub Pages
- [x] **P3.5a** Structured logging (structlog: JSON prod / console dev), request-id tracing end-to-end
- [ ] **P3.5b** Prometheus metrics endpoint
- [x] **P3.6a** Per-key sliding-window rate limiting (in-process, per replica)
- [ ] **P3.6b** Response caching keyed on canonical request hash
- [x] **P3.7** Property-based testing (Hypothesis): parity/bounds/round-trip invariants fuzzing the C++ boundary
- [ ] **P3.8** Wheel size audit, ABI3 evaluation, SLSA provenance on releases
