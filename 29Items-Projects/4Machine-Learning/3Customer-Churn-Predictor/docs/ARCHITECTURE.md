# Customer Churn Predictor — Architecture

## 2.1 Chosen Architectural Pattern

**Pattern: Layered Modular Monolith** (single deployable, strict internal layering),
with an **offline batch training pipeline** decoupled from the **online serving app**.

### Why this fits

| Requirement | How the pattern serves it |
|-------------|---------------------------|
| Small team, **self-service** tool | One deployable (Streamlit Cloud) — minimal ops overhead |
| Modest, bursty traffic (internal analysts) | No need for microservices or autoscaling fleets |
| ML lifecycle (train rarely, serve often) | **Training is offline/CI-driven**; serving just loads a frozen artifact |
| Maintainability | Clear layers (UI → domain → data) keep the core testable & reusable |
| Future REST/API or scheduler reuse | Core logic lives in an installable package, UI is a thin client |

Microservices would add network, deployment, and observability cost with no
payoff at this scale. Serverless functions struggle with large model artifacts
(cold starts loading XGBoost/SHAP). A layered modular monolith is the pragmatic
sweet spot: **simple to deploy, easy to evolve, hard to tangle.**

```mermaid
graph TB
    subgraph Offline["⚙️ Offline / CI Training Pipeline"]
        RAW[(Raw data<br/>CSV / PostgreSQL)]
        PRE[Preprocessing]
        FE[Feature Engineering]
        TUNE[Optuna Tuning<br/>XGB · LGBM · CatBoost]
        TRAIN[Train best model]
        ART[[Model Artifact<br/>+ metadata]]
        RAW --> PRE --> FE --> TUNE --> TRAIN --> ART
    end

    subgraph Online["🖥️ Online Serving — Streamlit Modular Monolith"]
        UI[Streamlit UI<br/>app/]
        DOMAIN[churn_predictor core<br/>predict · explain · retention]
        DATA[data + db layer]
        UI --> DOMAIN --> DATA
    end

    ART -. loaded at runtime .-> DOMAIN
    DATA <--> DB[(PostgreSQL<br/>customers · predictions · model_runs)]

    USER([Analyst]) --> UI
```

---

## 2.2 Key Component Interactions

| Interaction | Mechanism | Notes |
|-------------|-----------|-------|
| Analyst ↔ App | HTTPS (Streamlit server) | Session-based, websocket for reactivity |
| App → Core package | **In-process function calls** | No network hop; thin UI over domain API |
| Core → PostgreSQL | **SQLAlchemy** (pooled connections) | Parameterized queries only |
| Core → Model artifact | **Direct file load**, cached via `st.cache_resource` | Loaded once per process |
| Training → Artifact | File write (`joblib`) + metadata row in `model_runs` | Produced offline / in CI |
| CI → Streamlit Cloud | Git push to `main` triggers redeploy | Migrations gated before deploy |

The system is intentionally **call-based, not message-based**. There is no message
queue or event bus — traffic volume and latency tolerance do not justify the
complexity. Batch scoring runs synchronously within a Streamlit session (chunked
for large files); if volumes grow, the same core functions can be lifted into an
async worker without touching the UI.

```mermaid
graph LR
    subgraph app[" app/ (Presentation) "]
        A1[streamlit_app.py]
        A2[1_Predict.py]
        A3[2_Model_Insights.py]
        A4[3_Batch_Scoring.py]
    end
    subgraph core[" churn_predictor (Domain) "]
        C1[models/predict.py]
        C2[models/explain.py]
        C3[retention/recommendations.py]
        C4[features/engineering.py]
    end
    subgraph data[" data + db (Persistence) "]
        D1[db/repository.py]
        D2[data/loader.py]
        D3[db/connection.py]
    end
    A2 --> C1 --> C2 --> C3
    A4 --> C1
    A3 --> C2
    C1 --> C4
    C1 --> D1
    D2 --> D3
    D1 --> D3
    D3 --> PG[(PostgreSQL)]
```

---

## 2.3 Data Flow

### Single-customer prediction (online)

```mermaid
sequenceDiagram
    actor Analyst
    participant UI as Streamlit (1_Predict.py)
    participant Pred as predict.py
    participant FE as engineering.py
    participant SHAP as explain.py
    participant Rec as recommendations.py
    participant Repo as repository.py
    participant DB as PostgreSQL

    Analyst->>UI: Enter / select customer attributes
    UI->>FE: build_features(raw_input)
    FE-->>UI: feature_vector
    UI->>Pred: predict_proba(feature_vector)
    Pred-->>UI: churn_probability (0..1)
    UI->>SHAP: explain(feature_vector)
    SHAP-->>UI: per-feature contributions
    UI->>Rec: recommend(top_drivers)
    Rec-->>UI: ranked retention actions
    UI->>Repo: save_prediction(customer, proba, drivers)
    Repo->>DB: INSERT INTO predictions
    UI-->>Analyst: Probability + SHAP plot + action playbook
```

### Offline training (CI / manual)

