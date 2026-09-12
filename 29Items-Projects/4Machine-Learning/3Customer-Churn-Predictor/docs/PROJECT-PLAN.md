# Customer Churn Predictor — Project Plan

> **Mission:** Predict customer churn probability and surface **actionable retention
> recommendations**, delivered as a self-service Streamlit application for the
> business/analytics team.

| | |
|---|---|
| **Tech Stack** | Python 3.12, XGBoost, LightGBM, CatBoost, Optuna, SHAP, Streamlit, PostgreSQL |
| **Deployment** | Streamlit Community Cloud (app) + Managed PostgreSQL (Neon / Supabase / RDS) |
| **CI/CD** | GitHub Actions |
| **Primary Users** | Retention / CRM analysts, Customer Success managers (self-service) |
| **Model Family** | Gradient-boosted decision tree ensemble (best-of-3 selected by CV AUC) |

---

## 1.1 Project File Structure

```text
3Customer-Churn-Predictor/
│
├── .github/
│   └── workflows/
│       ├── ci.yml                      # Lint + type-check + test on every PR
│       └── deploy.yml                  # Streamlit Cloud trigger + DB migration gate
│
├── docs/
│   ├── PROJECT-PLAN.md                 # ← this file
│   ├── ARCHITECTURE.md                 # System design, diagrams, data flow
│   └── TECH-NOTES.md                   # CI/CD, testing, deployment, pitfalls
│
├── src/
│   └── churn_predictor/                # Installable package (pip install -e .)
│       ├── __init__.py
│       ├── config.py                   # Pydantic settings (env-driven)
│       │
│       ├── data/
│       │   ├── loader.py               # Read from PostgreSQL / CSV
│       │   └── preprocessing.py        # Cleaning, encoding, split
│       │
│       ├── features/
│       │   └── engineering.py          # Domain feature transforms (tenure buckets, etc.)
│       │
│       ├── models/
│       │   ├── train.py                # Fit final model, persist artifact
│       │   ├── tuning.py               # Optuna study (XGB / LGBM / CatBoost search)
│       │   ├── predict.py              # Load artifact, score new customers
│       │   └── explain.py              # SHAP values + plots
│       │
│       ├── db/
│       │   ├── connection.py           # SQLAlchemy engine / session factory
│       │   └── repository.py           # CRUD: customers, predictions, runs
│       │
│       └── retention/
│           └── recommendations.py      # Map SHAP drivers → playbook actions
│
├── app/                                # Streamlit front-end
│   ├── streamlit_app.py                # Home / single-customer scoring
│   └── pages/
│       ├── 1_Predict.py                # Interactive single + manual prediction
│       ├── 2_Model_Insights.py         # SHAP global importance, metrics
│       └── 3_Batch_Scoring.py          # CSV upload → scored table → DB write
│
├── migrations/                         # Plain-SQL, forward-only migrations
│   ├── 001_initial_schema.sql
│   └── 002_prediction_audit.sql
│
├── tests/
│   ├── conftest.py                     # Fixtures (synthetic dataframe, fake engine)
│   ├── test_preprocessing.py
│   ├── test_models.py
│   └── test_recommendations.py
│
├── notebooks/
│   └── 01_eda.ipynb                    # Exploratory analysis (not in prod path)
│
├── models/artifacts/                   # Serialized model + metadata (git-ignored)
├── data/{raw,processed}/               # Local data (git-ignored)
│
├── .streamlit/
│   ├── config.toml                     # Theme + server settings
│   └── secrets.toml.example            # Secrets template for Streamlit Cloud
│
├── .env.example                        # Local/runtime config template
├── .gitignore
├── .pre-commit-config.yaml             # ruff + black + mypy hooks
├── .dockerignore
├── Dockerfile                          # Optional self-hosting / parity image
├── docker-compose.yml                  # Local app + PostgreSQL
├── pyproject.toml                      # Build, deps, tool config (ruff/black/mypy/pytest)
├── requirements.txt                    # Runtime deps (pinned, for Streamlit Cloud)
├── requirements-dev.txt                # Dev/test deps
├── Makefile                            # Dev ergonomics (make test, make train, ...)
├── README.md
└── claude-opus-4-8.txt                 # Build provenance marker
```

