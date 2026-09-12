# Image Classification Service

Multi-label **product image categorization** for e-commerce catalog management.
A CLIP-style Vision Transformer (ViT) trained on a custom taxonomy, exported to
**ONNX** for fast inference, served by **FastAPI**, cached in **Redis**, with a
**React + TypeScript** operator console. Deployed to **Google Cloud Run** via GitHub
Actions + Artifact Registry.

> **Status: fully implemented and working.** Real ML pipeline (train → export →
> parity → serve), JWT + API-key auth, taxonomy CRUD, Redis caching, Prometheus
> metrics, rate limiting, and a complete console UI. 52 backend tests (84% coverage)
> and 6 frontend tests pass.

## Architecture at a glance

```
React console ─┐                              ┌─ Redis cache (read-through, optional)
               ├─HTTPS─▶ FastAPI (Cloud Run) ─┤
catalog jobs ──┘            │  │  │           └─ ONNX Runtime (ViT multi-label)
                            │  │  └─ Postgres (taxonomy + prediction log)
                            │  └──── Prometheus /metrics
                       offline: app/ml (train.py → export_onnx.py → model.onnx)
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for diagrams and rationale,
[`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) for structure + roadmap, and
[`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) for CI/CD, testing, and pitfalls.

## Quick start

### Option A — Docker (full stack, self-bootstrapping model)

```bash
docker compose up --build
# model-init trains+exports a small model into a shared volume, then:
#   api       -> http://localhost:8080  (docs at /docs)
#   frontend  -> http://localhost:5173
```

`model-init` runs once to produce a working ONNX model, so the API starts **ready**.

### Option B — Local (Python + Node)

```bash
# 1. Build a model artifact (synthetic training, no data needed, ~10s)
cd backend && pip install -r requirements-dev.txt
python ../scripts/bootstrap_model.py --out ../models --arch small --epochs 2

# 2. Run the API (SQLite + no Redis is fine for local)
export MODEL_PATH=../models/vit_clip_v1.onnx \
       LABELS_PATH=../models/labels.json \
       THRESHOLDS_PATH=../models/thresholds.json \
       DATABASE_URL=sqlite:///./dev.db
uvicorn app.main:app --reload --port 8080

# 3. Run the console (separate terminal)
cd frontend && npm install && npm run dev   # http://localhost:5173
```

## Using the API

```bash
# Get a console token (demo creds)
TOKEN=$(curl -s -X POST localhost:8080/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .access_token)

# Classify an image
curl -F file=@product.jpg localhost:8080/classify -H "Authorization: Bearer $TOKEN"

# Manage taxonomy
curl -X POST localhost:8080/categories -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"electronics"}'
```

Service-to-service clients can use an API key instead of a token: set `API_KEYS=...`
and send `X-API-Key: <key>`.

## API reference

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/auth/token` | none | Issue a console JWT (demo login) |
| `POST` | `/classify` | key/JWT `classify` | Classify one image (multipart `file`) |
| `POST` | `/classify/batch` | key/JWT `classify` | Classify many (single batched forward pass) |
| `GET` | `/categories` | key/JWT `taxonomy:read` | List taxonomy (paginated) |
| `POST` | `/categories` | key/JWT `taxonomy:write` | Create a category |
| `GET` | `/categories/{id}` | key/JWT `taxonomy:read` | Get one |
| `PUT` | `/categories/{id}` | key/JWT `taxonomy:write` | Update |
| `DELETE` | `/categories/{id}` | key/JWT `taxonomy:write` | Delete |
| `GET` | `/health` / `/ready` | none | Liveness / readiness probes |
| `GET` | `/metrics` | none | Prometheus metrics |

Interactive OpenAPI docs are served at `/docs` (Swagger) and `/redoc`.

## Testing

```bash
# Backend: lint, types, tests (uses fakes — no model/Redis/Postgres needed)
cd backend
ruff check . && ruff format --check . && mypy app && pytest

# Frontend: lint, build, unit tests
cd frontend
npm run lint && npm run build && npm test
```

## ML pipeline

```bash
cd backend
# Train (synthetic or real JSONL manifests), with the CLIP-style ViT
python -m app.ml.train --synthetic --arch small --epochs 3 --out artifacts/
python -m app.ml.train --train data/train.jsonl --val data/val.jsonl \
    --labels data/labels.json --arch base --pretrained --out artifacts/

# Export to ONNX + parity check (+ optional INT8 quantization)
python -m app.ml.export_onnx --weights artifacts/model.pt \
    --labels artifacts/labels.json --arch base --out artifacts/vit_clip_v1.onnx --quantize
```

The model is a self-contained PyTorch implementation of the CLIP ViT architecture
(`app/ml/model.py`), so the whole pipeline runs offline with only `torch`.
`load_pretrained_clip()` optionally copies real CLIP weights when `transformers` is
installed.

## Layout

| Path | What |
| --- | --- |
| `backend/app/api` | FastAPI routes (classify, categories, auth, health) + DI |
| `backend/app/services` | inference, preprocessing, cache, prediction log |
| `backend/app/core` | config, logging, security (JWT/keys), metrics, rate limit |
| `backend/app/ml` | training + ONNX export pipeline (model, dataset, metrics) |
| `frontend/src` | React console (auth, classify, taxonomy management) |
| `.github/workflows` | CI (lint/type/test/build) + Cloud Run deploy |
| `infrastructure` | Cloud Run spec + GCP/WIF setup |
| `scripts/bootstrap_model.py` | one-command model artifact builder |
