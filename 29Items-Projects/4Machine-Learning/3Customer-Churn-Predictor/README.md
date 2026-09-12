# 📉 Customer Churn Predictor

Self-service tool that predicts customer **churn probability** and produces
**actionable retention recommendations**, with **SHAP** explanations and
**Optuna**-tuned gradient-boosting models (XGBoost / LightGBM / CatBoost).

> Built for the analytics/CRM team. Deployed on **Streamlit Community Cloud**,
> backed by **PostgreSQL**.

---

## ✨ Features
- **Best-of-3 modeling** — Optuna searches XGBoost, LightGBM, and CatBoost; the
  best by cross-validated ROC-AUC wins.
- **Explainable** — per-prediction and global SHAP feature importance.
- **Actionable** — top churn drivers mapped to a retention playbook.
- **Self-service UI** — single prediction, model insights, and batch CSV scoring.
- **Auditable** — every prediction and training run is logged to PostgreSQL.

## 🏗️ Architecture
A **layered modular monolith**: a thin Streamlit UI over a reusable
`churn_predictor` package, with offline (CI-driven) training producing a frozen
model artifact that the app loads at runtime. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

```
app/ (Streamlit)  →  src/churn_predictor/ (domain)  →  PostgreSQL + model artifact
```

## 📚 Documentation
| Doc | Contents |
|-----|----------|
| [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) | File structure + phased TODO list |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Pattern, diagrams, data flow, security |
| [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) | CI/CD, testing, deployment, pitfalls |

## 🚀 Quickstart

### Option A — one command (Docker)
Brings up PostgreSQL, runs a one-shot **trainer** (migrate → seed synthetic data →
train a model), then starts the app:

```bash
docker compose up --build      # app at http://localhost:8501
```

### Option B — local
```bash
# 1. Clone & enter
git clone <repo-url> && cd 3Customer-Churn-Predictor

# 2. Set up environment
cp .env.example .env          # edit DATABASE_URL etc.
make dev                       # install deps + pre-commit hooks

# 3. Apply schema (cross-platform; no psql needed) + seed demo data
make migrate
make seed                      # synthetic customers (idempotent)

# 4. Train a model (writes models/artifacts/model.joblib)
make train                     # or: make bootstrap  (migrate + seed + train)

# 5. Run the app
make run                       # http://localhost:8501
```

> `make bootstrap` does steps 3–4 in one shot (wait for DB → migrate → seed → train).

## 🧪 Development
```bash
make lint        # ruff
make format      # black + ruff --fix
make typecheck   # mypy
make test        # fast tests
make test-all    # incl. slow model-training tests
```

## ✅ Testing

Three tiers, selected by marker. `make test` runs the fast lane; CI runs everything
(it provides a PostgreSQL service so the integration tier executes).

```bash
pytest -m "not slow and not integration"   # fast unit lane (no heavy deps, no DB)
pytest -m slow                              # full ML stack (XGBoost/LightGBM/CatBoost/SHAP)
pytest -m integration                       # requires a live PostgreSQL (self-skips if absent)
pytest                                      # everything (what CI runs)
```

**Unit** — no external dependencies, run anywhere:

| File | Covers |
|------|--------|
| `test_preprocessing.py` | `clean` / `encode` / `split`; `Preprocessor` fit-transform, leakage & unseen-category handling |
| `test_synthetic.py` | synthetic data generator (shape, binary target, determinism) |
| `test_config.py` | settings caching + secret redaction |
| `test_loader.py` | CSV loading + schema validation |
| `test_recommendations.py` | SHAP-driver → retention-action mapping |
| `test_migrate_unit.py` | SQL-splitter logic (parses the real migration files, **no DB**) |

**Slow** (`@pytest.mark.slow`) — exercise the real ML stack:

| File | Covers |
|------|--------|
| `test_models.py` | full training run → artifact bundle |
| `test_predict.py` | `predict_one` / `predict_frame`, SHAP explanations, missing-artifact errors |
| `test_tuning.py` | Optuna tuning across all three algorithms |

**Integration** (`@pytest.mark.integration`) — require a live PostgreSQL; self-skip when unreachable:

| File | Covers |
|------|--------|
| `test_repository.py` | customer CRUD round-trip, bulk upsert, save/read predictions, model-run logging |
| `test_migrate.py` | migrations apply + idempotency against a real DB |
| `test_bootstrap.py` | `wait_for_db`, `seed_if_empty` idempotency |

> Coverage gate: **70%** (`--cov-fail-under=70`), enforced in CI. Fixtures use only
> synthetic data — no real customer data ever touches the test suite.

## 📦 Deployment
Push to `main` → Streamlit Cloud auto-redeploys; the `deploy.yml` workflow applies
DB migrations and smoke-tests the app. See [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md).

## 🗂️ Project layout
```
app/                 Streamlit UI (entrypoint + pages)
src/churn_predictor/ Core package: data, features, models, db, retention
migrations/          Forward-only SQL migrations
tests/               Pytest suite (synthetic data)
docs/                Plan, architecture, tech notes
.github/workflows/   CI + deploy pipelines
```

## 🛠️ Troubleshooting

| Symptom | Cause & fix |
|---------|-------------|
| App shows **"No trained model found"** | No artifact yet. Run `make bootstrap` (or `make seed && make train`), or set `MODEL_REMOTE_URL` to pull one. With Docker, the `trainer` service does this automatically. |
| **`DataLoadError: Failed to read from PostgreSQL`** | DB unreachable or schema not applied. Check `DATABASE_URL`, then run `make migrate`. Confirm Postgres is up (`docker compose ps`). |
| **`ModelArtifactError: ... missing keys`** | Artifact was trained on an older schema. Retrain with `make train` to regenerate the `{model, preprocessor, metadata}` bundle. |
| Streamlit prediction/save shows a **DB error** | The app degrades gracefully and shows a message instead of crashing. Verify the DB is reachable; scoring still works without a DB (only persistence needs it). |
| **`pip install` fails on xgboost/lightgbm/catboost** | Use Python 3.12 and upgrade pip (`pip install -U pip`) so manylinux/Windows wheels are picked up. On minimal Linux images install `libgomp1`. |
| Windows: **"fatal exception: access violation"** *after* tests report `100%` | Benign native-lib (OpenMP) shutdown artifact; all tests have already passed. Does not occur on the Linux CI runner. |
| **CatBoost** error `Can't create train working dir` | Already handled — the estimator is built with `allow_writing_files=False`. If you add new CatBoost calls, pass the same flag. |
| Docker build fails on **`COPY README.md`** | `README.md` must stay in the build context (it's un-ignored in `.dockerignore` because `pyproject.toml` references it). Don't re-add a blanket `*.md` ignore without the `!README.md` exception. |
| App is **slow on first prediction** | Expected: the model + SHAP explainer load lazily and are then cached (`st.cache_resource`). Subsequent calls are fast. |
| Optuna tuning **runs too long** | Lower `OPTUNA_N_TRIALS` / `CV_FOLDS` (env vars), or set `OPTUNA_TIMEOUT_SEC`. The Docker `trainer` uses small values for a fast demo. |

## 📄 License
MIT.
