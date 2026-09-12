# Medical Imaging Platform

A HIPAA-aligned **PACS** (Picture Archiving and Communication System) for
radiology: DICOM ingestion and archiving, a web-based viewer, and ML-assisted
chest X-ray diagnostics.

> ⚠️ Built to be deployed under a signed **AWS BAA** using HIPAA-eligible
> services. The ML output is **decision support only**, not a diagnosis — and the
> bundled classifier is a **placeholder** (deterministic, **not trained on medical
> data**); provide real trained weights before any clinical use.

## Stack

| Layer | Technology |
|---|---|
| Backend | Python 3.12 · FastAPI · pydicom · SQLAlchemy 2 (async) · Alembic |
| Datastores | PostgreSQL (metadata + audit) · MinIO / S3 (image archive) |
| Frontend | React 18 · TypeScript · Cornerstone3D · TanStack Query · React Router |
| ML | Chest X-ray classifier (DenseNet-121 / CheXNet lineage), separate service |
| Interop | DICOMweb — QIDO-RS · WADO-RS · STOW-RS |
| Cloud / CI | AWS (ECS, RDS, S3, SQS, KMS) · GitHub Actions · Terraform |

## Architecture in one line

A modular FastAPI app on the request path + queue-driven workers for heavy
ingestion/ML, so the API stays fast while parsing and inference scale
independently. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## What works

- **Auth**: OAuth2 password grant → JWT, RBAC scopes (radiologist / technologist
  / referring / admin), admin-gated user creation.
- **Ingestion**: STOW-RS upload (single or `multipart/related`) → pydicom parse →
  optional de-identification → archive to S3/MinIO → idempotent upsert of the
  Patient→Study→Series→Instance hierarchy → audit → chest X-ray ML.
- **Query/retrieve**: REST worklist (filters, paging, series counts) + DICOMweb
  QIDO-RS / WADO-RS. Presigned URLs for direct, time-boxed pixel access.
- **ML**: chest X-ray classifier service (14 ChestX-ray14 labels), inline or
  queue-driven; results stored and surfaced in the viewer. **The bundled model is
  a placeholder** — deterministic and reproducible from the image pixels, but
  **not trained on medical data**, so its scores are illustrative only, *not
  clinically meaningful*. The pipeline (decode → preprocess → infer → persist →
  display) is fully real; drop in trained DenseNet-121 weights via
  `ML_MODEL_S3_URI` for genuine inference — preprocessing and the I/O contract
  already match (see `ml/inference/chest_xray_classifier.py`).
- **HIPAA**: append-only audit trail on every PHI access, PHI-redacted structured
  logs, encryption-at-rest (KMS on real S3), least-privilege IaC.

## Repository layout

```
backend/        FastAPI app + async worker + Alembic migrations + tests
frontend/       React/TypeScript DICOM viewer (Cornerstone3D)
ml/             Chest X-ray inference microservice (FastAPI)
infrastructure/ Terraform (AWS) + nginx config
.github/        CI/CD pipelines (backend, frontend, ml, deploy)
docs/           PROJECT-PLAN.md · ARCHITECTURE.md · TECH-NOTES.md
scripts/        Dev helpers (synthetic DICOM seeding)
```

## Quick start (Docker)

```bash
docker compose up --build
```

This starts Postgres, MinIO, the API (runs migrations + seeds dev users on boot),
the ML service, an ingestion worker, and the frontend.

- Frontend: http://localhost:5173
- API + Swagger UI: http://localhost:8000/docs
- MinIO console: http://localhost:9001 (`minioadmin` / `minioadmin`)
- ML service: http://localhost:8001/docs

**Dev login** (seeded automatically, non-production only):

| Email | Password | Role |
|---|---|---|
| `radiologist@medimaging.local` | `radiology123` | radiologist |
| `admin@medimaging.local` | `admin12345` | admin |

Seed some synthetic studies (no PHI):

```bash
python scripts/seed_sample_dicom.py --count 5 --api http://localhost:8000
```

