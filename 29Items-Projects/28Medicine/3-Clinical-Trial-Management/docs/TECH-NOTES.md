# Technical Notes — Clinical Trial Management System

Actionable engineering guidance for building, testing, deploying and operating
the CTMS on **AWS GovCloud** with **GitHub Actions**. Read alongside
[`ARCHITECTURE.md`](./ARCHITECTURE.md) and [`PROJECT-PLAN.md`](./PROJECT-PLAN.md).

---

## 3.1 CI/CD Pipeline Design

**Goal:** every merge to `main` is provably lint-clean, type-safe, tested,
dependency-scanned and reproducibly built; promotion to environments is gated and
auditable (an implicit part of CSV evidence).

### Stages

```mermaid
flowchart LR
    PR[Pull Request] --> L[Lint<br/>ruff + black + eslint]
    L --> T[Type-check<br/>mypy + tsc]
    T --> U[Unit tests<br/>pytest + vitest]
    U --> I[Integration tests<br/>pg + redis services]
    I --> S[Security<br/>pip-audit, npm-audit,<br/>Trivy, CodeQL]
    S --> B[Build images<br/>multi-stage, SBOM]
    B --> Dev[Auto-deploy → Dev]
    Dev --> Stg[Deploy → Staging<br/>manual approval]
    Stg --> Migr[DB migrate<br/>backward-compatible]
    Migr --> Prod[Deploy → Prod<br/>manual approval + change ticket]
```

| Stage | Tooling | Gate |
|-------|---------|------|
| **Lint** | `ruff`, `black --check`, `eslint`, `prettier --check` | hard fail |
| **Type-check** | `mypy` (backend), `tsc --noEmit` (frontend) | hard fail |
| **Unit** | `pytest`, `vitest` | hard fail + coverage threshold |
| **Integration** | `pytest` against ephemeral Postgres + Redis services | hard fail |
| **Security (SAST/deps)** | `pip-audit`, `npm audit`, `bandit`, **Trivy** (image+fs), **CodeQL** | fail on High/Critical |
| **Build** | Docker multi-stage; generate **SBOM** (Syft) + sign (cosign) | artifact published to ECR |
| **Deploy** | OIDC → AWS; ECS rolling/blue-green | environment-scoped approvals |

### Key practices
- **No long-lived AWS keys in CI.** GitHub Actions assumes an IAM role via
  **OIDC**; the trust policy restricts to this repo + protected branches.
- **Environments** (`dev`/`staging`/`prod`) use GitHub *Environments* with
  required reviewers and wait timers; prod requires a linked change record.
- **Migrations are decoupled & backward-compatible** (expand/contract) so a
  rolling deploy never breaks the running version. Run migrations as a discrete,
  observable step — never implicitly on container start in prod.
- **Immutable images**, tagged by Git SHA; staging and prod deploy the *same*
  artifact (build once, promote).

---

## 3.2 Testing Strategy

A clinical system needs a **deep** test pyramid; bugs here can corrupt regulated
data. Tests are also part of the validation evidence.

### Layers & targets

| Layer | Tools | Coverage target | Focus |
|-------|-------|-----------------|-------|
| **Unit** | `pytest`, `pytest-django`, `factory_boy`, `vitest` + RTL | **≥ 85%** core domain; **100% for `audit` & e-signature** | business rules, serializers, edge cases |
| **Integration** | `pytest` + real Postgres/Redis (Docker services), DRF `APIClient` | ≥ 70% | API contracts, permissions, transactions, Celery task wiring |
| **End-to-end** | **Playwright** | critical paths | enroll subject, e-consent, eligibility review/sign-off, query workflow |
| **Contract** | `drf-spectacular` schema + schemathesis | n/a | API ↔ SPA type alignment, fuzzing |
| **ML evaluation** | `pytest` + golden datasets | tracked metrics | precision/recall on labeled notes, **fairness across demographics**, drift |
| **Non-functional** | **Locust/k6** (load), OWASP ZAP (DAST) | SLO-based | performance budgets, security |

### Practices
- **Deterministic tests:** freeze time (`freezegun`), seed RNG, no network — ML
  models stubbed behind the `EligibilityScreener` interface for app tests; the
  *real* model is exercised in the dedicated ML-evaluation suite.
- **Permission matrix tests:** parametrized over every role × resource to prove a
  Monitor can't write and an Auditor can't mutate.
