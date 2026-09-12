# Customer Churn Predictor — Technical Notes

Actionable guidance for building, testing, shipping, and operating the project.

---

## 3.1 CI/CD Pipeline Design

Two workflows, both GitHub Actions:

### `ci.yml` — runs on every PR and push
```text
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│  Lint    │ → │  Type    │ → │  Test    │ → │  Build   │ → │  Audit   │
│ ruff +   │   │  mypy    │   │ pytest + │   │ pip      │   │ pip-audit│
│ black --check│          │   │ coverage │   │ install -e│   │          │
└──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘
```
- **Fast feedback first:** lint/format are seconds; fail early.
- **Test matrix:** Python 3.12 (single version to match Streamlit Cloud runtime).
- **Coverage gate:** fail under 80% on changed packages.
- **Integration tests:** spin up an ephemeral PostgreSQL `services:` container.

### `deploy.yml` — runs on push to `main` (after CI passes)
```text
main push → [CI green?] → [Run DB migrations] → [Streamlit Cloud auto-redeploy] → [Smoke check]
```
- Streamlit Community Cloud redeploys automatically on `main` updates; `deploy.yml`
  owns the **migration gate** and a post-deploy **smoke test** (HTTP 200 on `/`).
- For staging, use a second Streamlit Cloud app tracking a `staging` branch.

**Environments:** `dev` (local), `staging` (staging branch + staging DB),
`prod` (main branch + prod DB). Each maps to its own GitHub Environment with
scoped secrets and optional required reviewers on prod.

---

## 3.2 Testing Strategy

| Level | Tool | Scope | Target |
|-------|------|-------|--------|
| **Unit** | `pytest` | preprocessing, feature engineering, recommendation mapping, predict contract | ≥ 80% line coverage on `src/` |
| **Property/Data** | `pytest` + synthetic frames | invariants: no NaN leakage, shape stability, prob ∈ [0,1] | key transforms |
| **Integration** | `pytest` + PostgreSQL service | `repository.py` against a real ephemeral DB; migrations apply cleanly | critical paths |
| **Model quality** | `pytest` "gate" test | trained model AUC ≥ baseline threshold on fixed seed/holdout | regression guard |
| **E2E / smoke** | `streamlit` + `pytest` (AppTest) or Playwright | app boots, key page renders, single prediction works | happy path |

**Conventions**
- Deterministic seeds (`random_state=42`) everywhere randomness matters.
- Fixtures generate **synthetic** churn data (`conftest.py`) — never real customer data in tests.
- Mock the DB engine for unit tests; use the real service only in integration tests.
- Mark slow/model-training tests with `@pytest.mark.slow` and exclude from PR fast lane.

---

## 3.3 Deployment Strategy

**Primary:** **Streamlit Community Cloud**
- Connect the GitHub repo; entrypoint `app/streamlit_app.py`.
- Runtime deps from `requirements.txt` (must be **pinned** and Cloud-compatible).
- Secrets via the Cloud **Secrets** UI (mirrors `.streamlit/secrets.toml.example`).
- Auto-redeploy on `main`.

**Database:** Managed PostgreSQL — **Neon** or **Supabase** (free-tier friendly,
serverless) or **AWS RDS** for production-grade. Connect via pooled URL.

**Model artifact distribution:**
- Small artifact (< 100 MB): commit to a release asset or object storage (S3 /
  Supabase Storage); app downloads + caches on first load.
- Avoid committing large binaries to git; use `models/artifacts/` (git-ignored) + remote pull.

**Containerization (optional parity / self-hosting):**
- `Dockerfile` provided for reproducible local runs and self-hosted deploys
  (e.g. behind an SSO reverse proxy). Not required for Streamlit Cloud.
- `docker-compose.yml` brings up app + PostgreSQL for local end-to-end dev.

---

## 3.4 Environment Management

**12-factor config:** all environment-specific values come from environment
variables, loaded and validated by Pydantic in `config.py`. Never hardcode.

Precedence: `process env` → `.env` (local only) → defaults.

