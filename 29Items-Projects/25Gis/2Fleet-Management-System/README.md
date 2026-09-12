# Fleet Management System

Real-time fleet tracking with FastAPI, PostgreSQL/PostGIS, Redis, Kafka, React, TypeScript, and Leaflet.

## Features

- Vehicle and driver CRUD.
- Telemetry ingestion with latest vehicle state updates.
- Historical vehicle location playback.
- GeoJSON geofence management and point-in-polygon event detection.
- ETA and route prediction using latest location plus historical speed.
- Redis-backed active vehicle cache with graceful fallback.
- Kafka telemetry consumer for event-driven ingestion.
- Docker Compose stack for PostGIS, Redis, Kafka, backend, and frontend.
- GitHub Actions pipeline for linting, tests, image builds, and ECS deployment hook.

## Local Setup

1. Install Docker Desktop.
2. Copy `.env.example` to `.env` and adjust values when needed.
3. Start the stack:

```powershell
docker-compose up --build
```

4. Open the frontend at `http://localhost:5173`.
5. Open FastAPI docs at `http://localhost:8000/docs`.

PostGIS initializes from `migrations/001_initial_schema.sql` on first database startup.

Docker is optional for backend tests. Without Docker, the FastAPI app defaults to a local SQLite file unless `DATABASE_URL` is set.

## Backend Commands

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
pytest
$coverageFile = Join-Path $env:TEMP "fleet-coverage.sqlite"
coverage run --data-file=$coverageFile -m pytest
coverage report --data-file=$coverageFile --fail-under=70
ruff check app tests
uvicorn app.main:app --reload
```

The test suite uses SQLite for fast service/API coverage. Production and Docker use PostgreSQL/PostGIS.

## Frontend Commands

```powershell
cd frontend
npm install
npm run dev
npm run typecheck
npm run lint
npm test
```

## API Overview

- `GET /health`
- `GET|POST /api/v1/drivers`
- `GET|PATCH|DELETE /api/v1/drivers/{driver_id}`
- `GET|POST /api/v1/vehicles`
- `GET|PATCH|DELETE /api/v1/vehicles/{vehicle_id}`
- `GET /api/v1/vehicles/{vehicle_id}/history`
- `GET|POST /api/v1/geofences`
- `GET|PATCH|DELETE /api/v1/geofences/{geofence_id}`
- `GET /api/v1/geofences/events`
- `POST /api/v1/telemetry`
- `GET|POST /api/v1/trips`
- `GET|PATCH|DELETE /api/v1/trips/{trip_id}`
- `GET /api/v1/trips/{trip_id}/playback`
- `POST /api/v1/routes/predict`
- `GET /api/v1/routes/predictions`

List endpoints accept `limit` and `offset` query parameters. `limit` is bounded by endpoint-specific maximums to avoid unbounded result sets.

Example requests:

```powershell
curl http://localhost:8000/health

curl -X POST http://localhost:8000/api/v1/vehicles `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Courier 1\",\"license_plate\":\"AA-0001-AA\",\"status\":\"idle\"}"

curl "http://localhost:8000/api/v1/vehicles?limit=25&offset=0"
```

## Authentication

Local development defaults to `AUTH_REQUIRED=false`. In secured environments, set `AUTH_REQUIRED=true` and provide `JWT_SECRET`, `JWT_ISSUER`, and `JWT_AUDIENCE`. `JWT_SECRET` must be at least 32 characters when authentication is enabled. API routes then require a valid HS256 bearer token.

## Production Notes

- Terminate TLS at the load balancer or edge proxy; production responses include HSTS when `ENVIRONMENT=production`.
- Keep `.env` out of source control. Use AWS Secrets Manager or SSM Parameter Store for production secrets.
- Keep `AUTO_CREATE_TABLES=false` in production once migrations are managed by deployment automation.
- All database access goes through SQLAlchemy parameterized statements.
- API responses include request IDs and security headers. Large responses are gzip-compressed.

## Troubleshooting

- `docker-compose up` cannot connect to Docker: start Docker Desktop first. The app can still be tested locally with `pytest` and `npm` commands.
- PostGIS tables are missing: remove the `postgis-data` volume and restart Compose, or run `migrations/001_initial_schema.sql` manually.
- Frontend cannot reach the API: verify `VITE_API_BASE_URL` and CORS origins in `.env`.
- Auth returns `401`: confirm `AUTH_REQUIRED`, `JWT_SECRET`, `JWT_ISSUER`, and `JWT_AUDIENCE` match the token.
- Coverage file permission errors on Windows: use the README command that writes coverage data to `$env:TEMP`.

## Environment Variables

All expected variables are documented in `.env.example`, including API, database, Redis, Kafka, JWT, frontend, AWS, compression, and security-header settings.

## Deployment

The repository includes ECS task definition and Terraform starter resources for the ECS cluster, log group, and ECR repositories. GitHub Actions can trigger ECS deployment when `AWS_ROLE_TO_ASSUME`, `ECS_CLUSTER`, and `ECS_SERVICE_API` are configured in repository settings.