> **Local viewer note:** in Docker, presigned image URLs are generated for the
> internal MinIO host (`minio:9000`), which a browser on the host can't resolve.
> The full pipeline (upload → archive → metadata → ML → worklist → API) works in
> compose; in-browser *pixel rendering* against local MinIO needs the presign
> host to be browser-reachable. Against real AWS S3 (production) this is a
> non-issue. For pure local pixel viewing, run the backend on the host (below).

### Data persistence vs. slow Docker storage

`docker-compose.override.yml` (auto-merged by Compose) runs the **Postgres and
MinIO data dirs in tmpfs (RAM)**. This was added because Docker's storage was on
a slow disk (flash), where Postgres's first-run `initdb`/server start stalled on
fsync and MinIO object I/O made uploads take ~70 s. With tmpfs, init is instant
and uploads are sub-second — but the data is **ephemeral** (reset on every `up`).

- **Fast disk (recommended):** if Docker's data root is on a fast drive (e.g. the
  `C:` drive or a USB SSD), **delete `docker-compose.override.yml`** to use the
  persistent named volumes (`pgdata`, `miniodata`) — studies/users then survive
  restarts. (Move Docker Desktop's disk image in *Settings → Resources →
  Advanced → Disk image location*.)
- **Slow disk:** keep the override (ephemeral but fast), and re-seed after each
  `up` with the script above.

## Local development (without Docker)

```bash
# Backend
cd backend
python -m venv .venv && . .venv/Scripts/activate   # or .venv/bin/activate
pip install -r requirements-dev.txt
alembic upgrade head            # against a running Postgres (see .env)
uvicorn app.main:app --reload   # http://localhost:8000/docs

# ML service
cd ml && pip install -r requirements.txt
uvicorn inference.server:app --port 8001

# Frontend
cd frontend && npm install && npm run dev   # http://localhost:5173
```

## Running tests

```bash
cd backend
pip install -r requirements-dev.txt
pytest                  # 70 tests, ~86% coverage (gate: 80%)
```

The suite runs entirely on in-memory SQLite + a mocked S3 (moto) — **no Docker,
Postgres, or MinIO required**. Lint/type:

```bash
ruff check .  &&  black --check .  &&  mypy app
```

## API documentation

Interactive OpenAPI docs are served at `/docs` (Swagger UI) and `/redoc`; the raw
schema is at `/openapi.json`.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| **App refuses to start**: *"Refusing to start in production with insecure default secrets"* | By design. With `APP_ENV=production` the app rejects placeholder secrets (`JWT_SECRET_KEY`, `POSTGRES_PASSWORD`, `S3_ACCESS_KEY`/`S3_SECRET_KEY`) to prevent signing tokens with a public key. Set real values via env / AWS Secrets Manager. |
| **Images don't render in the browser (Docker)** | Presigned URLs target the internal `minio:9000` host the browser can't resolve. Run the backend on the host for local pixel viewing, or use real S3 (see *Local viewer note*). The rest of the pipeline still works in compose. |
| **Postgres unhealthy / uploads take ~70 s on first boot** | Docker's data root is on a slow disk. The bundled `docker-compose.override.yml` runs the data dirs in tmpfs (RAM); see *Data persistence vs. slow Docker storage*. |
| **`401 Unauthorized` on every request** | Token expired or malformed — re-authenticate at `POST /api/v1/auth/token`. Tokens are short-lived (30 min by default). |
| **Tests can't find Postgres/MinIO** | They shouldn't need them — the suite runs on in-memory SQLite + mocked S3. Run `pytest` from `backend/` with `requirements-dev.txt` installed. |

## Documentation

- 📋 [Project Plan](docs/PROJECT-PLAN.md) — structure + phased TODO list
- 🏛️ [Architecture](docs/ARCHITECTURE.md) — patterns, data flow, security (Mermaid)
- 🛠️ [Technical Notes](docs/TECH-NOTES.md) — CI/CD, testing, deploy, pitfalls

## Compliance notes

PHI is encrypted at rest (KMS) and in transit (TLS); every PHI access is audited;
logs are PHI-redacted; de-identification (PS3.15 subset) guards research/ML data
paths. Use synthetic data only outside production. See `docs/ARCHITECTURE.md` §2.6.
