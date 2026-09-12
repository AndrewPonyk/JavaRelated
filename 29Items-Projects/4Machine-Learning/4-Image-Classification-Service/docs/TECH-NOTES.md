# Technical Notes — Image Classification Service

Actionable engineering guidance for building, testing, deploying, and operating the
service. Pairs with `PROJECT-PLAN.md` (what to build) and `ARCHITECTURE.md` (how it
fits together).

---

## 3.1 CI/CD Pipeline Design

Two GitHub Actions workflows:

### `ci.yml` — runs on every PR and push to `main`

```
┌─────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────┐   ┌────────────┐
│  Lint   │ → │  Types   │ → │   Test   │ → │ Build images │ → │  (artifact)│
│ ruff    │   │  mypy    │   │ pytest   │   │ docker build │   │  on PR only│
│ prettier│   │  tsc     │   │ vitest   │   │ (no push)    │   │            │
└─────────┘   └──────────┘   └──────────┘   └──────────────┘   └────────────┘
```

- **Lint:** `ruff check` + `ruff format --check` (Python), `eslint` + `prettier --check`
  (frontend).
- **Type-check:** `mypy app` (backend), `tsc --noEmit` (frontend).
- **Test:** `pytest --cov` with a coverage gate; `vitest run --coverage`.
- **Build:** `docker build` both images to prove they compile — but **do not push** on PRs.
- Jobs run in parallel where possible; the build job depends on lint+test passing.

### `deploy.yml` — runs on push to `main` (or a release tag)

```
auth (WIF) → build & tag → push to Artifact Registry → deploy Cloud Run (staging)
          → smoke test (staging) → manual approval → deploy Cloud Run (prod)
```

- Authenticates to GCP with **Workload Identity Federation** (no JSON keys).
- Tags images with both `:$GIT_SHA` (immutable) and `:latest`.
- Promotes the **same image digest** staging → prod (build once, deploy many).
- Uses Cloud Run **revisions + traffic splitting** for safe rollout / instant rollback.

### Environments

| Env | Trigger | Approval | Traffic |
| --- | --- | --- | --- |
| `dev` | local / feature branch | none | n/a |
| `staging` | merge to `main` | automatic | 100% to new revision |
| `prod` | post-staging smoke pass | **manual gate** | canary 10% → 100% |

---

## 3.2 Testing Strategy

### Unit tests (target: **≥ 80% line coverage** on `services/` and `core/`)

- **Framework:** `pytest` + `pytest-asyncio` (backend), `vitest` + Testing Library (frontend).
- **Mock the model:** inject a *fake* `InferenceSession` returning deterministic logits so
  tests never download CLIP or load ONNX. The `inference.py` service takes the session via
  DI precisely to enable this.
- **Mock Redis:** use `fakeredis` so cache logic is tested without a server.
- Cover: preprocessing (resize/normalize/hash determinism), threshold logic,
  cache hit/miss/degradation, error mapping.

### Integration tests

- Spin up Redis + Postgres via `docker compose` (or `testcontainers`).
- Hit the real FastAPI app with `httpx.AsyncClient` against an ASGI transport.
- Verify the full `/classify` path (validation → cache → inference → response envelope)
  and `/categories` CRUD against a real DB + Alembic-migrated schema.

### End-to-end tests

- **Tool:** Playwright against a deployed **staging** URL.
- Flows: upload an image in the React console → see ranked labels; error states
  (bad file, server 5xx); auth-gated routes.
- Run nightly and as the `deploy.yml` staging smoke gate.

### Model quality tests (offline, not in request CI)

- Held-out eval set: assert macro-F1 ≥ threshold and per-class precision floors.
- **ONNX parity test:** PyTorch logits vs ONNX logits must match within `atol=1e-3`.
  This is the single most important guard against silent serving regressions.

---

## 3.3 Deployment Strategy

### Containerization

- **Multi-stage Docker builds.** Builder stage installs deps into a venv; final stage is
  a slim `python:3.12-slim` (or distroless) copying only the venv + app.
- **Serving image excludes torch/transformers** — it only needs `onnxruntime`, FastAPI,
  Pillow, redis. This keeps the image small (~hundreds of MB vs multiple GB) and cold
  starts fast.
