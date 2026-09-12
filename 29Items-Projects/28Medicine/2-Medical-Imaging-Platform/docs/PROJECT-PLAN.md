# Medical Imaging Platform — Project Plan

> A HIPAA-aligned PACS (Picture Archiving and Communication System) for radiology
> image management, DICOM archiving, web-based viewing, and ML-assisted chest
> X-ray diagnostics.

| | |
|---|---|
| **Domain** | Radiology / Medical Imaging (PACS) |
| **Backend** | Python 3.12, FastAPI, pydicom |
| **Datastores** | PostgreSQL (metadata), MinIO / AWS S3 (object archive) |
| **Frontend** | React 18, TypeScript, Cornerstone.js (Cornerstone3D) |
| **ML** | Chest X-ray classification (DenseNet-121 / CheXNet-style) |
| **Cloud** | AWS (HIPAA-eligible services, signed BAA) |
| **CI/CD** | GitHub Actions |
| **Interop** | DICOMweb — QIDO-RS, WADO-RS, STOW-RS |

---

## 1.1 Project File Structure

The repository is a **polyglot monorepo**. Three deployable units (`backend`,
`frontend`, `ml`) plus shared `infrastructure` and `docs`. Each unit owns its
dependency manifest and Dockerfile so it can be built and scaled independently.

```text
2-Medical-Imaging-Platform/
│
├── README.md                      # Repo overview, quick-start
├── docker-compose.yml             # Local dev: api, worker, postgres, minio, frontend
├── Makefile                       # Common dev commands (up, test, lint, migrate)
├── .gitignore
├── .pre-commit-config.yaml        # Ruff, black, mypy, eslint, detect-secrets
├── Opus-4.8.txt                   # Model marker file (this generation)
│
├── docs/                          # ── Documentation ─────────────────────────
│   ├── PROJECT-PLAN.md            #   This file
│   ├── ARCHITECTURE.md            #   System design + Mermaid diagrams
│   └── TECH-NOTES.md              #   CI/CD, testing, deploy, pitfalls
│
├── backend/                       # ── FastAPI service + async workers ───────
│   ├── pyproject.toml             #   Project metadata + tool config (ruff, mypy)
│   ├── requirements.txt           #   Pinned runtime deps
│   ├── requirements-dev.txt       #   Test/lint deps
│   ├── Dockerfile                 #   Multi-stage, non-root, distroless-ish
│   ├── alembic.ini                #   Migration runner config
│   ├── .env.example               #   Documented env contract
│   ├── app/
│   │   ├── main.py                #   ASGI app factory, middleware, routers
│   │   ├── core/                  #   Cross-cutting concerns
│   │   │   ├── config.py          #     Pydantic Settings (12-factor)
│   │   │   ├── security.py        #     JWT, password hashing, RBAC scopes
│   │   │   ├── logging.py         #     Structured JSON logging + PHI redaction
│   │   │   └── exceptions.py      #     Typed domain errors + handlers
│   │   ├── api/
│   │   │   ├── deps.py            #   FastAPI dependencies (auth, db session)
│   │   │   └── v1/
│   │   │       ├── router.py      #   Aggregates all v1 endpoints
│   │   │       └── endpoints/
│   │   │           ├── auth.py        # Login / token refresh
│   │   │           ├── studies.py     # Worklist CRUD (REST)
│   │   │           ├── instances.py   # Instance metadata + frame access
│   │   │           ├── dicomweb.py    # QIDO-RS / WADO-RS / STOW-RS
│   │   │           ├── ml.py          # Trigger + fetch ML predictions
│   │   │           └── health.py      # Liveness / readiness probes
│   │   ├── models/                #   SQLAlchemy ORM (DICOM information model)
│   │   │   ├── base.py
│   │   │   ├── patient.py
│   │   │   ├── study.py
│   │   │   ├── series.py
│   │   │   ├── instance.py
│   │   │   ├── ml_result.py
│   │   │   ├── user.py
│   │   │   └── audit.py           #     PHI access audit trail (HIPAA)
│   │   ├── schemas/               #   Pydantic request/response contracts
│   │   │   ├── study.py
│   │   │   ├── series.py
│   │   │   ├── instance.py
│   │   │   ├── ml.py
│   │   │   └── user.py
│   │   ├── services/              #   Business logic (framework-agnostic)
│   │   │   ├── dicom_service.py        # pydicom parse / metadata extraction
│   │   │   ├── storage_service.py      # MinIO/S3 object I/O
│   │   │   ├── ingestion_service.py    # Orchestrates ingest pipeline
│   │   │   ├── deidentify_service.py   # PHI scrubbing for research/ML
│   │   │   └── ml_service.py           # ML inference client
│   │   ├── db/
│   │   │   ├── session.py         #   Async engine + session factory
│   │   │   └── init_db.py         #   Bootstrap (seed admin, buckets)
│   │   └── workers/
│   │       └── ingestion_worker.py    # SQS-driven DICOM processing
│   ├── migrations/                #   Alembic
│   │   ├── env.py
│   │   ├── script.py.mako
│   │   └── versions/
│   │       └── 0001_initial_schema.py
│   └── tests/
│       ├── conftest.py
│       ├── test_dicom_service.py
│       └── test_studies_api.py
│
├── frontend/                      # ── React + TypeScript viewer ─────────────
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   ├── .eslintrc.cjs
│   ├── .prettierrc
│   ├── .env.example
│   ├── Dockerfile                 #   Build static assets → nginx
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── api/client.ts          #   Typed API/DICOMweb client (axios)
│       ├── types/dicom.ts         #   Shared DTO types
│       ├── hooks/useStudies.ts    #   TanStack Query data hook
│       ├── components/
│       │   ├── StudyList.tsx       #  Worklist table (loading/error/empty)
│       │   ├── DicomViewer.tsx     #  Cornerstone3D viewport
│       │   └── MlResultsPanel.tsx  #  Classification overlay
│       └── pages/ViewerPage.tsx
│
├── ml/                            # ── Diagnostic ML service ─────────────────
│   ├── README.md
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── inference/
│   │   └── chest_xray_classifier.py   # Model load + predict (stub)
│   └── models/.gitkeep            #   Weights pulled from S3 at deploy
│
├── infrastructure/                # ── IaC ───────────────────────────────────
│   ├── terraform/
│   │   ├── main.tf                #   VPC, RDS, S3, ECS, ALB, KMS, Cognito
│   │   ├── variables.tf
│   │   └── outputs.tf
│   └── docker/
│       └── nginx.conf            #   Frontend reverse proxy / security headers
│
├── scripts/
│   └── seed_sample_dicom.py       #   Dev helper: push sample studies
│
└── .github/workflows/             # ── CI/CD ─────────────────────────────────
    ├── backend-ci.yml             #   Lint → type → test → build → scan
    ├── frontend-ci.yml            #   Lint → type → test → build
    ├── ml-ci.yml                  #   Lint → test → build inference image
    └── deploy.yml                 #   OIDC → ECR push → ECS deploy (staging/prod)
```

