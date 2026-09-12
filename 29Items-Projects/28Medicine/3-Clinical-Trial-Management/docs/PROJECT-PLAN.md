# Clinical Trial Management System (CTMS) — Project Plan

> **Domain:** Clinical trial protocol management, patient enrollment, electronic
> data capture (EDC), and ML-assisted eligibility screening.
> **Regulatory envelope:** FDA **21 CFR Part 11** (electronic records / signatures),
> **HIPAA**, **ICH-GCP (E6 R2)**, and **FedRAMP** controls (AWS **GovCloud**).
> **Tech stack:** Python · Django · Django REST Framework · PostgreSQL · Celery ·
> React · TypeScript.

---

## 1. Overview

The CTMS is a **modular Django monolith** with an **event-driven asynchronous
tier** (Celery) and a **separable ML inference service** for NLP-based eligibility
screening. The system is built "compliance-first": every clinical data mutation
is wrapped in an immutable, computer-generated audit trail, and the ML layer is
**advisory only** — a human (clinical research coordinator / investigator) always
confirms eligibility decisions.

> ⚠️ **Regulatory note:** In a regulated trial the software must be *validated*
> (CSV — Computer System Validation, IQ/OQ/PQ). This plan treats validation
> artifacts (traceability matrix, validation protocols) as first-class
> deliverables, not afterthoughts.

### Primary actors

| Actor | Role |
|-------|------|
| **Sponsor / CRO** | Owns the trial, defines protocols, monitors enrollment. |
| **Principal Investigator (PI)** | Medically responsible at a site; signs eligibility & key data. |
| **Clinical Research Coordinator (CRC)** | Day-to-day enrollment, eCRF data entry. |
| **Clinical Data Manager (CDM)** | Data quality, query management, locks. |
| **Monitor / CRA** | Source data verification, audit review. |
| **System / Auditor** | Read-only audit access; never mutates clinical data. |

---

## 2. Project File Structure

```text
3-Clinical-Trial-Management/
├── .github/
│   └── workflows/
│       ├── ci.yml                      # lint → type-check → test → SAST → build
│       ├── cd-production.yml           # gated deploy to AWS GovCloud (OIDC)
│       └── security-scan.yml           # Trivy / pip-audit / npm-audit / CodeQL
│
├── backend/
│   ├── manage.py
│   ├── pyproject.toml                  # ruff + black + mypy + pytest config
│   ├── Dockerfile                      # multi-stage, non-root, distroless-ish
│   ├── requirements/
│   │   ├── base.txt                    # runtime deps
│   │   ├── dev.txt                     # test/lint deps  (-r base.txt)
│   │   └── prod.txt                    # gunicorn/uvicorn, observability
│   │
│   ├── config/                         # Django *project* (not an app)
│   │   ├── __init__.py
│   │   ├── celery.py                   # Celery app + beat schedule
│   │   ├── urls.py                     # root URL conf + API versioning
│   │   ├── wsgi.py / asgi.py
│   │   └── settings/
│   │       ├── base.py                 # 12-factor base (env-driven)
│   │       ├── development.py
│   │       ├── staging.py
│   │       ├── production.py           # GovCloud, KMS, strict security headers
│   │       └── test.py
│   │
│   ├── apps/                           # bounded contexts (modular monolith)
│   │   ├── accounts/                   # users, RBAC roles, MFA, e-signature creds
│   │   ├── audit/                      # 21 CFR Part 11 audit trail (append-only)
│   │   ├── trials/                     # protocols, studies, arms, visit schedule
│   │   ├── sites/                      # investigative sites, delegation logs
│   │   ├── patients/                   # subjects (PHI-bearing, encrypted)
│   │   ├── enrollment/                 # screening → consent → randomization
│   │   ├── ecrf/                       # electronic Case Report Forms, queries
│   │   ├── eligibility/               # ML screening orchestration + results
│   │   ├── notifications/             # email/in-app, visit & SAE reminders
│   │   └── common/                     # shared base models, mixins, perms
│   │
│   ├── ml/                             # NLP eligibility pipeline (model code)
│   │   ├── pipelines/
│   │   │   └── eligibility_screening.py
│   │   ├── features/                   # feature extraction (concept mapping)
│   │   ├── models/                     # model artifacts / loaders (DVC-tracked)
│   │   └── serving/                    # inference adapters (local / SageMaker)
│   │
│   └── tests/                          # cross-app integration & e2e fixtures
│       ├── unit/
│       └── integration/
│
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── .eslintrc.cjs
│   ├── Dockerfile                      # build → static assets served by Nginx/CDN
│   ├── public/
│   └── src/
│       ├── api/                        # typed API client, auth interceptor
│       ├── components/                 # shared/presentational components
│       ├── features/                   # feature-sliced (trials, patients, ...)
│       │   ├── trials/
│       │   ├── patients/
│       │   └── enrollment/
│       ├── hooks/                      # reusable hooks (useAuth, usePolling)
│       ├── store/                      # client state (Zustand/Redux Toolkit)
│       ├── types/                      # shared TS domain types
│       └── utils/
│
├── infrastructure/
│   ├── terraform/                      # VPC, RDS, ElastiCache, ECS, KMS, S3, WAF
│   ├── ecs/                            # task defs / service defs
│   └── docker/                         # shared base images, compose overrides
│
├── docs/
│   ├── PROJECT-PLAN.md                 # ← this file
│   ├── ARCHITECTURE.md
│   ├── TECH-NOTES.md
│   └── validation/                     # (future) IQ/OQ/PQ, traceability matrix
│
├── scripts/                            # bootstrap, seed, db backup, ci helpers
├── docker-compose.yml                  # local dev: web + worker + beat + pg + redis
├── .env.example
├── .gitignore
└── README.md
```

