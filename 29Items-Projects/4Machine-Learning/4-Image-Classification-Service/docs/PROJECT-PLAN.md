# Project Plan — Image Classification Service

> **Vision Transformer (ViT) image classifier for e-commerce product catalog management.**
> Transfer learning from CLIP, multi-label classification, ONNX-optimized inference,
> Redis caching, served via FastAPI, with a React operator UI. Deployed to Google
> Cloud Run via GitHub Actions and Artifact Registry.

---

## 1. Overview

| Property | Value |
| --- | --- |
| **Domain** | E-commerce product image categorization |
| **Model** | Vision Transformer fine-tuned from CLIP (`openai/clip-vit-base-patch32`) |
| **Task type** | Multi-label classification (a product can belong to several categories) |
| **Serving** | ONNX Runtime behind FastAPI |
| **Cache** | Redis (content-addressed by image hash) |
| **Frontend** | React + TypeScript + Vite operator console |
| **Cloud** | Google Cloud Run + Artifact Registry |
| **CI/CD** | GitHub Actions |

The service accepts product images and returns a ranked list of category labels with
confidence scores. Repeated images (same SKU re-uploaded, batch re-runs) are served
from Redis to keep p99 latency low and GPU/CPU cost down.

---

## 2. Project File Structure

```text
4-Image-Classification-Service/
│
├── docs/                              # Architecture & planning docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── backend/                          # Python / FastAPI inference service
│   ├── app/
│   │   ├── main.py                   # FastAPI application factory & startup
│   │   ├── api/
│   │   │   ├── deps.py               # Shared FastAPI dependencies (DI)
│   │   │   └── routes/
│   │   │       ├── classification.py # POST /classify (core inference)
│   │   │       ├── categories.py     # CRUD for category taxonomy
│   │   │       └── health.py         # Liveness / readiness probes
│   │   ├── core/
│   │   │   ├── config.py             # Pydantic Settings (12-factor config)
│   │   │   ├── logging.py            # Structured JSON logging setup
│   │   │   └── security.py           # API-key / JWT auth helpers
│   │   ├── models/
│   │   │   ├── schemas.py            # Pydantic request/response DTOs
│   │   │   └── db_models.py          # SQLAlchemy ORM models
│   │   ├── services/
│   │   │   ├── inference.py          # ONNX Runtime session + predict()
│   │   │   ├── preprocessing.py      # Image decode / resize / normalize
│   │   │   └── cache.py             # Redis read-through cache
│   │   ├── ml/                       # Offline training & export pipeline
│   │   │   ├── model.py              # ViT/CLIP model definition
│   │   │   ├── dataset.py            # Dataset + dataloaders
│   │   │   ├── train.py              # Fine-tuning entrypoint
│   │   │   └── export_onnx.py        # PyTorch -> ONNX + validation
│   │   └── db/
│   │       └── session.py            # Engine / session factory
│   ├── tests/
│   │   ├── conftest.py               # Fixtures (test client, fakes)
│   │   ├── test_classification.py    # API contract tests
│   │   ├── test_inference.py         # Inference service unit tests
│   │   └── test_cache.py             # Cache behavior tests
│   ├── migrations/                   # Alembic database migrations
│   │   ├── env.py
│   │   └── versions/
│   │       └── 0001_initial.py
│   ├── alembic.ini
│   ├── pyproject.toml                # Tooling config (ruff, black, mypy, pytest)
│   ├── requirements.txt              # Runtime dependencies
│   ├── requirements-dev.txt          # Dev/test dependencies
│   ├── Dockerfile                    # Multi-stage production image
│   ├── .dockerignore
│   └── .env.example
│
├── frontend/                         # React operator console
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── api/
│   │   │   └── client.ts             # Typed fetch client
│   │   ├── hooks/
│   │   │   └── useClassify.ts        # Data-fetching hook
│   │   └── components/
│   │       ├── ImageClassifier.tsx   # Upload + results widget
│   │       └── ResultDisplay.tsx     # Label/confidence rendering
│   ├── public/
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── .eslintrc.cjs
│   ├── .prettierrc
│   ├── Dockerfile
│   └── .env.example
│
├── .github/
│   └── workflows/
│       ├── ci.yml                    # Lint + test + build on PR
│       └── deploy.yml                # Build image -> push -> deploy Cloud Run
│
├── infrastructure/
│   ├── cloudrun-service.yaml         # Cloud Run service (Knative) spec
│   └── README.md                     # GCP setup / WIF notes
│
├── scripts/
│   └── smoke_test.sh                 # Post-deploy smoke check
│
├── docker-compose.yml                # Local dev: api + redis + db + frontend
├── .gitignore
├── README.md
└── claude-opus-4-8.txt               # Model marker file
```

