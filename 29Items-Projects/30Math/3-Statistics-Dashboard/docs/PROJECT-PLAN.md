# Statistics Dashboard — Project Plan

> Interactive statistical analysis with Streamlit: hypothesis testing, regression analysis,
> distribution fitting, and self-service A/B-test analytics with **automated statistical
> test selection** driven by data characteristics.

| | |
|---|---|
| **Tech stack** | Python 3.11+, Streamlit, Pandas, SciPy, statsmodels, Plotly, PostgreSQL |
| **Deployment** | Streamlit Community/Enterprise Cloud (auto-deploy from GitHub) |
| **CI/CD** | GitHub Actions |
| **Related docs** | [ARCHITECTURE.md](ARCHITECTURE.md) · [TECH-NOTES.md](TECH-NOTES.md) |

---

## 1.1 Project File Structure

The repository is a **single deployable Streamlit application** with strict internal layering
(see [ARCHITECTURE.md §2.1](ARCHITECTURE.md)). The structure separates the UI (Streamlit),
the framework-free statistics engine, the persistence layer, migrations, tests, CI, and tooling.

```text
3-Statistics-Dashboard/
│
├── streamlit_app.py                  # Entrypoint (Streamlit Cloud "main file"); registers pages via st.navigation
├── requirements.txt                  # Runtime deps — the file Streamlit Cloud installs from
├── requirements-dev.txt              # Dev/test/lint toolchain (includes -r requirements.txt)
├── pyproject.toml                    # Tool configs: ruff, mypy, pytest, coverage (deps stay in requirements*.txt)
├── alembic.ini                       # Alembic migration config (script_location = migrations/)
├── Makefile                          # Task shortcuts: install / lint / test / run / db-up / migrate / seed
├── README.md                         # Quickstart + repo map
├── .env.example                      # Template for local configuration (never commit .env)
├── .gitignore
├── .dockerignore
├── .pre-commit-config.yaml           # ruff + hygiene hooks on every commit
├── Claude-Fable-5.txt                # (scaffold provenance marker)
│
├── .streamlit/
│   ├── config.toml                   # Theme (palette-derived), server limits, telemetry off
│   └── secrets.toml.example          # Template for st.secrets (real secrets live in Streamlit Cloud UI)
│
├── .github/
│   ├── dependabot.yml                # Weekly pip + actions update PRs
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── workflows/
│       ├── ci.yml                    # lint → typecheck → unit+e2e (75% coverage gate) → integration → docker
│       ├── post-deploy-smoke.yml     # After push to main: poll the deployed app's health endpoint
│       └── cleanup.yml               # Weekly data-retention job (stale unreferenced datasets)
│
├── app/                              # ── Application package ──────────────────────────────
│   ├── core/                         # Cross-cutting: config, logging, error taxonomy
│   │   ├── config.py                 # pydantic-settings; resolves st.secrets > env > .env > defaults
│   │   ├── errors.py                 # AppError hierarchy + @guard_page (friendly errors, Sentry hook)
│   │   └── logging.py                # Idempotent stdout logging; JSON lines in production; Sentry init
│   │
│   ├── data/                         # Persistence layer (SQLAlchemy 2.0)
│   │   ├── db.py                     # Engine singleton (PG pooled / SQLite dev) + session_scope()
│   │   ├── models.py                 # ORM: Dataset (content-hash dedup), Experiment, AnalysisRun
│   │   └── repositories.py           # All queries; guarded experiment lifecycle; full CRUD
│   │
│   ├── services/                     # Use-case orchestration (UI-free, Streamlit-free)
│   │   ├── dataset_service.py        # Demo data, upload validation, saved-dataset library (parquet payloads)
│   │   ├── analysis_service.py       # profile → recommend → execute → post-hoc → persist (write-behind)
│   │   ├── ab_testing_service.py     # Frequentist + Bayesian conversion analysis, SRM, planner, exp. logging
│   │   ├── experiment_service.py     # Experiment registry CRUD + guarded lifecycle
│   │   └── report_service.py         # Self-contained HTML report rendering (escaped, zero external assets)
│   │
│   ├── stats/                        # ── Statistics engine: pure functions, no I/O, no Streamlit ──
│   │   ├── profiler.py               # Column-kind classification (+ analyst overrides), assumption checks
│   │   ├── test_selector.py          # Automated test selection decision tree → TestRecommendation
│   │   ├── hypothesis_tests.py       # Uniform runners: t/Welch/Mann-Whitney/ANOVA/Welch-ANOVA/KW/χ²/Fisher/z
│   │   ├── posthoc.py                # Holm correction + pairwise follow-ups after significant omnibus tests
│   │   ├── effect_sizes.py           # Cohen's d, Hedges' g, Cliff's δ, rank-biserial, Cramér's V, η²
│   │   ├── regression.py             # OLS & logistic: coefficients, diagnostics (DW/BP/JB), VIF, Q-Q data
│   │   └── distributions.py          # Candidate fitting ranked by AIC + KS; support guards; Q-Q data
│   │
│   └── ui/                           # Streamlit-only code
│       ├── state.py                  # Session-state accessors: active data, kind overrides, result memory
│       ├── components/
│       │   ├── data_source_picker.py # Demo / upload / saved-library sources; sampling cap; save & delete
│       │   ├── result_card.py        # Uniform result presentation (p, statistic, effect size, rationale)
│       │   └── charts.py             # All Plotly builders (validated palette, light+dark token sets)
│       └── pages/                    # One module per page; each exposes render()
│           ├── home.py               #   + recent-analyses panel (persisted runs)
│           ├── data_explorer.py      #   summary/missingness/group-by/correlations/kind overrides
│           ├── hypothesis_testing.py #   flagship flow + post-hoc table + HTML report download
│           ├── regression.py         #   coefficients, diagnostics metrics, residual & Q-Q charts, VIF
│           ├── distribution_fitting.py # candidate multiselect, AIC ranking, overlay + Q-Q charts
│           ├── ab_testing.py         #   frequentist + Bayesian analysis, SRM, planner, experiment logging
│           └── experiments.py        #   experiment registry (create / lifecycle / delete)
│
├── migrations/                       # Alembic (PostgreSQL schema as code)
│   ├── env.py
│   ├── script.py.mako
│   └── versions/
│       ├── 0001_initial_schema.py    # datasets, experiments, analysis_runs
│       └── 0002_dataset_hash_experiment_planning.py  # content_hash; planned n & expected split
│
├── tests/
│   ├── conftest.py                   # Seeded fixtures + sqlite_env (full persistence stack on SQLite)
│   ├── unit/                         # Stats engine, services, repositories (SQLite), report, A/B
│   ├── integration/                  # Real PostgreSQL parity (CI service container)
│   └── e2e/                          # AppTest: every page renders + widget-driven analysis flows
│
├── scripts/
│   ├── init_db.py                    # `alembic upgrade head` wrapper
│   ├── seed_demo_data.py             # Regenerates demo CSV; --to-db registers it in the library
│   └── cleanup_stale_datasets.py     # Retention: delete old datasets with no runs (dry-run by default)
│
├── data/
│   └── samples/
│       └── ab_test_demo.csv          # Bundled demo dataset → app works with zero setup (demo mode)
│
├── docker/
│   ├── Dockerfile                    # Self-host fallback image (build-verified in CI)
│   └── docker-compose.yml            # app + postgres:16 with healthchecks
│
└── docs/
    ├── PROJECT-PLAN.md               # ← this file
    ├── ARCHITECTURE.md
    └── TECH-NOTES.md
```