```mermaid
flowchart LR
    A[Load data<br/>loader.py] --> B[Preprocess<br/>clean · encode · split]
    B --> C[Feature engineering]
    C --> D{Optuna study<br/>per algorithm}
    D -->|XGBoost| E1[CV AUC]
    D -->|LightGBM| E2[CV AUC]
    D -->|CatBoost| E3[CV AUC]
    E1 & E2 & E3 --> F[Select best by AUC]
    F --> G[Refit on full train]
    G --> H[Evaluate on holdout]
    H --> I[[Persist artifact:<br/>model + preprocessor + metadata]]
    I --> J[(model_runs row in DB)]
```

> **Artifact contents.** The persisted bundle is a single `joblib` file containing
> `{model, preprocessor, metadata}`. The fitted `Preprocessor` (numeric medians +
> one-hot schema, fit on the **training split only**) travels with the model so the
> serving path reproduces transforms exactly — eliminating train/serve skew.
> In Docker, a one-shot **`trainer`** service runs `python -m churn_predictor.bootstrap`
> (migrate → seed → train) before the `app` service starts.

---

## 2.4 Scalability & Performance Strategy

**Current scale:** internal team, tens of concurrent users, batch files up to ~100k rows.

| Concern | Strategy |
|---------|----------|
| Model load cost | Load artifact **once** per process via `st.cache_resource`; never per request |
| Repeated reads | Cache reference/lookup data with `st.cache_data(ttl=...)` |
| Batch scoring | **Vectorized** scoring; chunked DB writes (`executemany`); SHAP sampled for large N |
| DB connections | SQLAlchemy **connection pool** (`pool_pre_ping=True`), short-lived sessions |
| Training cost | Offline only; Optuna **pruning** (median/Hyperband) cuts wasted trials |
| Read scaling | PostgreSQL **read replica** if reporting load grows (no app change) |
| Horizontal scale | Stateless app → run N replicas behind a load balancer if self-hosting |
| Future high-throughput | Lift `predict.py` into a queue-backed worker; UI contract unchanged |

**Performance budget (serving):** single prediction incl. SHAP < 1s; batch of
10k rows < 30s. Achieved by keeping the model in memory and vectorizing.

---

## 2.5 Security Considerations

| Area | Approach |
|------|----------|
| **Authentication** | Streamlit Cloud SSO/email allowlist for internal users; optional shared app password (`st.secrets`) or reverse-proxy SSO (OAuth2) when self-hosted |
| **Authorization** | Role-light: viewer vs. operator (batch write) flag in session; enforce write-back permissions in `repository.py` |
| **Data protection** | TLS in transit (managed PG + HTTPS); PII minimization — store customer **IDs**, not raw PII, where possible; encryption at rest via managed DB |
| **SQL safety** | **Parameterized queries only** via SQLAlchemy; no string-formatted SQL |
| **Input validation** | Pydantic / explicit schema checks on uploaded CSVs and manual inputs; reject unexpected columns/types |
| **Secret management** | **No secrets in git.** `st.secrets` on Streamlit Cloud, `.env` locally, GitHub **Actions Secrets** in CI. `.env.example` documents required keys only |
| **API/Transport** | HTTPS enforced; CSRF mitigated by Streamlit session model; rate-limit at proxy if exposed |
| **Dependency hygiene** | `pip-audit` / Dependabot in CI; pinned versions in `requirements.txt` |
| **Auditability** | Every prediction + model run written to DB with timestamp, model version, and actor |

---

## 2.6 Error Handling & Logging Philosophy

**Principles**
1. **Fail loud in code, fail gracefully in UI.** The core package raises typed
   exceptions; the Streamlit layer catches them and shows actionable messages.
2. **Structured logging** via the stdlib `logging` module configured centrally in
   `config.py` (JSON-ish key=value lines, level via `LOG_LEVEL` env var).
3. **No silent excepts.** Catch narrow exceptions; re-raise or log with context.
4. **Idempotent, transactional writes.** DB writes wrapped in transactions; rollback on error.

```mermaid
flowchart TD
    E[Operation] -->|raises| T{Error type?}
    T -->|Validation / user input| U[st.error + guidance<br/>log WARNING]
    T -->|Data / DB transient| R[Retry once · rollback<br/>log ERROR + context]
    T -->|Model / artifact missing| F[Degrade: disable scoring<br/>log CRITICAL · alert]
    T -->|Unexpected| X[Generic safe message<br/>log ERROR + stack trace]
```

**Custom exception hierarchy** (defined in the core package):

```text
ChurnError (base)
├── ConfigError          # missing/invalid settings
├── DataLoadError        # source unreachable / schema mismatch
├── PreprocessingError   # bad/unexpected input data
├── ModelArtifactError   # artifact missing / incompatible version
└── PredictionError      # scoring failure
```

**Logging targets:** stdout (captured by Streamlit Cloud / container runtime).
Each log line carries: timestamp, level, module, `run_id`/`request_id` where
applicable, and a short message. Sensitive values are never logged.