### Layering rationale

| Layer | Directory | Responsibility | Depends on |
|-------|-----------|----------------|------------|
| Presentation | `app/` | Streamlit UI, user interaction | `src/churn_predictor` only |
| Application/Domain | `src/churn_predictor/{models,features,retention}` | ML logic, business rules | `data`, `db` |
| Data access | `src/churn_predictor/{data,db}` | I/O, persistence | `config` |
| Config | `src/churn_predictor/config.py` | Settings from env | — |

The UI never touches the database or model files directly — it always goes
through the `churn_predictor` package. This keeps the app thin and the core
logic independently testable and reusable (e.g. from a future REST API or batch job).

---

## 1.2 Implementation TODO List

### ✅ Phase 1 — Foundation (High Priority) — **DONE**
- [x] Initialize repo, `pyproject.toml`, pinned `requirements*.txt`
- [x] Configure tooling: `ruff`, `black`, `mypy`, `pytest`, `pre-commit`
- [x] Implement `config.py` (Pydantic settings, 12-factor env loading)
- [x] Design & write `migrations/001_initial_schema.sql` (customers, predictions, model_runs)
- [x] Implement `db/connection.py` (SQLAlchemy engine, pooled, env-driven URL)
- [x] Implement `data/loader.py` — load from PostgreSQL **and** CSV fallback (+ incremental)
- [x] Implement `data/preprocessing.py` — clean, encode, train/test split (stratified)
- [x] Stand up CI (`ci.yml`): lint → type-check → test on every PR
- [x] Write `README.md` with quickstart

### 🟡 Phase 2 — Core Features (Medium Priority) — **DONE**
- [x] `features/engineering.py` — domain transforms (tenure buckets, usage ratios)
- [x] `models/tuning.py` — Optuna study with pruning across XGB/LGBM/CatBoost
- [x] `models/train.py` — fit best model, persist artifact + metadata, log run to DB
- [x] `models/predict.py` — load artifact, score single + batch
- [x] `models/explain.py` — SHAP global + per-prediction explanations
- [x] `retention/recommendations.py` — translate top SHAP drivers → retention actions
- [x] `db/repository.py` — full CRUD for customers + predictions + model runs (audit trail)
- [x] Streamlit `streamlit_app.py` + `1_Predict.py` — single-customer scoring UI + persist
- [x] Streamlit `2_Model_Insights.py` — global SHAP + holdout metrics + recent activity
- [x] Unit tests for preprocessing, recommendations, predict contract
- [x] Deploy pipeline (`deploy.yml`) + Streamlit Cloud connection

### 🟢 Phase 3 — Polish & Optimization (Lower Priority)
- [x] `3_Batch_Scoring.py` — CSV upload, bulk scoring, write-back to DB
- [ ] Probability calibration (isotonic / Platt) + calibration curve in UI *(future)*
- [x] Model registry / versioning (artifact metadata + metrics in `model_runs`)
- [ ] Drift monitoring job (PSI on key features) + alert *(future)*
- [x] Caching strategy (`st.cache_data` / `st.cache_resource`) applied
- [x] Role-light access control (optional shared password gate; SSO via reverse proxy)
- [x] Integration tests against ephemeral PostgreSQL service in CI
- [x] Performance: lazy artifact load, SHAP sampling for large batches
- [x] Containerize for optional self-hosting parity (`Dockerfile` + bootstrap trainer)

> Two Phase-3 items (probability calibration, drift monitoring) are intentionally
> deferred as future enhancements; everything else is implemented and tested.

---

## Definition of Done (per feature)
1. Code formatted (`black`), linted (`ruff`), typed (`mypy` clean).
2. Unit tests added, coverage on changed module ≥ 80%.
3. CI green on PR.
4. Documented in `README.md` or relevant `docs/` file.
5. No secrets committed; new config surfaced in `.env.example`.