### Layering rules (enforced in review; future: automate with import-linter)

| Layer | May import | Must NOT import |
|---|---|---|
| `app/ui` | services, stats (result types), core, streamlit, plotly | data (repositories) directly |
| `app/services` | stats, data, core | streamlit, plotly |
| `app/stats` | pandas, numpy, scipy, statsmodels, core (types only) | streamlit, sqlalchemy, services |
| `app/data` | sqlalchemy, core | streamlit, stats, services |
| `app/core` | stdlib, pydantic | everything else |

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority) — ✅ complete (code)

- [x] Repository scaffold: directory layout, `pyproject.toml`, `requirements*.txt`, `.gitignore`, pre-commit
- [x] `app/core`: settings resolution (st.secrets → env → .env), error taxonomy, idempotent logging
- [x] Entrypoint with `st.navigation` and all pages registered
- [x] Bundled demo dataset + deterministic seed script (`--to-db` registers it in the library)
- [x] Data source picker (demo / validated upload / saved library) with large-frame sampling cap
- [x] CI pipeline: ruff → mypy → unit+e2e (75% coverage gate) → integration (Postgres service) → Docker build

### Phase 2 — Core features (medium priority) — ✅ complete

- [x] Profiler: column-kind classification, missingness, group structure, normality (Shapiro, capped n) & variance (Levene) checks
- [x] **Automated test selector**: decision tree over data characteristics → `TestRecommendation` with human-readable rationale (incl. analyst-assigned ORDINAL outcomes)
- [x] Hypothesis-test runners with uniform `TestResult` (statistic, p, effect size, interpretation, warnings) — Welch ANOVA included
- [x] Effect sizes: Cohen's d, Hedges' g, rank-biserial, Cliff's δ (O(n log n)), Cramér's V, η²
- [x] Multiple-comparison corrections: Holm step-down pairwise follow-ups after significant >2-group omnibus tests
- [x] Persist every analysis run (params + results JSONB, write-behind); "Recent analyses" panel on Home
- [x] Dataset persistence: size-capped parquet payloads, sha256 content-hash dedup, list/load/delete library UI
- [x] A/B module: two-proportion analysis with 95% Newcombe CI on lift, SRM guardrail, sample-size/power planner (validated against published values)
- [x] Regression: OLS + logistic with coefficient table, R²/pseudo-R², Durbin-Watson/Breusch-Pagan/Jarque-Bera, residual & Q-Q charts, VIF screen
- [x] Distribution fitting: candidate multiselect (incl. beta with support guard), AIC ranking + KS diagnostics, overlay & Q-Q charts
- [x] Data Explorer: summaries, missingness chart, group-by aggregations, correlation matrix, column-kind overrides
- [x] Coverage gate in CI: `--cov-fail-under=75` (current: ~85%; stats engine ≥92%)