### Why this layout

- **`apps/` = bounded contexts.** Each app owns its models, serializers, views,
  tasks and tests. Cross-app calls go through service functions, never reaching
  into another app's ORM internals — this keeps a future extraction to a service
  cheap if a context (e.g. `eligibility`) needs to scale independently.
- **`config/` is the project, `apps/` are the apps** — the standard "two-scoops"
  Django split that keeps settings out of business code.
- **`ml/` is deliberately *outside* `apps/`.** Model/training code has a different
  lifecycle (data versioning, retraining, drift monitoring) than request/response
  code. `apps/eligibility/` is the thin Django-facing orchestration layer; `ml/`
  is the portable inference logic that can later move to SageMaker.
- **`infrastructure/` is IaC-only** so prod parity is reviewable in PRs.

---

## 3. Implementation TODO List

Legend: `P1` foundation · `P2` core features · `P3` polish/optimization.
Compliance-critical items are marked 🔒.

### Phase 1 — Foundation (High priority)

- [x] **P1** Repo scaffolding, `pyproject.toml`, pre-commit hooks (ruff/black/mypy).
- [x] **P1** Django project + settings split (base/dev/staging/prod/test).
- [x] **P1** 🔒 `accounts`: custom `User`, role model, RBAC, **MFA**, password policy.
- [x] **P1** 🔒 `audit`: append-only `AuditEvent` model + middleware capturing
      actor, before/after diff, timestamp, reason-for-change. **Immutable.**
- [x] **P1** PostgreSQL via RDS; connection pooling (PgBouncer); migrations CI gate.
- [x] **P1** Celery + Redis broker; `celery beat` schedule wired; healthchecks.
- [x] **P1** Docker Compose dev stack (web, worker, beat, postgres, redis).
- [x] **P1** CI pipeline: lint → type-check → unit tests → SAST → container build.
- [x] **P1** 🔒 Encryption: TLS everywhere; field-level encryption for PHI (KMS).
- [x] **P1** Frontend bootstrap (Vite + React + TS), typed API client, auth flow.

### Phase 2 — Core features (Medium priority)

- [x] **P2** `trials`: Protocol, Study, Arm, VisitSchedule, EligibilityCriteria.
- [x] **P2** `sites`: investigative sites, PI assignment, delegation-of-authority log.
- [x] **P2** 🔒 `patients`: Subject model with encrypted PHI + de-identification helper.
- [x] **P2** 🔒 `enrollment`: screening → **e-consent** → randomization workflow
      (state machine), with electronic signature capture (21 CFR Part 11 §11.50).
- [x] **P2** `ecrf`: form definitions, data points, edit checks, **query management**.
- [x] **P2** 🔒 `eligibility`: orchestrate ML screening (Celery), persist results
      with explainability + criterion-level match/no-match; human confirmation gate.
- [x] **P2** `ml/pipelines`: NLP pipeline — de-id → clinical NER → ontology mapping
      (SNOMED/ICD-10/RxNorm/LOINC) → criteria matching → scored recommendation.
- [x] **P2** `notifications`: visit-window reminders, overdue visits, SAE deadlines.
- [x] **P2** Celery beat jobs: protocol-deviation detection, re-screening, enrollment
      target monitoring, audit-integrity checksum.
- [x] **P2** Frontend: trial dashboard, subject enrollment wizard, eCRF data entry,
      eligibility review screen (shows ML rationale, requires human sign-off).
- [x] **P2** REST API: versioned (`/api/v1/`), OpenAPI schema, RBAC-enforced.

### Phase 3 — Polish & optimization (Lower priority)

- [ ] **P3** 🔒 Validation package: requirements traceability matrix, IQ/OQ/PQ scripts.
- [ ] **P3** Observability: structured JSON logs, OpenTelemetry traces, Grafana/CW dashboards.
- [ ] **P3** Read replicas + query optimization; report/export pipeline (PDF/SAS XPT).
- [ ] **P3** ML: model monitoring (drift, fairness across demographics), A/B harness.
- [ ] **P3** Performance: API caching, DB index review, frontend code-splitting.
- [ ] **P3** Accessibility (WCAG 2.1 AA) and i18n for multi-region sites.
- [ ] **P3** Disaster recovery: cross-AZ failover drill, PITR backups, RTO/RPO docs.
- [ ] **P3** Load & soak testing (Locust/k6); chaos testing of worker tier.

---

## 4. Milestones & Definition of Done

| Milestone | Exit criteria |
|-----------|---------------|
| **M1 — Compliant skeleton** | Auth + RBAC + audit trail live; all writes audited; CI green. |
| **M2 — Protocol & enrollment** | A trial can be defined and a subject enrolled end-to-end with e-consent + e-signature. |
| **M3 — ML screening** | EHR note → eligibility recommendation with rationale; human confirm/override persisted & audited. |
| **M4 — Production readiness** | GovCloud deploy via OIDC, DR drill passed, validation pack started. |

**Global Definition of Done:** code reviewed · tests ≥ target coverage · no
high/critical SAST or dependency findings · audit events emitted for every
clinical mutation · docs/CHANGELOG updated.