### Structure rationale

- **`backend/app/ml` is isolated from serving code.** Training/export pull in heavy
  PyTorch + `transformers`; serving only needs `onnxruntime`. Keeping them in separate
  modules lets the production image install a slimmer dependency set.
- **`services/` (business logic) vs `api/routes/` (transport).** Routes stay thin;
  testable logic lives in services so it can be unit-tested without HTTP.
- **`core/config.py` centralizes 12-factor configuration** so nothing reads `os.environ`
  ad hoc.
- **Frontend and backend are independently deployable** but co-located in one repo
  (monorepo) for atomic PRs and shared CI.

---

## 3. Implementation TODO List

> **Status:** Phases 1 & 2 complete; Phase 3 largely complete. Remaining items
> (OpenTelemetry traces, scheduled drift job, load testing, Playwright E2E) are
> explicitly out-of-scope operational follow-ups.

### ✅ Phase 1 — Foundation (High Priority) — DONE

- [x] Scaffold repo structure, `pyproject.toml`, linters
- [x] FastAPI app skeleton with `/health` (liveness) and `/ready` (readiness)
- [x] `core/config.py` — typed settings from env vars
- [x] Structured JSON logging with request-id correlation
- [x] Image preprocessing service (decode → resize 224×224 → normalize)
- [x] ONNX Runtime inference service wrapping the exported model
- [x] `POST /classify` endpoint with Pydantic validation (size/type limits)
- [x] Redis read-through cache keyed by SHA-256 image hash (graceful degradation)
- [x] Dockerfile (multi-stage) + `docker-compose.yml` for local dev
- [x] Unit + API tests; CI workflow (ruff, mypy, pytest) — 52 tests, 84% coverage

### ✅ Phase 2 — Core Features (Medium Priority) — DONE

- [x] Training pipeline: CLIP-style ViT + multi-label head (`ml/train.py`)
- [x] Dataset loader (real JSONL manifest + synthetic generator for CI)
- [x] Threshold calibration per label (multi-label decision thresholds)
- [x] `export_onnx.py` with parity check (PyTorch vs ONNX logits within tolerance)
- [x] Model versioning + load on startup (artifact path/`MODEL_VERSION`)
- [x] Category taxonomy CRUD (`/categories`) backed by Postgres + Alembic
- [x] AuthN/AuthZ: API keys for service clients, JWT + scopes for the console
- [x] Batch classification endpoint (`POST /classify/batch`, single batched forward)
- [x] React console: login, upload/drag-drop, preview, results, confidence bars
- [x] Deploy workflow to Cloud Run with Workload Identity Federation (no JSON keys)

### 🟢 Phase 3 — Polish & Optimization (Lower Priority)

- [x] ONNX dynamic quantization (INT8) via `export_onnx.py --quantize`
- [x] Prometheus metrics at `/metrics` (latency, cache-hit, model gauge, requests)
- [x] Canary / gradual traffic rollout on Cloud Run revisions (deploy workflow)
- [x] Prediction audit log (foundation for drift monitoring / active learning)
- [x] Rate limiting & abuse protection (token-bucket middleware, per key/IP)
- [ ] OpenTelemetry traces → Cloud Trace (operational follow-up)
- [ ] Scheduled drift re-evaluation job (operational follow-up)
- [ ] Load testing (Locust/k6) + autoscaling tuning (operational follow-up)
- [ ] E2E tests (Playwright) against a staging deployment (operational follow-up)

---

## 4. Milestones & Definition of Done

| Milestone | Definition of Done |
| --- | --- |
| **M1: Walking skeleton** | `/classify` returns mock predictions; CI green; runs in Docker Compose |
| **M2: Real model** | Fine-tuned ViT exported to ONNX; parity test passes; served from Cloud Run |
| **M3: Production hardening** | Auth, caching, taxonomy CRUD, metrics, staging→prod promotion |
| **M4: Optimization** | Quantized model meets latency SLO; drift monitoring live; E2E suite green |

## 5. Non-Functional Targets (initial SLOs)

| Metric | Target |
| --- | --- |
| p50 latency (cache miss, single image) | ≤ 250 ms |
| p99 latency (cache miss) | ≤ 800 ms |
| Cache-hit latency | ≤ 25 ms |
| Availability | 99.5% |
| Top-1 category accuracy (offline eval) | ≥ 0.90 |
| Macro-F1 (multi-label) | ≥ 0.80 |
