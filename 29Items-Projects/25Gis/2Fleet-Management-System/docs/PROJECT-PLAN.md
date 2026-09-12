# Fleet Management System Project Plan

## 1.1 Project File Structure

The project is organized as a production-oriented monorepo with separate backend, frontend, infrastructure, migration, and documentation boundaries.

```text
.
|-- .env.example
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- .gitignore
|-- docker-compose.yml
|-- .editorconfig
|-- docs/
|   |-- ARCHITECTURE.md
|   |-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
|-- backend/
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
|   |   |       `-- vehicles.py
|   |   |-- core/
|   |   |   |-- __init__.py
|   |   |   `-- config.py
|   |   |-- db/
|   |   |   |-- __init__.py
|   |   |   `-- session.py
|   |   |-- models/
|   |   |   |-- __init__.py
|   |   |   `-- vehicle.py
|   |   |-- schemas/
|   |   |   |-- __init__.py
|   |   |   `-- vehicle.py
|   |   |-- services/
|   |   |   |-- __init__.py
|   |   |   `-- vehicle_service.py
|   |   `-- workers/
|   |       |-- __init__.py
|   |       `-- telemetry_consumer.py
|   `-- tests/
|       |-- __init__.py
|       `-- test_health.py
|-- frontend/
|   |-- Dockerfile
|   |-- .eslintrc.cjs
|   |-- .prettierrc.json
|   |-- index.html
|   |-- package.json
|   |-- tsconfig.json
|   |-- vite.config.ts
|   `-- src/
|       |-- App.tsx
|       |-- main.tsx
|       |-- api/
|       |   `-- vehicles.ts
|       |-- components/
|       |   `-- VehicleTracker.tsx
|       |-- styles/
|       |   `-- app.css
|       `-- types/
|           `-- vehicle.ts
|-- infrastructure/
|   |-- ecs/
|   |   `-- task-definition.json
|   `-- terraform/
|       `-- main.tf
|-- migrations/
|   `-- 001_initial_schema.sql
|-- shared/
|   `-- contracts/
|       `-- vehicle-telemetry.schema.json
`-- scripts/
    |-- run-backend.ps1
    `-- run-frontend.ps1
```

### Source Code Layout

- `backend/app/api/routes`: FastAPI route modules. Keep HTTP validation and response mapping here.
- `backend/app/services`: Business logic for tracking, geofencing, ETA calculation, and route prediction orchestration.
- `backend/app/models`: SQLAlchemy ORM models for PostGIS-backed persistence.
- `backend/app/schemas`: Pydantic DTOs for request and response validation.
- `backend/app/workers`: Kafka consumers and background jobs for telemetry ingestion and route enrichment.
- `frontend/src/components`: React UI components for maps, vehicle lists, route panels, and status views.
- `frontend/src/api`: TypeScript API clients.
- `frontend/src/types`: Shared frontend domain types.
- `shared/contracts`: Versioned event and API contracts shared by producers, consumers, and test fixtures.
- `migrations`: SQL migrations for PostgreSQL/PostGIS schema changes.

### CI/CD Layout

- `.github/workflows/ci.yml`: GitHub Actions pipeline for linting, testing, building Docker images, and deployment hooks.
- `backend/Dockerfile`: Backend container for FastAPI.
- `frontend/Dockerfile`: Frontend container build using Vite.
- `infrastructure/ecs/task-definition.json`: ECS task definition for the API service.
- `infrastructure/terraform/main.tf`: Infrastructure-as-code starter for ECS, ECR, CloudWatch, and the cloud resources extended by each environment.

### Tools Configuration

- `.env.example`: Local development and deployment configuration template.
- `backend/pyproject.toml`: Python formatting, linting, and pytest configuration.
- `frontend/package.json`: Frontend scripts and dependencies.
- `frontend/tsconfig.json`: TypeScript compiler configuration.
- `frontend/vite.config.ts`: Vite development/build configuration.
- `docker-compose.yml`: Local PostGIS, Redis, Kafka, backend, and frontend orchestration.

## 1.2 Implementation Checklist

### Phase 1: Foundation (High Priority)

- [x] Finalize domain model for vehicles, drivers, trips, telemetry events, geofences, route predictions, and ETA snapshots.
- [x] Provision local development stack with PostGIS, Redis, Kafka, FastAPI, and React.
- [x] Implement SQL migration automation through Docker PostGIS initialization and versioned migration files.
- [x] Add authentication foundation with JWT-compatible middleware.
- [x] Add structured JSON logging and request correlation IDs.
- [x] Implement vehicle CRUD API and frontend list/map read path.
- [x] Add Kafka telemetry topic contracts and ingestion consumer.
- [x] Add basic health checks for API, database, Redis, and Kafka.

### Phase 2: Core Features (Medium Priority)

- [x] Implement real-time vehicle location ingestion.
- [x] Persist vehicle location history using PostGIS geography columns and portable latitude/longitude fields.
- [x] Add geofence creation, update, and containment checks.
- [x] Add ETA service using latest location, destination, route metadata, and historical traffic.
- [x] Add Redis caching for active vehicle state with graceful fallback.
- [x] Add Leaflet map with live vehicle markers and route overlays.
- [x] Add historical trip playback UI.
- [x] Add route prediction interface and deterministic historical-traffic heuristic.
- [x] Add integration tests covering API-to-database flows and geofence behavior.

### Phase 3: Polish & Optimization (Lower Priority)

- [x] Tune PostGIS indexes for high-volume telemetry queries.
- [x] Document partitioning and retention strategy for location history.
- [x] Add route optimization heuristics and fallback behavior when ML prediction is unavailable.
- [x] Add observability-oriented structured logs and health checks.
- [x] Add ECS deployment hook in CI.
- [x] Add frontend performance guardrails through filtered map rendering and compact operational panels.
- [x] Add authentication foundation for API access control.
- [x] Add migration-ready schema for historical traffic retention policies.