### Phase 3 — Polish & optimization (lower priority) — ✅ code items complete

- [x] Caching strategy: `st.cache_data` on demo/library loads; frames > `MAX_ANALYSIS_ROWS` analyzed on a seeded sample with a visible banner
- [x] Report export: hypothesis analysis → self-contained, escaped HTML download
- [x] Bayesian A/B analysis: Beta-Binomial posterior, P(B beats A), posterior-density chart, credible interval on lift
- [x] Interim-look guardrails: pre-registered `planned_n_per_variant` triggers peeking warnings; experiment split feeds the SRM check
- [x] Experiment registry UI: full CRUD with forward-only lifecycle (draft → running → completed), result logging with data snapshots
- [x] Observability: JSON log lines in production, optional Sentry (DSN-gated) initialized at startup and hooked into `guard_page`
- [x] Performance: WebGL traces above 10k points, box-point capping, palette dark-mode token set
- [x] Data retention: `cleanup_stale_datasets.py` (dry-run default) + weekly `cleanup.yml` workflow

### Operational launch checklist (requires cloud access — not code)

- [ ] Provision managed PostgreSQL (Neon/Supabase/RDS) for staging & prod; put the URL in Streamlit secrets (`sslmode=require`)
- [ ] Run `alembic upgrade head` against the target database (release step)
- [ ] Create the Streamlit Cloud app(s): main file `streamlit_app.py`, Python 3.12, secrets from `secrets.toml.example`
- [ ] Set repo variable `STREAMLIT_APP_URL` (post-deploy smoke) and secret `DATABASE_URL` (retention workflow)
- [ ] Branch protection on `main`: require CI green + 1 review
- [ ] Enable viewer access control (workspace allow-list now; `st.login` OIDC when an IdP is available)

### Future backlog (explicitly deferred)

- [ ] Sequential testing (mSPRT / always-valid p-values) beyond the current planned-n interim warnings
- [ ] Property-based tests (`hypothesis`) for profiler & selector totality
- [ ] Subject-ID-keyed pairing UI for paired tests (currently: by row order, warned in results)
- [ ] Object storage for payloads beyond the parquet size cap; import-linter contract for the layering table

### Definition of Done (every task)

Code + tests + docstring, ruff/mypy clean, CI green, no secrets in git,
user-facing errors human-readable, statistical output includes assumptions & caveats.