### Design rationale for the layout

- **Layered backend (`api` → `services` → `models`/`db`).** Endpoints stay thin
  (HTTP + validation); business rules live in `services` and are unit-testable
  without a web server; persistence is isolated behind the ORM. This keeps the
  DICOM/PACS domain logic portable if the transport layer ever changes.
- **DICOM information model is first-class.** `Patient → Study → Series →
  Instance` is the backbone of every PACS; modelling it explicitly (not as a flat
  "images" table) is what makes QIDO-RS queries and worklists efficient.
- **Ingestion is separated from the request path.** Parsing a DICOM file, writing
  it to object storage, and running ML are slow/CPU-bound. They run in workers so
  the API stays responsive and the two halves scale on different signals.
- **`ml` is its own unit.** Model weights, CUDA, and heavy frameworks have a
  different lifecycle and hardware profile (GPU) than the API — separate image,
  separate scaling group.

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (High priority) 🔴 — ✅ complete

- [x] Scaffold repo, `docker-compose` (Postgres + MinIO + API + worker + ML + web)
- [x] `core/config.py` settings contract + `.env.example`
- [x] SQLAlchemy models for Patient/Study/Series/Instance + Alembic `0001`
- [x] `storage_service`: MinIO/S3 put/get, bucket bootstrap, presigned URLs
- [x] `dicom_service`: pydicom parse → metadata dict, mandatory-tag validation
- [x] STOW-RS upload endpoint → ingest (inline or queue) → `ingestion_worker`
- [x] AuthN: OAuth2 password/JWT, password hashing, `User` model
- [x] RBAC scopes (radiologist / technologist / referring / admin)
- [x] Health/readiness endpoints; structured JSON logging w/ PHI redaction
- [x] CI: backend lint + type + unit tests green on every PR

### Phase 2 — Core Features (Medium priority) 🟡 — ✅ complete

- [x] QIDO-RS study/series/instance search (paging, patient/modality/date filters)
- [x] WADO-RS retrieve (Part-10) + REST series/instance listing + presigned frames
- [x] Frontend StudyList worklist (TanStack Query, loading/error/empty states)
- [x] Cornerstone3D viewer: stack scroll, window/level, zoom/pan
- [x] De-identification service (DICOM PS3.15 basic-profile subset)
- [x] ML pipeline: chest X-rays → classifier service → `ml_result` rows
- [x] ML results overlay panel in viewer (per-pathology probabilities)
- [x] Audit logging of every PHI read/write (who/what/when/where)
- [x] Test suite on in-memory SQLite + moto (70 tests, 86% coverage)
- [x] Frontend CI + ML CI pipelines
- [ ] Measurement tools + CAM heatmap overlay (storage/schema ready; UI pending)
- [ ] Testcontainers integration suite against real Postgres + MinIO

### Phase 3 — Polish & Optimization (Lower priority) 🟢

- [x] Terraform scaffold: VPC, RDS, S3 + KMS, ECS, ALB, Cognito, SQS, lifecycle
- [x] `deploy.yml`: GitHub OIDC → ECR → ECS (blue/green steps stubbed)
- [x] S3 lifecycle → Intelligent-Tiering / Glacier (in `infrastructure/terraform`)
- [ ] RDS read replica for worklist query load
- [ ] Thumbnail / multi-frame pre-rendering + CDN caching of frames
- [ ] OpenTelemetry traces → X-Ray; dashboards + alerts (CloudWatch)
- [ ] E2E tests (Playwright) on a synthetic-DICOM seeded staging env
- [ ] Load testing (Locust) of STOW/QIDO/WADO paths
- [ ] DICOM C-STORE SCP bridge (accept pushes from modalities/legacy PACS)
- [ ] Disaster-recovery runbook + cross-region S3 replication

---

## 1.3 Definition of Done (per feature)

A feature is "done" when: code reviewed & merged · unit + integration tests pass
· no new high/critical SAST or dependency findings · audit logging covers any new
PHI access · API documented in OpenAPI · runbook/README updated · deployed to
staging and smoke-tested.
