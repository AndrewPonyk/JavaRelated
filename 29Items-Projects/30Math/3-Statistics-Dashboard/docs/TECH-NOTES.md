# Statistics Dashboard — Technical Notes

> Companion docs: [PROJECT-PLAN.md](PROJECT-PLAN.md) · [ARCHITECTURE.md](ARCHITECTURE.md)

---

## 3.1 CI/CD Pipeline Design

Two GitHub Actions workflows (`.github/workflows/`):

```mermaid
flowchart LR
    FB["feature branch"] -->|"PR"| L["lint & typecheck<br/>ruff · ruff format · mypy"]
    L --> T["unit + e2e tests<br/>pytest -m 'not integration'<br/>coverage report"]
    L --> I["integration tests<br/>postgres:16 service<br/>alembic upgrade head → pytest -m integration"]
    T --> D["docker build<br/>(main only — deploy artifact parity)"]
    I --> D
    D -->|"merge to main"| SC["Streamlit Cloud<br/>auto-deploy from GitHub"]
    SC --> SM["post-deploy-smoke.yml<br/>poll /_stcore/health (20×30s)"]
```

**`ci.yml`** — on every PR and push to `main`:

| Stage | What runs | Fails when |
|---|---|---|
| `lint` | `ruff check .`, `ruff format --check .`, `mypy app` | style drift, dead imports, type errors |
| `test` | `pytest -m "not integration" --cov=app --cov-fail-under=75` (unit + headless AppTest e2e) | logic regressions or coverage below the 75% gate |
| `integration` | Postgres 16 service container → `alembic upgrade head` → `pytest -m integration` | broken migrations or repository queries |
| `docker` | `docker build` (push to `main` only) | image no longer builds — keeps the self-host escape hatch honest |

A third workflow, **`cleanup.yml`**, runs weekly (and on demand): it deletes saved
datasets older than 90 days that no analysis run references
(`scripts/cleanup_stale_datasets.py --apply`), and exits cleanly while the
`DATABASE_URL` secret is unset.

**`post-deploy-smoke.yml`** — on push to `main`: Streamlit Cloud redeploys automatically from GitHub;
this workflow polls `${STREAMLIT_APP_URL}/_stcore/health` with retries and fails loudly if the deploy
never becomes healthy. Requires repo **variable** `STREAMLIT_APP_URL` (skips gracefully when unset).

Conventions: concurrency group per ref (superseded runs cancel), pip cache via `setup-python`,
Dependabot keeps actions and pips fresh, `main` is protected (CI green + review required).

**Environments & promotion:** deploys are git-driven. `dev` = any branch run locally; `staging` = a
second Streamlit Cloud app tracking `develop` (optional); `prod` = the app tracking `main`. Promotion
is a merge — no manual artifact copying. Database migrations run as an explicit release step
(`alembic upgrade head` against the target DB) *before* merging code that needs the new schema
(expand → migrate → contract; see §3.6).

---

## 3.2 Testing Strategy

**Pyramid (fast & pure at the bottom, thin at the top):**

| Level | Tooling | Scope & targets |
|---|---|---|
| **Unit** (bulk) | `pytest`, seeded `numpy.random.default_rng` | `app/stats` (every selector rule has a named test; runners/effect sizes/post-hoc against known answers — ≥92% line coverage), services and repositories over a **SQLite-backed persistence fixture** (`sqlite_env`) so the real save/load/log code paths run without PostgreSQL |
| **Integration** | `pytest -m integration`, real PostgreSQL (CI service container; locally `docker compose up db`), gated by `TEST_DATABASE_URL` | dialect parity: JSONB round-trips, migration chain applies cleanly from zero |
| **E2E** | `streamlit.testing.v1.AppTest` (headless, no browser) | every page renders in demo mode, plus widget-driven flows: select columns → submit → assert results for hypothesis, regression, distribution, and A/B pages |

Principles:

- **Statistical correctness tests use known answers**: shifted normal samples (d = 1.5, n = 100) must reject; effect sizes checked against hand-computed values; sample-size planner validated against published tables. Avoid asserting exact p-values on borderline cases (flaky by construction) — assert direction and thresholds.
- **Determinism**: every random fixture takes a fixed seed; no test depends on wall-clock or network.
- **Skips are explicit**: `pytest.importorskip("statsmodels")` and `skipif(not TEST_DATABASE_URL)` — the suite degrades visibly, never silently.
- The **75% coverage gate is enforced** in CI (`--cov-fail-under=75`); the suite currently sits around 85%.
- Two AppTest facts worth knowing: widget auto-IDs derive from construction params, so when one selection changes another widget's options, apply values one `run()` at a time; and `from_function` executes the function's *source* as a fresh script, so pass a runner that imports the page module inside its body.
- Future work: property-based tests (`hypothesis`) for profiler/selector totality.

