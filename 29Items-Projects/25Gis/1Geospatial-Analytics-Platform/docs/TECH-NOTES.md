# Geospatial Analytics Platform - Technical Notes

## 1. CI/CD Pipeline Design

GitHub Actions runs:

1. Backend dependency install.
2. Ruff linting.
3. Pytest with coverage against a PostGIS service container.
4. Backend image build.
5. Frontend dependency install.
6. ESLint.
7. TypeScript and Vite build.
8. Vitest frontend tests.
9. Frontend image build.
10. Docker Compose deployment bundle validation on `main`.

The deployment job is intentionally credential-free in source control. Repository secrets should provide EC2 host, SSH key, registry credentials, and environment-specific secret references.

## 2. Testing Strategy

Backend tests use Pytest and cover:

- Health checks and request metadata.
- Dataset CRUD and feature creation.
- GeoJSON feature import.
- Bounding-box, proximity, and summary spatial queries.
- Layer CRUD.
- Classification job creation and execution.
- User and role administration.
- Validation error response shape.
- Domain unit tests for GeoJSON validation and deterministic classification.

Frontend tests use Vitest for API client behavior. The UI is typed with TypeScript and validates user inputs before sending API requests.

## 3. Deployment Strategy

Local and EC2 deployment use Docker Compose:

- `postgis` stores authoritative spatial data and runs the SQL migration on first initialization.
- `geoserver` exposes WMS/WFS services against a PostGIS datastore configured by the `geoserver-init` Compose service.
- `backend` runs FastAPI and PostGIS-backed business logic.
- `frontend` serves the Vite build through Nginx.

For production, move secrets to AWS Secrets Manager or Systems Manager Parameter Store, place TLS at an ALB or reverse proxy, and prefer RDS PostgreSQL with PostGIS for managed backups.

## 4. Environment Management

`.env.example` documents all required variables. `config/env/development.env`, `config/env/staging.env`, and `config/env/production.env` provide safe, non-secret environment profiles.

Required groups:

- API: app name, environment, logging, CORS.
- Database: PostGIS URL and database credentials.
- GeoServer: public URL and admin credentials.
- Frontend: API URL, GeoServer URL, Mapbox token.
- Security: JWT issuer, audience, secret reference, auth toggle, and HTTPS enforcement.
- ML: model path and worker concurrency.

## 5. Version Control Workflow

Use trunk-based development with short-lived branches. `main` remains protected by CI, and release tags should be cut after staging validation.

## 6. Common Pitfalls Covered

- Geometry is stored in PostGIS, not treated as plain JSON.
- SRID 4326 is enforced at write time.
- Spatial indexes are defined in the migration.
- Large interactive point rendering is isolated to Deck.gl.
- WMS/WFS serving remains GeoServer-owned.
- ML outputs are job records plus artifact URIs and generated classification layers.
- Write endpoints are guarded by role checks when authentication is enabled.