- **Audit assertions** baked into integration tests: every clinical mutation test
  also asserts an `AuditEvent` row was written with correct before/after.
- **De-identification tests:** assert no PHI reaches the ML pipeline or logs.
- Coverage is a **floor, not a goal** — review prioritizes meaningful assertions
  on domain invariants over line count.

---

## 3.3 Deployment Strategy

**Target:** AWS **GovCloud (US)** — note the `aws-us-gov` partition, separate
credentials/console, and a **reduced service catalog** vs. commercial AWS
(validate every service's GovCloud availability before depending on it).

### Containerization
- **Multi-stage Docker** images: a builder stage compiles wheels/assets; the
  runtime stage is **slim, non-root, read-only filesystem**, no build tools.
- One backend image runs three roles via command override: `web` (gunicorn/
  uvicorn), `worker` (celery), `beat` (celery beat). Frontend builds to static
  assets served by CloudFront (S3 origin) or an Nginx sidecar.

### Runtime topology (GovCloud)

```mermaid
graph TB
    CF[CloudFront + WAF] --> S3W[(S3 — SPA static)]
    CF --> ALB[Application Load Balancer]
    subgraph ECS["ECS Fargate (private subnets)"]
        WebSvc[web service x N]
        WorkSvc[worker service x M]
        BeatSvc[beat service x1]
    end
    ALB --> WebSvc
    WebSvc --> RDS[(RDS PostgreSQL<br/>Multi-AZ + replicas)]
    WebSvc --> REDIS[(ElastiCache Redis)]
    WorkSvc --> RDS
    WorkSvc --> REDIS
    WorkSvc --> SM[SageMaker / ML endpoint]
    WebSvc --> SEC[Secrets Manager / KMS]
    BeatSvc --> REDIS
```

- **ECS Fargate** for web + worker + beat (no node management; FedRAMP-aligned).
  EKS is an option if richer orchestration is needed later.
- **Blue/green** (CodeDeploy) or rolling deploys with health checks + automatic
  rollback. `beat` runs as a **single** task (a lock prevents duplicate schedules).
- **Infrastructure as Code** in `infrastructure/terraform/` — VPC (private
  subnets, no public DB), RDS, ElastiCache, ECS, ALB, WAF, KMS, S3, IAM.
- **DR:** Multi-AZ RDS with automated backups + PITR; documented RTO/RPO;
  periodic restore drills (also validation evidence).

---

## 3.4 Environment Management

- **12-factor config:** all environment differences come from env vars / Secrets
  Manager; the *image* is identical across environments.
- **Settings split:** `config/settings/{base,development,staging,production,test}.py`
  selected by `DJANGO_SETTINGS_MODULE`. `base.py` reads env via `django-environ`
  and **fails fast** if a required secret is missing in non-dev environments.
- **Never commit secrets.** `.env` is git-ignored; `.env.example` (below) is the
  contract. Prod/staging resolve secrets from **AWS Secrets Manager / SSM** at
  task start, not from `.env`.

### `.env.example` (template)

```dotenv
# ── Django ───────────────────────────────────────────────
DJANGO_SETTINGS_MODULE=config.settings.development
DJANGO_SECRET_KEY=change-me-generate-50-random-chars
DJANGO_DEBUG=true
DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1

# ── Database (PostgreSQL) ────────────────────────────────
DATABASE_URL=postgres://ctms:ctms@localhost:5432/ctms
DB_CONN_MAX_AGE=60
DB_SSL_REQUIRE=false            # true in staging/prod

# ── Celery / Redis ───────────────────────────────────────
CELERY_BROKER_URL=redis://localhost:6379/0
CELERY_RESULT_BACKEND=redis://localhost:6379/1

# ── Security / Crypto ────────────────────────────────────
FIELD_ENCRYPTION_KEY=base64-32-byte-key-for-phi-fields
JWT_ACCESS_TTL_MINUTES=15
JWT_REFRESH_TTL_DAYS=7

# ── AWS (GovCloud) ───────────────────────────────────────
AWS_REGION=us-gov-west-1
AWS_S3_BUCKET=ctms-documents
KMS_KEY_ID=arn:aws-us-gov:kms:us-gov-west-1:ACCT:key/xxxx
# Credentials come from the task role in AWS — NOT from here.

# ── ML / NLP ─────────────────────────────────────────────
ML_BACKEND=local                # local | sagemaker
SAGEMAKER_ENDPOINT_NAME=ctms-eligibility-screener
ELIGIBILITY_MIN_CONFIDENCE=0.70

# ── OIDC / IdP ───────────────────────────────────────────
OIDC_ISSUER_URL=https://idp.example.gov/realms/ctms
OIDC_CLIENT_ID=ctms-web
OIDC_CLIENT_SECRET=change-me

# ── Observability ────────────────────────────────────────
LOG_LEVEL=INFO
LOG_FORMAT=json
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
SENTRY_DSN=
```

---

## 3.5 Version Control Workflow

**Recommended: Trunk-Based Development with short-lived feature branches +
protected `main`.**

```mermaid
gitGraph
    commit id: "main"
    branch feat/eligibility-screening
    commit id: "wip"
    commit id: "tests"
    checkout main
    merge feat/eligibility-screening tag: "squash + CI green"
    commit id: "release v1.4.0" tag: "v1.4.0"
```

**Rationale**
- Short-lived branches + **frequent integration** avoid long divergent branches —
  critical when migrations and a shared schema are involved.
- **Protected `main`:** PR required, ≥1 review (≥2 for `audit`/security/migration
  changes), green CI, no unresolved security findings, signed commits.
- **Squash-merge** keeps a clean, auditable history; **Conventional Commits** feed
  automated CHANGELOG + **SemVer** release tags.
- **Release tags are immutable** and map 1:1 to a deployed image SHA — essential
  for tracing "what version was running when" during an audit/inspection.
- Gitflow's long-lived `develop`/`release` branches are **not** recommended here —
  unnecessary overhead for a single primarily-continuous deployment line.

> Tag the deployed commit and record the tag in the change ticket; this linkage is
> part of the regulatory paper trail.

---

## 3.6 Common Pitfalls (this stack & domain)

**Django / DRF**
- **N+1 queries** on nested serializers — use `select_related`/`prefetch_related`
  and assert query counts in tests (`assertNumQueries`).
- **Migrations that lock tables** under load (e.g. adding a non-null column with a
  default on a huge `ecrf_datapoint`). Use expand/contract + `CONCURRENTLY` index
  creation; never auto-migrate on prod container start.
- **Leaky permissions:** DRF object-level perms are easy to forget on detail
  routes — enforce via a base viewset + the role×resource test matrix.
- **PHI in `__str__`/`repr`/logs** — a subtle HIPAA leak. Scrub centrally.

**Celery**
- **Non-idempotent tasks + retries = double enrollment / duplicate notifications.**
  Make every task idempotent (idempotency keys, `get_or_create`, status guards).
- **Passing ORM objects** through the broker — pass **IDs**, re-fetch in the task.
- **`beat` running on >1 instance** double-fires schedules — run a single beat or
  use a distributed lock.
- **Long tasks blocking a queue** — isolate ML/reports on dedicated queues with
  their own workers and time limits.

**PostgreSQL**
- **Connection exhaustion** from many web/worker processes — front RDS with
  **PgBouncer**; tune `CONN_MAX_AGE`.
- **Audit/eCRF table bloat** — partition by time, archive cold partitions to S3.

**ML / NLP (clinical)**
- **De-identification gaps** — a single un-scrubbed identifier reaching the model
  or logs is a reportable breach. Test it explicitly.
- **Over-trusting the model** — keep it advisory; the human e-signed decision is
  authoritative. Persist **explainability** (which criteria matched) for review &
  audit. Monitor for **drift** and **demographic bias**.
- **Ontology version skew** — SNOMED/ICD/RxNorm/LOINC are versioned; pin and
  record the version used for each screening (reproducibility).

**AWS GovCloud**
- **Service gaps:** not all commercial AWS services/regions exist in GovCloud, and
  ARNs use the **`aws-us-gov`** partition — hard-coded commercial ARNs will fail.
- **Separate accounts/credentials** and stricter IAM boundaries; test the OIDC
  trust policy early.
- **AMI/marketplace/quotas differ** — validate capacity and image availability
  before committing to a topology.

**TypeScript / React**
- **API/type drift** — generate TS types from the OpenAPI schema instead of
  hand-writing them; fail CI on mismatch.
- **Unhandled loading/error states** on slow async (ML screening) flows — model
  `idle | loading | success | error` explicitly (see `useTrials.ts`).