- Run as a **non-root** user; `EXPOSE 8080` (Cloud Run's default `$PORT`).
- `uvicorn` with `--workers` tuned to vCPU; honor `$PORT`.

### Cloud Run

- Stateless service, autoscale on concurrency. `--min-instances=1` to avoid cold starts;
  `--max-instances` caps cost. `--cpu`/`--memory` sized for ONNX (e.g. 1–2 vCPU, 1–2 GiB).
- Model artifact loaded **at startup** from GCS (or baked into the image for fully
  immutable deploys — prefer baking for prod reproducibility).
- Secrets injected from **Secret Manager**; config via env vars.
- Connect to Memorystore (Redis) and Cloud SQL (Postgres) over the VPC connector.

### Rollout

- New revision → route 10% (canary) → watch error rate/latency → 100%.
- Rollback = shift traffic back to the previous revision (instant, no rebuild).

---

## 3.4 Environment Management

- **12-factor config:** everything via env vars, parsed once in `core/config.py`
  (`pydantic-settings`). No `os.environ` reads scattered through the code.
- Per-environment values live in the deploy platform (GitHub Environments + Secret
  Manager), **never** committed.
- Local dev uses a gitignored `.env` (copy from `.env.example`); `docker-compose.yml`
  wires service hostnames (`redis`, `db`).

### `.env.example` (backend)

```dotenv
# --- App ---
APP_ENV=development            # development | staging | production
LOG_LEVEL=INFO
PORT=8080

# --- Model ---
MODEL_VERSION=v1
MODEL_PATH=/models/vit_clip_v1.onnx
LABELS_PATH=/models/labels.json
THRESHOLDS_PATH=/models/thresholds.json
MAX_UPLOAD_BYTES=10485760      # 10 MiB

# --- Redis (optional; cache degrades gracefully if unset/unreachable) ---
REDIS_URL=redis://localhost:6379/0
CACHE_TTL_SECONDS=86400

# --- Database (taxonomy) ---
DATABASE_URL=postgresql+psycopg://postgres:postgres@localhost:5432/ics

# --- Security ---
API_KEYS=devkey1,devkey2       # comma-separated; hashed/compared server-side
JWT_SECRET=change-me-in-prod
JWT_ALGORITHM=HS256
```

---

## 3.5 Version Control Workflow

**Recommended: Trunk-Based Development with short-lived feature branches.**

- `main` is always releasable; protected (PR + green CI + 1 review required).
- Feature branches are small and short-lived (< a few days), merged via squash.
- Releases cut from `main` via tags (`v1.2.0`); CI/CD promotes the built digest.

**Rationale:** the team is small and deploys are continuous to Cloud Run. Trunk-based
keeps integration frequent (fewer merge conflicts, faster feedback) and pairs naturally
with revision-based rollback. Gitflow's long-lived `develop`/`release` branches add
ceremony this project doesn't need. The ML model is versioned **separately** (artifact
+ `MODEL_VERSION`), decoupled from code releases.

---

## 3.6 Common Pitfalls (this stack)

| Pitfall | Mitigation |
| --- | --- |
| **CLIP preprocessing mismatch** | The single most common bug. Training and serving **must** use identical resize, center-crop, and normalization (CLIP mean/std). Centralize in `preprocessing.py` and reuse it in the training pipeline. |
| **ONNX export drift** | Always run a parity check (PyTorch vs ONNX, `atol=1e-3`) right after export and in CI. Pin `opset` version. |
| **Dynamic batch axis** | Export with a dynamic batch dimension or `/classify/batch` will fail/recompile. Declare `dynamic_axes` in `torch.onnx.export`. |
| **Multi-label ≠ softmax** | Use **sigmoid + per-label thresholds**, not softmax/argmax. Calibrate thresholds per class; store in `thresholds.json`. |
| **Cold starts on Cloud Run** | Loading ONNX per request kills latency. Load once in the FastAPI lifespan; set `min-instances ≥ 1`. |
| **Fat serving image** | Don't ship torch/transformers to production. Split runtime vs training requirements. |
| **Redis as a hard dependency** | Treat cache as optional — on Redis error, log and fall through to inference. Never 500 because the cache is down. |
| **Decompression bombs** | Cap `Image.MAX_IMAGE_PIXELS`, enforce max upload size and dimensions before decode. |
| **Thread/oversubscription** | ONNX Runtime intra-op threads × uvicorn workers can oversubscribe CPU. Tune both to the container's vCPU count. |
| **WIF misconfiguration** | Workload Identity Federation setup is fiddly; document the pool/provider/SA binding in `infrastructure/README.md`. |
| **Connection pool exhaustion** | Cloud Run scales instances; cap SQLAlchemy pool size per instance and use Cloud SQL connector limits. |