| Environment | Config source | DB |
|-------------|---------------|-----|
| dev | `.env` (from `.env.example`) | local Docker PG or Neon dev branch |
| staging | GitHub Environment secrets / Streamlit secrets | staging DB |
| prod | GitHub Environment secrets / Streamlit secrets | prod DB (restricted) |

See **`.env.example`** in the repo root for the full key list. Summary:

```dotenv
APP_ENV=development
DATABASE_URL=postgresql+psycopg://user:pass@localhost:5432/churn
MODEL_ARTIFACT_PATH=models/artifacts/model.joblib
MODEL_REMOTE_URL=
LOG_LEVEL=INFO
OPTUNA_N_TRIALS=50
RANDOM_SEED=42
```

---

## 3.5 Version Control Workflow

**Recommended: GitHub Flow (trunk-biased).**

- `main` is always deployable; protected (PR + green CI + 1 review required).
- Short-lived feature branches: `feat/…`, `fix/…`, `chore/…`, `exp/…` (experiments).
- Squash-merge to keep `main` history linear and readable.
- Optional `staging` branch mapped to a staging Streamlit app for pre-prod validation.

**Rationale:** GitHub Flow matches a small team continuously shipping a single
deployable far better than Gitflow's heavier release/hotfix branching. ML
experiments live on disposable `exp/` branches and are never merged unless they
improve the model gate.

**Commit hygiene:** Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`…),
enforced lightly. `pre-commit` runs ruff/black/mypy before every commit.

**Notebooks:** keep `notebooks/` out of the critical path; strip outputs before
commit (`nbstripout`) to avoid noisy diffs and accidental data leakage.

---

## 3.6 Common Pitfalls (this stack)

1. **Data leakage** — Fitting encoders/scalers on the full dataset before split.
   *Fix:* fit transforms on **train only**, apply to test; persist them with the model.

2. **Native build wheels on Streamlit Cloud** — XGBoost/LightGBM/CatBoost +
   SHAP/llvmlite can fail to build if versions drift. *Fix:* pin known-good
   versions; prefer manylinux wheels; test the exact `requirements.txt` in CI.

3. **Class imbalance** — Churn is typically 5–20% positive. Accuracy is
   misleading. *Fix:* optimize **AUC / PR-AUC / recall@k**; use
   `scale_pos_weight` (XGB/LGBM) or class weights; consider calibration.

4. **Uncalibrated probabilities** — Tree ensembles output ranking scores, not
   true probabilities. *Fix:* isotonic/Platt calibration if you act on absolute
   thresholds; show a calibration curve.

5. **SHAP performance** — Exact SHAP on large batches is slow/memory-heavy.
   *Fix:* use `TreeExplainer` (fast for GBMs), sample rows for global plots, cache.

6. **CatBoost categorical handling differs** — It consumes raw categoricals;
   XGBoost/LightGBM need encoding. *Fix:* keep per-algorithm preprocessing
   branches; don't double-encode for CatBoost.

7. **Streamlit reruns** — The whole script reruns on each interaction; expensive
   work (model load, DB queries) re-executes. *Fix:* `st.cache_resource` for the
   model/engine, `st.cache_data` for query results.

8. **Connection exhaustion** — Streamlit reruns can leak DB connections.
   *Fix:* pooled engine cached once; use context-managed short sessions; `pool_pre_ping=True`.

9. **Train/serve skew** — Feature engineering implemented differently in training
   vs. serving. *Fix:* a **single** `features/engineering.py` used by both paths.

10. **Optuna determinism & cost** — Studies can run unbounded. *Fix:* set
    `n_trials`/`timeout`, fix the sampler seed, enable pruning, persist the study.

11. **Artifact/version mismatch** — Loading a model trained with a different lib
    version. *Fix:* store library versions + feature schema in artifact metadata;
    validate on load (`ModelArtifactError`).

12. **Secrets leaking via `st.write`/logs** — Easy to accidentally print config.
    *Fix:* never log secrets; `repr` redaction in `config.py`.
