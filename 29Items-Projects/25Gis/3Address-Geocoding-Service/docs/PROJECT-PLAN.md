# Address Geocoding Service Project Plan

## 1. Project File Structure

This repository is organized as a deployable full-stack service with a FastAPI backend, React frontend, PostGIS schema migrations, Docker packaging, and GitHub Actions CI.

```text
.
|-- .env.example
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- .gitignore
|-- docker-compose.yml
|-- README.md
|-- backend/
|   |-- .dockerignore
|   |-- Dockerfile
|   |-- pyproject.toml
|   |-- requirements.txt
|   |-- app/
|   |   |-- __init__.py
|   |   |-- main.py
|   |   |-- api/
|   |   |   |-- __init__.py
|   |   |   `-- routes/
|   |   |       |-- __init__.py
|   |   |       |-- addresses.py
|   |   |       |-- geocode.py
|   |   |       `-- health.py
|   |   |-- core/
|   |   |   |-- __init__.py
|   |   |   |-- config.py
|   |   |   `-- logging.py
|   |   |-- db/
|   |   |   |-- __init__.py
|   |   |   `-- session.py
|   |   |-- models/
|   |   |   |-- __init__.py
|   |   |   `-- address.py
|   |   |-- schemas/
|   |   |   |-- __init__.py
|   |   |   `-- geocode.py
|   |   `-- services/
|   |       |-- __init__.py
|   |       |-- address_book.py
|   |       |-- geocoding.py
|   |       |-- reverse_geocoding.py
|   |       `-- search.py
|   `-- tests/
|       |-- __init__.py
|       `-- test_health.py
|-- deploy/
|   |-- nginx/
|   |   `-- address-geocoding.conf
|   `-- systemd/
|       `-- address-geocoding.service
|-- docs/
|   |-- ARCHITECTURE.md
|   |-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
|-- frontend/
|   |-- .dockerignore
|   |-- Dockerfile
|   |-- index.html
|   |-- eslint.config.js
|   |-- nginx.conf
|   |-- package-lock.json
|   |-- package.json
|   |-- tsconfig.json
|   |-- vite.config.ts
|   `-- src/
|       |-- App.tsx
|       |-- main.tsx
|       |-- vite-env.d.ts
|       |-- components/
|       |   `-- AddressLookup.tsx
|       |-- lib/
|       |   `-- api.ts
|       `-- styles/
|           `-- global.css
|-- migrations/
|   `-- 001_init_postgis.sql
`-- scripts/
    `-- wait-for-services.ps1
```

### Source Code

- `backend/app/main.py` is the FastAPI application entry point.
- `backend/app/api/routes/` contains HTTP route modules.
- `backend/app/services/` contains application logic for Nominatim, Elasticsearch, and PostGIS operations.
- `backend/app/schemas/` contains Pydantic request and response models.
- `backend/app/models/` contains SQLAlchemy ORM models.
- `frontend/src/components/` contains reusable React UI components.
- `frontend/src/lib/` contains API client utilities.
- `migrations/` contains SQL migrations for PostGIS-enabled persistence.

### CI/CD

- `.github/workflows/ci.yml` runs backend lint/tests, frontend lint/build, and Docker image build checks.
- `deploy/nginx/address-geocoding.conf` is a reverse proxy stub for EC2.
- `deploy/systemd/address-geocoding.service` is a systemd unit stub for EC2 deployments.

### Tools Configuration

- `.env.example` documents required runtime configuration.
- `docker-compose.yml` runs local PostGIS, Elasticsearch, backend, and frontend containers.
- `backend/pyproject.toml` configures Python linting, formatting, and pytest.
- `frontend/package.json`, `frontend/tsconfig.json`, and `frontend/vite.config.ts` configure the React toolchain.
- `frontend/eslint.config.js` configures frontend linting.
- `backend/.dockerignore` and `frontend/.dockerignore` keep container build contexts small.

## 2. Implementation TODO List

> **MVP status:** The backend is now functionally implemented. **Architecture change:**
> fuzzy search uses PostgreSQL `pg_trgm` similarity instead of Elasticsearch, which
> removes a service/dependency — the trigram GIN index already existed in migration 001.

### Phase 1: Foundation - High Priority

- [ ] Confirm address data retention, privacy, and compliance requirements.
- [x] Finalize API contract for lookup, autocomplete, validation, and reverse geocoding.
- [x] Provision development PostGIS with Docker Compose (Elasticsearch removed).
- [ ] Add migration runner such as Alembic or Flyway and wire it into CI.
- [x] Replace service stubs with real async clients for Nominatim and PostGIS (search via `pg_trgm`).
- [x] Add structured logging and request correlation IDs.
- [x] Implement liveness + readiness health checks (readiness verifies the PostGIS connection).
- [ ] Configure GitHub Actions secrets for staging deployment.

### Phase 2: Core Features - Medium Priority

- [x] Implement forward geocoding through Nominatim with in-process response caching.
- [x] Implement fuzzy autocomplete using PostgreSQL trigram similarity.
- [x] Implement reverse geocoding against PostGIS (`ST_DWithin`/`ST_Distance`) with provider fallback.
- [x] Store normalized address records and lookup metadata in PostGIS.
- [ ] Add validation scoring and explainable match reasons.
- [x] Add backend integration tests with a disposable PostGIS container.
- [ ] Add frontend address lookup, autocomplete, validation state, and result display flows.
- [ ] Add API authentication for production clients.

### Phase 3: Polish & Optimization - Lower Priority

- [ ] Tune Elasticsearch mappings, analyzers, synonyms, and edge n-grams.
- [ ] Add cache invalidation and stale-while-revalidate behavior for common lookups.
- [ ] Add load testing and define SLOs for p95 latency and error rate.
- [ ] Add dashboards and alerts for API latency, Nominatim failures, and search quality.
- [ ] Add blue/green or rolling deployment automation on EC2.
- [ ] Add frontend accessibility and keyboard navigation tests.
- [ ] Document operational runbooks for reindexing, restoring PostGIS, and rotating secrets.