---

## 3.3 Deployment Strategy

**Primary: Streamlit Community/Enterprise Cloud** (no containers needed):

1. Connect the GitHub repo; main file = `streamlit_app.py`; pin **Python 3.12** in advanced settings (there is no `runtime.txt` support — it's a UI setting).
2. Dependencies install from **`requirements.txt`** (that's why runtime deps live there, not in `pyproject.toml`).
3. Secrets (`DATABASE_URL`, etc.) go into the app's **Secrets** UI in `secrets.toml` format — mirrors `.streamlit/secrets.toml.example`.
4. Every push to the tracked branch redeploys; `post-deploy-smoke.yml` verifies health.
5. PostgreSQL is a managed external service (Neon / Supabase / RDS) with `sslmode=require`; Streamlit Cloud has no persistent disk, so **nothing durable is ever written to the container filesystem**.

**Fallback / self-host: Docker** (kept honest by the CI build stage):

- `docker/Dockerfile` — slim Python image, non-root user, `/_stcore/health` healthcheck.
- `docker/docker-compose.yml` — app + `postgres:16-alpine` for local full-stack dev: `docker compose -f docker/docker-compose.yml up`.
- Self-hosting for real traffic: run N replicas behind a reverse proxy **with sticky sessions** (Streamlit uses WebSockets and holds per-session state in process memory).

Rollback = revert the commit on `main` (Streamlit redeploys) — plus, if a migration was involved, its tested `downgrade()`; prefer expand/contract so code rollback alone is safe.

---

## 3.4 Environment Management

Configuration is code-free and resolved by `app/core/config.py` (pydantic-settings) in priority order:

```
st.secrets (Streamlit Cloud)  >  process env vars  >  .env file (local)  >  defaults
```

| | development | staging | production |
|---|---|---|---|
| App | `streamlit run streamlit_app.py` / compose | Streamlit Cloud app (develop) | Streamlit Cloud app (main) |
| Config source | `.env` (+ optional local `secrets.toml`) | Streamlit secrets UI | Streamlit secrets UI |
| Database | compose Postgres — or none (**demo mode**) | managed PG, `_staging` db | managed PG, dedicated db + least-privilege role |
| `APP_ENV` | `development` | `staging` | `production` |

**Demo mode** is the key DX feature: with no `DATABASE_URL`, the app runs entirely in-memory on
bundled sample data — clone → install → run, zero infrastructure.

**`.env.example` template** (committed; copy to `.env`, never commit `.env`):

```dotenv
# ── Statistics Dashboard — local configuration ─────────────────────
# Copy to .env and adjust. Precedence: st.secrets > env vars > .env > defaults.

APP_ENV=development                 # development | staging | production
LOG_LEVEL=INFO                      # DEBUG | INFO | WARNING | ERROR

# Leave unset to run in demo mode (no persistence, bundled sample data).
# Local full stack: docker compose -f docker/docker-compose.yml up -d db
DATABASE_URL=postgresql+psycopg2://stats:stats@localhost:5432/stats_dashboard
# Managed PG (staging/prod) — require TLS:
# DATABASE_URL=postgresql+psycopg2://user:pass@host:5432/db?sslmode=require
# Lightweight local library without Docker (SQLite is supported for dev):
# DATABASE_URL=sqlite:///./local-dev.db

MAX_UPLOAD_MB=25                    # keep in sync with .streamlit/config.toml server.maxUploadSize
DEFAULT_ALPHA=0.05                  # default significance level offered in the UI
MAX_ANALYSIS_ROWS=200000            # larger frames analyzed on a seeded sample (visible banner)

# Optional error tracking (requires `pip install sentry-sdk`); unset = disabled.
# SENTRY_DSN=https://...@sentry.io/...
```

Rules: new setting ⇒ add to `Settings`, `.env.example`, and `secrets.toml.example` in the same PR.
CI never needs real secrets (integration tests use the throwaway service container).

---

## 3.5 Version Control Workflow: **GitHub Flow**

Short-lived branches off `main` → PR → CI + one review → squash-merge → auto-deploy. Tag releases (`v0.x.y`) for humans.

**Why not the alternatives:** Gitflow's release/hotfix ceremony buys nothing when deployment is
"push to main" on Streamlit Cloud and the team is small; pure trunk-based (commit straight to main)
drops the PR gate that CI and statistical-correctness review need — this app's whole value is
*correct* statistics, so review is non-negotiable. GitHub Flow is the minimal process that keeps
`main` always deployable.

Conventions: conventional-commit-ish messages (`feat:`, `fix:`, `docs:`…), branch names
`feat/…`, `fix/…`, `chore/…`; PR template enforces "tests added + docs touched"; migrations
reviewed by a second person as a hard rule.

---

## 3.6 Common Pitfalls (this stack, specifically)

**Streamlit's execution model**
1. **The script reruns top-to-bottom on every widget interaction.** Anything expensive must be cached (`st.cache_data`) or it will run dozens of times per session. Corollary: never put side effects (DB writes!) in module/page top-level flow — gate them behind `st.button`/forms.
2. **Idempotent initialization**: logging/engine setup runs on every rerun — guard it (module singleton), or you get duplicated log lines and leaked connections.
3. **`st.cache_data` hashes inputs**: DataFrames hash by content (fine but costly for huge frames — hash a content digest instead); unhashable args need `_arg` underscore-prefix. Cached functions must return *copies* semantics — never mutate a cached frame in place.
4. **`st.session_state` is per-browser-tab and lives in process memory** — lost on redeploy/restart; anything durable belongs in Postgres.

**Streamlit Cloud**
5. ~1 GB RAM on the community tier: a 300 MB CSV parsed with object dtypes will OOM the app. Cap uploads, prefer pyarrow dtypes, sample large frames.
6. No persistent filesystem + apps sleep when idle: never write durable state to disk; first request after sleep is slow — the smoke workflow tolerates this with retries.
7. Connection slots on managed Postgres are scarce; every app replica × pool_size consumes them. Keep pools small, `pool_pre_ping=True`, `pool_recycle` under the provider's idle timeout, and add pgbouncer before adding replicas.

**Statistics (correctness traps)**
8. **Shapiro-Wilk at large n rejects trivially** (everything is "non-normal" at n = 50k). Cap/subsample normality checks (`NORMALITY_MAX_N`) and lean on CLT reasoning for large samples — the selector encodes this.
9. **Multiple comparisons**: >2 groups with pairwise follow-ups without Holm/BH correction silently inflates false positives (Phase 2 item).
10. **A/B peeking**: repeatedly checking a running test until p < 0.05 is a guaranteed false-positive machine. The UI must display "planned n vs. current n" and Phase 3 adds sequential methods.
11. **SRM (sample-ratio mismatch)**: a 55/45 split when 50/50 was configured invalidates the experiment regardless of p-value — that's why `srm_check` runs before any A/B verdict.
12. **Zero variance / tiny groups** make t-tests undefined (NaN): validate preconditions and raise `AnalysisError` with a human explanation rather than rendering NaN.
13. **p-value ≠ effect size**: always report both (the result card enforces this) — at n = 10⁶ everything is "significant".

**Pandas / SciPy / Plotly**
14. CSV type inference surprises: numeric IDs become int (meaningless means), "1/0" columns are binary not continuous, dates arrive as strings — the profiler's column-kind classification exists to catch this; let users override (Phase 2 explorer).
15. `NaN` handling differs per SciPy function (some propagate, some raise): drop/report missing values explicitly *before* testing; report dropped counts in warnings.
16. Plotly with >10k points: use `Scattergl` (WebGL) or aggregate first; serializing huge figures to the browser is the slow part.
17. `statsmodels` formula API (`patsy`) evaluates column names as Python-ish expressions — sanitize/quote column names (`Q("weird col")`) and never feed raw user text into formulas.

**PostgreSQL / SQLAlchemy / Alembic**
18. Alembic autogenerate does not fully understand `JSON().with_variant(JSONB, "postgresql")` or server defaults — review every autogenerated migration by hand.
19. Naive vs. aware datetimes: standardize on `TIMESTAMPTZ` + UTC everywhere (`DateTime(timezone=True)`, `func.now()`), or joins across sources will lie by an offset.
20. **Expand → migrate → contract**: with auto-deploy, code and schema never flip atomically — make each migration backward-compatible with the previous app version (add nullable column → deploy code → backfill → tighten).

**Windows dev specifics (this repo is developed on Windows)**
21. Use `python -m pip` / `python -m pytest` to dodge PATH shims; paths in code go through `pathlib` only; Git line endings: keep `.gitattributes`/editor at LF for `.py`, `.yml`, `.toml`.
