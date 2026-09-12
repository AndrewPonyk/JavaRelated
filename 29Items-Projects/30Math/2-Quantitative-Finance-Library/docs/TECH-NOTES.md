# Quantitative Finance Library — Technical Notes

## 3.1 CI/CD Pipeline Design

Two workflows with distinct triggers; releases are tag-driven and reuse the CI gates.

```
ci.yml  (push to main + all PRs)
────────────────────────────────
lint          ruff check + ruff format --check + clang-format --dry-run + mypy (all blocking)
  └─ build    cmake  →  build _qfcore  →  ctest (Catch2 C++ tests) on 3 OS
      └─ test pytest (unit + integration) on matrix {ubuntu, macos, windows} × {3.10, 3.13}
              coverage gate: fail under 70% (currently ~83%)
              alembic upgrade head && alembic check  (migrations match models)
              pytest benchmarks --benchmark-disable   (smoke only, no timing gates in PRs)
      └─ audit pip-audit (blocking — known vulns fail the build)

release.yml  (tag v*.*.*)
────────────────────────────────
verify        re-run full test job (tags don't skip gates)
  └─ wheels   cibuildwheel matrix: manylinux x86_64 / macos universal2 / windows amd64
  └─ sdist    python -m build --sdist
      └─ publish  TestPyPI (always) → smoke-install test → PyPI via Trusted Publishing (OIDC)
      └─ docker   build docker/Dockerfile.api → push ghcr.io/<org>/quantfinlib-api:{ver,latest}
```

Key decisions:

- **Compiled-extension CI must run on all three OSes** — Windows MSVC breaks C++ code that
  GCC accepts weekly; catching it in PRs is 100× cheaper than at release time.
- **Trusted Publishing** (PyPI OIDC): no `PYPI_TOKEN` secret exists anywhere. The `release`
  GitHub *environment* is protected (required reviewer) — a compromised PR cannot publish.
- **ccache/sccache** with GitHub Actions cache keys on the CMake+source hash keeps C++
  rebuilds under a minute.
- Deploy stages for the API: `docker.yml` pushes the image; dev auto-deploys `main`,
  staging auto-deploys tags, **prod is a manual environment approval** on the same artifact
  (promote-the-image, never rebuild for prod).

## 3.2 Testing Strategy

| Layer | Framework | What & targets |
|---|---|---|
| C++ core | **Catch2** via CTest | Closed-form values vs published references (Hull tables), edge cases (T→0, σ→0, deep ITM/OTM). Fast (<1 s), runs in every build. |
| Python unit | **pytest** (+ `numpy.testing`) | ≥ 90 % line coverage on `quantfinlib`. The numerics-specific suites below matter more than the % number. |
| Numerical invariants | pytest + **Hypothesis** (`test_properties.py`) | Put-call parity to 1e-12; Greeks vs central finite differences (rtol 1e-6); monotonicity (price ↑ in σ); price-space implied-vol round trips (vol-space only where vega is non-negligible — deep ITM inversion is ill-posed); C++ vs pure-Python fallback agree to 1e-12 (this cross-check is the real test of the bindings). |
| Statistical (MC) | pytest, fixed seeds | MC price within 3·SE of closed form; SE shrinks ~1/√N; antithetic variance strictly lower. Seeded → deterministic in CI, no flaky tolerance-fudging. |
| ML | pytest (small synthetic surfaces) | Fit on a known SVI-generated surface recovers it within tolerance; arbitrage-penalty terms decrease during training; smoke-test on CPU in <60 s (tiny net, few epochs). |
| Integration | pytest + **FastAPI TestClient** + SQLite | Full HTTP round-trips: happy paths, 422 validation payloads, auth failures, job lifecycle (submit→poll→result). No mocking of the library — real numerics on small inputs. |
| E2E (release gate) | `docker compose` in `release.yml` | Boot the real image + Postgres, hit `/health` and one pricing call with `curl`. Catches wheel/image packaging errors nothing else does. |
| Performance | **pytest-benchmark** | Tracked on main only (not PRs — CI runners are noisy). Alert threshold: >25 % regression on batch-pricing benchmark. |

Test data policy: no live market data in tests — synthetic quotes + a small frozen CSV
fixture (`tests/python/fixtures/`) so tests are hermetic and license-clean.

## 3.3 Deployment Strategy

Two artifacts, two paths:

**1. Library → PyPI.** Binary wheels via **cibuildwheel** (manylinux2014 x86_64,
macOS universal2, Windows amd64; CPython 3.10–3.13) plus an sdist for everything else.
The pure-Python fallback means an sdist install without a compiler still works (slower,
warns). Version = git tag, single source in `pyproject.toml` via `setuptools-scm`-style
dynamic versioning (scikit-build-core supports it).

