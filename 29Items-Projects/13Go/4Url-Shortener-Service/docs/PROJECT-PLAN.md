# URL Shortener Service Project Plan

## 1.1 Project File Structure

This project uses a layered Go backend with a React frontend. PostgreSQL is the durable source of truth, Redis is used for hot URL cache entries, rate limiting, and short-lived analytics counters, and golang-migrate owns schema evolution.

```text
.
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- cmd/
|   `-- api/
|       `-- main.go
|-- deploy/
|   `-- fly/
|       `-- README.md
|-- docs/
|   |-- ARCHITECTURE.md
|   |-- API.md
|   |-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
|-- frontend/
|   |-- index.html
|   |-- package-lock.json
|   |-- package.json
|   |-- playwright.config.ts
|   |-- tsconfig.json
|   |-- vite.config.ts
|   |-- vitest.config.ts
|   |-- tests/
|   |   `-- e2e/
|   |       `-- url-shortener.spec.ts
|   `-- src/
|       |-- App.tsx
|       |-- main.tsx
|       |-- vite-env.d.ts
|       |-- api/
|       |   `-- client.ts
|       |-- components/
|       |   |-- UrlShortener.test.tsx
|       |   `-- UrlShortener.tsx
|       `-- styles/
|           `-- app.css
|-- internal/
|   |-- analytics/
|   |   `-- events.go
|   |-- config/
|   |   `-- config.go
|   |-- http/
|   |   |-- handlers/
|   |   |   `-- url_handler.go
|   |   |-- middleware/
|   |   |   |-- auth.go
|   |   |   |-- logging.go
|   |   |   |-- ratelimit.go
|   |   |   `-- request_id.go
|   |   `-- routes/
|   |       |-- router.go
|   |       `-- router_test.go
|   |-- models/
|   |   `-- url.go
|   |-- qrcode/
|   |   `-- generator.go
|   |-- service/
|   |   |-- url_service.go
|   |   `-- url_service_test.go
|   `-- store/
|       |-- postgres/
|       |   `-- url_repository.go
|       `-- redis/
|           |-- cache.go
|           `-- ratelimit.go
|-- migrations/
|   |-- 000001_create_urls.down.sql
|   |-- 000001_create_urls.up.sql
|   |-- 000002_create_url_clicks.down.sql
|   |-- 000002_create_url_clicks.up.sql
|   |-- 000003_create_abuse_reports.down.sql
|   `-- 000003_create_abuse_reports.up.sql
|-- scripts/
|   |-- migrate.ps1
|   `-- migrate.sh
|-- tests/
|   `-- integration/
|       |-- README.md
|       `-- url_flow_test.go
|-- .editorconfig
|-- .env.example
|-- .gitignore
|-- .golangci.yml
|-- Dockerfile
|-- docker-compose.yml
|-- fly.toml
|-- go.mod
|-- Makefile
`-- README.md
```

### Source Code Organization

- `cmd/api`: application entrypoint, dependency wiring, lifecycle management.
- `internal/config`: environment driven configuration with validation.
- `internal/http`: Gin routes, handlers, middleware, and request level behavior.
- `internal/service`: business logic and orchestration across storage, cache, analytics, and QR generation.
- `internal/store/postgres`: PostgreSQL repositories and query ownership.
- `internal/store/redis`: Redis cache, counters, and future rate limit primitives.
- `internal/analytics`: event and aggregate contracts for click tracking.
- `internal/qrcode`: QR code generation boundary.
- `migrations`: versioned SQL migrations managed by golang-migrate.
- `frontend`: Vite React app that consumes the REST API.

### CI/CD and Tooling

- GitHub Actions: lint, test, build, and Docker image validation.
- Docker Compose: local PostgreSQL, Redis, API, and frontend development.
- Fly.io: production deployment target for the API container.
- golangci-lint: Go linting.
- npm scripts: frontend lint, test, typecheck, and build.
- `.env.example`: shared configuration contract for local and deployed environments.

## 1.2 Implementation Checklist

### Phase 1: Foundation (high priority)

- [x] Finalize API contract for URL creation, redirect, analytics, QR code, and admin operations.
- [x] Implement PostgreSQL repository methods with transactions where needed.
- [x] Implement Redis cache read-through and write-through behavior for hot short codes.
- [x] Add request validation, structured error responses, and consistent HTTP status mapping.
- [x] Add database migrations for URLs, clicks, users/API keys, and abuse prevention metadata.
- [x] Add Docker Compose local workflow and verify startup from a clean checkout.
- [x] Add CI pipeline with Go test, frontend typecheck, migration validation, and Docker build.

### Phase 2: Core features (medium priority)

- [x] Implement short code generation with collision handling and reserved word protection.
- [x] Implement redirect path with cache lookup, DB fallback, and analytics recording.
- [x] Add URL analytics endpoints with time buckets, referrers, user agents, and country fields.
- [x] Add QR code generation endpoint and frontend preview/download behavior.
- [x] Add API key based authentication for write/admin endpoints.
- [x] Add Redis backed rate limiting per IP and API key.
- [x] Add integration tests using PostgreSQL and Redis through Docker Compose or CI services.
- [x] Add frontend create, copy, QR preview, and analytics views.

### Phase 3: Polish and optimization (lower priority)

- [x] Store analytics in PostgreSQL and Redis series counters.
- [x] Add rollup table schema for high volume analytics queries.
- [x] Add structured request logging and metrics-friendly request metadata.
- [x] Add PostgreSQL indexes, Redis TTLs, and configurable rate limits.
- [x] Add Fly.io deployment configuration and CI deploy job.
- [x] Add security hardening foundations for rate limiting, API keys, abuse reports, and blocked domains.
- [x] Add automated frontend tests for the main create flow.
