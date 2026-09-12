# Clinical Trial Management System (CTMS)

A regulated platform for clinical trial **protocol management**, **patient
enrollment & data capture**, and **ML-assisted eligibility screening** (NLP over
EHR/clinical notes). Built compliance-first for **21 CFR Part 11**, **HIPAA**,
**ICH-GCP**, and **FedRAMP** (AWS **GovCloud**).

> **The ML screening is advisory.** The authoritative eligibility decision is
> always a human, electronically-signed action.

## Stack

| Layer | Tech |
|-------|------|
| Backend | Python 3.12 · Django 5 · Django REST Framework · SimpleJWT |
| Async / scheduled | Celery + Redis (isolated queues + Beat) |
| Database | PostgreSQL (SQLite for the test suite) |
| ML / NLP | rule-based clinical matcher (de-id → concept extraction → criteria matching) |
| Frontend | React 18 · TypeScript · Vite · React Query · React Router |
| Infra / CI | Docker Compose · AWS GovCloud (ECS/RDS/KMS) · GitHub Actions |

## What's implemented

- **Auth & RBAC** — JWT login (rotation + blacklist), custom user with 6 clinical
  roles, read-only roles blocked from writes by construction.
- **Audit trail (21 CFR Part 11)** — append-only, hash-chained `AuditEvent` with
  field-level before/after diffs (PHI redacted), nightly integrity verification,
  actor captured via middleware.
- **Trials** — Protocol / Study / Arm / VisitTemplate / EligibilityCriterion with
  guarded state transitions (open/close) and enrollment progress.
- **Subjects** — PHI encrypted at rest (Fernet), de-identification, role-gated PHI
  visibility.
- **Enrollment** — screening → e-consent → balanced randomization → activation →
  visit scheduling; consent/randomization e-signed.
- **eCRF** — form definitions with edit checks, data points, queries, SDV.
- **Eligibility** — async ML screening with per-criterion rationale; authoritative
  e-signed human decision.
- **Notifications** — idempotent visit reminders & deviation alerts.
- **API** — 100+ operations under `/api/v1/`, OpenAPI schema + Swagger UI.

## Documentation

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — structure, file layout, phased TODOs.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, diagrams, data flow, security.
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deployment, pitfalls.

## Quick start — Docker (full stack)

```bash
cp .env.example .env            # optional; compose sets dev defaults
docker compose up --build       # web + worker + beat + postgres + redis + frontend
```

`web` auto-runs migrations and seeds demo data on startup.

- Frontend:   http://localhost:5173/
- API:        http://localhost:8000/api/v1/
- API docs:   http://localhost:8000/api/docs/
- Admin:      http://localhost:8000/admin/  (admin / admin)
- Health:     http://localhost:8000/healthz/

**Demo login:** `crc` / `crc-password-123`

## Quick start — local backend (no Docker)

```bash
cd backend
python -m venv .venv && source .venv/Scripts/activate   # Windows Git Bash
pip install -r requirements/dev.txt

export DJANGO_SETTINGS_MODULE=config.settings.development
export DATABASE_URL="sqlite:///db.sqlite3"   # or a postgres:// URL
export DJANGO_SECRET_KEY=dev-secret FIELD_ENCRYPTION_KEY=dev-key

python manage.py migrate
python manage.py seed_demo
python manage.py runserver
```

## Local frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 (proxies /api → http://localhost:8000)
npm run gen:api    # regenerate TS types from the live OpenAPI schema
```

## Testing

```bash
# Backend: 67 tests, 87% coverage (target 70%+). Hermetic — no DB server needed.
cd backend && pytest

# Lint / format / type gates (as in CI)
ruff check . && black --check . && python manage.py makemigrations --check --dry-run

# Frontend
cd frontend && npm test && npm run lint && npm run type-check
```

## API examples

Full interactive docs (try-it-out) live at **`/api/docs/`** (Swagger UI), schema at
`/api/schema/`. A typical flow with `curl`:

```bash
BASE=http://localhost:8000/api/v1

# 1. Authenticate → JWT access/refresh
curl -s -X POST $BASE/auth/login/ -H 'Content-Type: application/json' \
  -d '{"username":"crc","password":"crc-password-123"}'
# → {"access":"<jwt>","refresh":"<jwt>"}
TOKEN=<access>

# 2. List studies (paginated: {count,next,previous,results})
curl -s $BASE/studies/ -H "Authorization: Bearer $TOKEN"

# 3. Author a protocol
curl -s -X POST $BASE/protocols/ -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"ACME-002","title":"My Trial","version":"1.0","phase":"II"}'

# 4. Submit an EHR note for ML eligibility screening (async)
curl -s -X POST $BASE/screenings/ -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"study":"<study-uuid>","subject":"<subject-uuid>",
       "note_text":"Type 2 diabetes; denies pregnancy"}'

# 5. Record the authoritative, e-signed human decision
curl -s -X POST $BASE/screenings/<id>/decision/ -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"decision":"ELIGIBLE"}'
```

Errors use a consistent envelope:
`{"error":{"code","message","correlation_id","details"}}`.

Health probes: `GET /healthz/` (liveness, no DB) and `GET /readyz/` (readiness, checks DB).

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `OperationalError: could not connect` to Postgres | Ensure `db` is up and `DATABASE_URL` is correct. For DB-less local runs use `DATABASE_URL="sqlite:///db.sqlite3"`. |
| `You must set settings.ALLOWED_HOSTS if DEBUG is False` | Use `config.settings.development`, or set `DJANGO_ALLOWED_HOSTS`. |
| API returns `401` after a while | Access tokens expire in 15 min — get a new one via `POST /api/v1/auth/refresh/`. |
| Worker can't read screening notes (decrypt error) | `FIELD_ENCRYPTION_KEY` **must be identical** across `web`, `worker`, and `beat`. |
| `POST /screenings/` hangs then 500s (Celery redis error) | Every Celery service needs **both** `CELERY_BROKER_URL` and `CELERY_RESULT_BACKEND` pointed at the `redis` host (not `localhost`). Tasks are fire-and-forget (`CELERY_TASK_IGNORE_RESULT`), so a missing result backend otherwise blocks enqueue. |
| `InsecureKeyLengthWarning` from PyJWT | `DJANGO_SECRET_KEY` must be ≥ 32 bytes (production generates 50). |
| CI `makemigrations --check` fails | You changed a model — run `python manage.py makemigrations` and commit. |
| Frontend shows network errors | Backend must be on `:8000`; `npm run dev` proxies `/api` automatically (see `vite.config.ts`). |

## Repository layout

```
backend/    Django modular monolith (apps/ = bounded contexts) + ml/ pipeline
frontend/   React + TypeScript SPA (feature-sliced)
infrastructure/  Terraform + ECS task defs (IaC)
docs/       architecture & planning
.github/    CI, security scan, gated GovCloud deploy
```

See `docs/TECH-NOTES.md §3.2` for the testing strategy (audit & e-signature paths
are covered with dedicated tests).