**2. API → Docker.** Multi-stage `docker/Dockerfile.api`:
stage 1 builds the wheel inside manylinux; stage 2 is `python:3.12-slim` + wheel + api code,
runs `uvicorn` as non-root. Image is immutable and environment-agnostic — all config via env
vars (12-factor). Target platform is anything that runs a container (ECS/Fargate, Cloud Run,
or a plain VM with compose); nothing in the code assumes a specific cloud. Health endpoints:
`/health/live` (process up) and `/health/ready` (DB reachable + native core loaded) — wire
these to orchestrator probes. Rollback = redeploy previous image tag; DB migrations are
applied by a separate one-shot job (`alembic upgrade head`) *before* the new replicas roll,
and must always be backward-compatible one version (expand→migrate→contract).

## 3.4 Environment Management

- All runtime config through **environment variables**, parsed once by
  `api/app/config.py` (`pydantic-settings`). Fail-fast: the app refuses to boot with
  missing/invalid config rather than limping.
- Local dev: copy `.env.example` → `.env` (gitignored). CI: workflow env + repo secrets.
  Staging/prod: platform secret store injects env vars; no `.env` files exist on servers.
- Same image across dev/staging/prod; only env differs. `QF_ENV=dev|staging|prod` selects
  log format (pretty/JSON) and enables prod guards (`QF_REQUIRE_NATIVE=1`, docs UI off).
- The `.env.example` (in repo root) is the authoritative list of every variable — adding a
  config field without updating it fails a unit test (`test_config_documented`).

## 3.5 Version Control Workflow

**Trunk-based development with short-lived branches** (GitHub Flow):

- `main` is always releasable and protected: PRs only, CI green + 1 review required.
- Branches: `feat/...`, `fix/...`, live < a few days, squash-merged (linear history —
  makes `git bisect` on numerical regressions actually usable).
- Releases: tag `vX.Y.Z` on main → `release.yml` does the rest. SemVer where **any change
  to numerical results is at least a minor version** and called out in CHANGELOG — downstream
  risk systems must be able to pin against silent number changes.
- No Gitflow: with wheels published from tags and one deployable service, `develop`/release
  branches add ceremony without benefit at this team size. Revisit only if long-term support
  branches (e.g., `1.x` security fixes) become contractual.

## 3.6 Common Pitfalls (this specific stack)

1. **ABI/version skew between wheel builds** — a wheel built against NumPy 2.x headers can
   break on NumPy 1.x at runtime. Pin `build-system.requires` NumPy properly (build against
   oldest supported; NumPy 2 headers are backward-compatible targets) and smoke-import wheels
   in `release.yml` on a clean venv.
2. **Holding the GIL in C++ loops** — forgetting `py::gil_scoped_release` turns the FastAPI
   service single-threaded under load. Release the GIL in every compute function; never touch
   Python objects inside the released region (copy sizes/pointers out first).
3. **Windows MSVC differences** — `M_PI` needs `_USE_MATH_DEFINES`, `/fp:fast` changes results
   vs GCC's defaults, and OpenMP support is older (2.0). Keep math flags conservative
   (`/fp:precise`, `-ffp-contract=off`) — reproducibility beats a few percent of speed in
   finance; cross-OS CI catches the rest.
4. **MC tests that flake** — asserting `abs(mc - bs) < 0.01` fails randomly. Always fix seeds
   AND assert within k·standard-error, testing the *statistics*, not magic tolerances.
5. **NaN poisoning** — one bad quote (negative bid) silently NaNs an entire fitted surface.
   Validate at the boundary, `np.errstate(invalid="raise")` in library internals, and unit-test
   the *rejection* paths, not just happy paths.
6. **Torch as a hard dependency** — it drags ~2 GB into every install and breaks manylinux
   audits. Keep it behind `quantfinlib[ml]` with a lazy import and a clear error message.
7. **Editable installs with compiled code** — `pip install -e .` does not rebuild C++ on
   header edits. Use `scikit-build-core`'s `editable.rebuild = true` in dev, and document
   `cmake --build build` for the inner loop.
8. **Async FastAPI + blocking numerics** — calling a 30 s MC job in an `async def` route
   blocks the event loop. CPU-bound endpoints are plain `def` (thread pool) or dispatch to
   the job runner; never `await`-wrap compute.
9. **Alembic drift** — autogenerate misses server defaults and enum changes. Every migration
   is reviewed by hand and tested by the CI step `alembic upgrade head && alembic check`
   against a scratch SQLite/Postgres.
10. **Benchmarking in CI PRs** — shared runners are noisy; gating PRs on timings creates
    false reds. Track benchmarks on `main` trend only, gate releases manually.
