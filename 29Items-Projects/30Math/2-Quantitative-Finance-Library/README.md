# quantfinlib

Quantitative finance library with a **C++17 numerical core** and **Python bindings**,
plus an optional **FastAPI service**.

| Area | What's implemented |
|---|---|
| Options (closed form) | Black-Scholes price / Greeks / implied vol (bracketed Newton), vectorised over chains, exact limit values at `T→0` / `σ→0` |
| Options (Monte Carlo) | European (exact terminal scheme, pathwise delta & vega), arithmetic Asian, discretely monitored barriers; antithetic variates; OpenMP over deterministic per-chunk RNG streams — same seed ⇒ same result at any thread count |
| Options (American) | Cox-Ross-Rubinstein binomial lattice |
| Risk | Historical (numpy-compatible interpolated quantile) & parametric VaR + Expected Shortfall; portfolio aggregation |
| ML | Neural-net implied-vol surface fitting with calendar + butterfly (Durrleman) no-arbitrage penalties, early stopping, wall-clock budget, versioned serialization |
| API | Pricing / risk / surface-fitting endpoints, API-key auth with scopes, per-key rate limiting, calculation audit trail, background fit jobs (202 → poll → query), RFC 7807 errors, structured logging |
| Fallback | Pure-Python backend (`quantfinlib._pure`) keeps `pip install` working without a compiler — identical results to 1e-12, and it doubles as the parity-test oracle |

## Install

```bash
pip install quantfinlib            # core library (binary wheels)
pip install "quantfinlib[ml]"      # + torch for vol-surface fitting
pip install "quantfinlib[api,ml,dev]"  # service + ML + dev tooling (repo checkout)
```

## Library quick start

```python
import numpy as np
from quantfinlib.options import black_scholes as bs, monte_carlo as mc, price_american
from quantfinlib.risk import value_at_risk

# Whole chains in one call (batch-first: one C++ crossing, not 10 000)
strikes = np.linspace(80, 120, 9)
prices = bs.price(spot=100, strike=strikes, vol=0.2, rate=0.05, expiry=1.0, kind="call")
greeks = bs.greeks(spot=100, strike=strikes, vol=0.2, rate=0.05, expiry=1.0, kind="call")
iv     = bs.implied_vol(price=10.45, spot=100, strike=100, rate=0.05, expiry=1.0)

# Seeded Monte Carlo with pathwise Greeks; Asian & barrier payoffs
euro  = mc.price_european(100, 100, 0.2, 0.05, 1.0, "call", n_paths=500_000, seed=42)
asian = mc.price_asian(100, 100, 0.2, 0.05, 1.0, "call", n_steps=252, seed=42)
ko    = mc.price_barrier(100, 100, 0.2, 0.05, 1.0, "call",
                         barrier=130, barrier_type="up-out", seed=42)

amer  = price_american(100, 110, 0.2, 0.05, 1.0, "put")        # CRR lattice
var99 = value_at_risk(np.random.default_rng(0).normal(0, 0.01, 1000), confidence=0.99)
```

Full tour: [`examples/quickstart.py`](examples/quickstart.py) (includes surface fitting).

## API service

```bash
uvicorn app.main:app --reload --app-dir api      # dev: http://localhost:8000
```

Interactive docs at `/docs`, demo UI at `/`. Highlights:

```bash
# Price a chain
curl -X POST localhost:8000/v1/options/price -H 'Content-Type: application/json' \
  -d '{"spot":100,"strikes":[90,100,110],"vol":0.2,"rate":0.05,"expiry":1.0,"kind":"call"}'

# Long-running surface fit: 202 + job id -> poll -> query sigma(K,T)
curl -X POST localhost:8000/v1/vol-surface/fit -d @quotes.json -H 'Content-Type: application/json'
curl localhost:8000/v1/vol-surface/jobs/<job_id>
curl "localhost:8000/v1/vol-surface/<surface_id>?strike=105&expiry=0.5"
```

Auth: set `QF_API_KEY_HASHES` (see `.env.example`) — entries are SHA-256 hashes,
optionally scope-restricted (`<hash>:price|fit`). Dev mode with no keys allows
anonymous access. Every pricing call lands in the audit table
(`GET /v1/admin/audit`, admin scope) with a params hash and seed, so any
historical number can be re-derived.

## Docker (full stack)

```bash
docker compose -f docker/docker-compose.yml up --build
# db (Postgres) -> migrate (alembic upgrade head, one-shot) -> api :8000
```

## Development

```bash
./scripts/dev_setup.sh          # POSIX; Windows: scripts/dev_setup.ps1
                                # venv + editable install (builds C++) + pre-commit + tests

pytest tests -q                 # unit + integration (SQLite, real numerics)
pytest tests -q --cov           # with the 70% coverage gate (currently ~83%)
cmake -B build -DQFCORE_BUILD_TESTS=ON && cmake --build build && ctest --test-dir build
ruff check . && ruff format --check . && mypy      # linters (all blocking in CI)
alembic -c migrations/alembic.ini upgrade head     # apply DB migrations
python examples/quickstart.py                      # end-to-end library tour
```

Releases: tag `vX.Y.Z` → `release.yml` builds wheels (cibuildwheel), publishes to
TestPyPI, smoke-installs, then publishes to PyPI via **trusted publishing** behind a
protected environment; `docker.yml` pushes the API image to GHCR with a boot smoke test.

## Troubleshooting

**"compiled core (_qfcore) not available" RuntimeWarning** — you are on the pure-Python
fallback (correct results, 10-100× slower). Install a binary wheel (`pip install
quantfinlib` on a supported platform) or build from source with a C++17 compiler and
CMake ≥ 3.24. In production the API refuses to start like this when `QF_REQUIRE_NATIVE=1`.

**`pip install -e .` doesn't pick up C++ edits** — editable installs rebuild on import
(`editable.rebuild = true`), but only when scikit-build-core detects changes; if in doubt
run `pip install -e . --no-build-isolation -v` or build directly with `cmake --build build`.

**Vol-surface fit job ends `FAILED: did not converge`** — too few epochs for the quote
set, or the wall-clock budget (`QF_ML_MAX_FIT_SECONDS`) expired. Raise `epochs`, simplify
the quote set, or raise the budget. The API returns the residual MSE in the job detail.

**`422 implied vol: target price outside attainable range`** — the observed price is
below intrinsic or above the spot bound; no volatility can produce it. Check discounting
conventions (rates are continuously compounded).

**Windows console shows `UnicodeEncodeError`** — legacy cp1252 consoles can't print
Greek letters; run `chcp 65001` or `set PYTHONIOENCODING=utf-8`.

**`docker compose up` fails at `migrate`** — the Postgres healthcheck must pass first;
check `docker compose logs db`. To reset a broken local DB volume:
`docker compose -f docker/docker-compose.yml down -v`.

**MC results differ between machines** — results are deterministic per seed *per
platform*; `std::normal_distribution` is implementation-defined, so cross-OS
reproducibility is best-effort (documented in `cpp/include/qfcore/monte_carlo.hpp`).

## Documentation

- [Project plan & roadmap](docs/PROJECT-PLAN.md)
- [Architecture (diagrams, data flow, security)](docs/ARCHITECTURE.md)
- [Tech notes (CI/CD, testing, deployment, pitfalls)](docs/TECH-NOTES.md)

## License

MIT — see [LICENSE](LICENSE).
