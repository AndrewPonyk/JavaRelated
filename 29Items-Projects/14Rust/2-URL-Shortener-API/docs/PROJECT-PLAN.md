# URL Shortener API — Delivery Plan

## Implemented structure

The repository contains an Actix-Web API (`src/api`), business services (`src/services`), domain models (`src/domain`), Diesel/SQLite infrastructure (`src/infrastructure`), embedded SQL migrations, isolated API integration tests, a Vite/React frontend, Docker Compose, GitHub Actions, and deployment examples.

## Completed delivery checklist

### Foundation

- [x] Locked Rust dependencies and embedded, automatic Diesel migrations.
- [x] Health endpoint, structured request logging, validated environment configuration, CORS, and bounded JSON bodies.
- [x] CI checks formatting, Clippy, tests, and container image construction.
- [x] README quick-start and OpenAPI contract.

### Core features

- [x] Persistent SQLite repository with unique short codes and collision retries.
- [x] Create, list, read, update, and delete management endpoints.
- [x] Public 302 redirects with atomic visit-count updates.
- [x] Authenticated custom aliases, URL status, expiration, request validation, and administrative access control.
- [x] Temporary-database integration tests for primary lifecycle and failure flows.

### Operational readiness

- [x] Memory-bounded, proxy-aware rate limiting with trusted-header controls and actionable retry responses.
- [x] JSON errors for validation, authentication, missing routes, unsupported methods, conflicts, inactive links, limits, and internal failures.
- [x] Complete environment reference, OpenAPI schemas/examples, production checklist, and troubleshooting guide.
- [x] Dependency advisory scans and an 80% line-coverage gate in CI.

- [x] Multi-stage API image and a frontend image orchestrated by `docker compose up --build`.
- [x] Railway/Fly configuration examples and secret-driven deployment configuration.
- [x] Clear SQLite single-writer boundaries and a documented PostgreSQL migration path for future scale.

PostgreSQL replication, distributed caching, asynchronous analytics, and dashboards are intentionally excluded from this single-process SQLite release; the layered repository boundary keeps those future changes isolated from the HTTP contract.
