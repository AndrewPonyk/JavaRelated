# Address Geocoding Service

Full-stack address lookup and validation service using FastAPI, React, PostGIS (with `pg_trgm` fuzzy search), and Nominatim.

The service is functionally implemented end to end: addresses persist to PostGIS, forward geocoding calls Nominatim (with in-process response caching) and stores the best match, reverse geocoding runs a PostGIS `ST_DWithin`/`ST_Distance` nearest-neighbour search with an optional Nominatim fallback, and autocomplete uses PostgreSQL trigram similarity. Every lookup is audited in `address_lookup_events`. Setting `ENABLE_UPSTREAM_GEOCODER=false` runs the service fully offline against its own store.

## Prerequisites

- Python 3.12
- Node.js 20
- npm
- Docker and Docker Compose for local PostGIS and containerized startup

Docker is optional for linting and unit tests, but required for the full local stack.

## Configuration

Copy the example environment file and edit values for your machine:

```powershell
Copy-Item .env.example .env
```

Important variables:

- `APP_ENV`: `development`, `staging`, or `production`
- `API_CORS_ORIGINS`: comma-separated browser origins
- `ALLOWED_HOSTS`: comma-separated host headers accepted by FastAPI
- `DATABASE_URL`: async SQLAlchemy URL for PostGIS
- `NOMINATIM_BASE_URL`: Nominatim endpoint
- `NOMINATIM_USER_AGENT`: required for compliant Nominatim usage
- `ENABLE_UPSTREAM_GEOCODER`: `true` to call Nominatim, `false` for offline/local-only mode
- `REVERSE_SEARCH_RADIUS_METERS`: local reverse-geocode radius before provider fallback
- `GEOCODE_RESULT_LIMIT`: max candidates returned by lookup/autocomplete

Never commit real secrets. Use GitHub Actions secrets and AWS Systems Manager Parameter Store or AWS Secrets Manager for deployed environments.

## Backend

Install dependencies:

```powershell
cd backend
python -m pip install -r requirements.txt
```

Run tests with coverage:

```powershell
python -m pytest --cov=app --cov-report=term-missing
```

Start the API:

```powershell
uvicorn app.main:app --reload
```

API docs are available in non-production environments at:

- `http://localhost:8000/docs`
- `http://localhost:8000/redoc`

## Frontend

Install dependencies:

```powershell
cd frontend
npm ci
```

Run local development server:

```powershell
npm run dev
```

Run checks:

```powershell
npm run typecheck
npm run lint
npm run build
npm audit --audit-level=moderate
```

## Docker Compose

When Docker is available:

```powershell
docker compose up --build
```

Services:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8000`
- API docs: `http://localhost:8000/docs`
- PostGIS: `localhost:5432`

## API Examples

Health (liveness) and readiness:

```powershell
Invoke-RestMethod http://localhost:8000/api/v1/health
Invoke-RestMethod http://localhost:8000/api/v1/health/ready
```

Autocomplete (PostgreSQL trigram similarity over stored addresses):

```powershell
Invoke-RestMethod "http://localhost:8000/api/v1/geocode/search?q=350%205th%20ave&limit=5"
```

Forward lookup:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8000/api/v1/geocode/lookup `
  -ContentType application/json `
  -Body '{"query":"1600 Pennsylvania Ave NW"}'
```

Reverse geocode:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8000/api/v1/geocode/reverse `
  -ContentType application/json `
  -Body '{"latitude":38.8977,"longitude":-77.0365}'
```

Create address:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8000/api/v1/addresses `
  -ContentType application/json `
  -Body '{"formatted_address":"350 5th Ave, New York, NY","latitude":40.7484,"longitude":-73.9857,"confidence":0.95,"source":"manual"}'
```

List addresses:

```powershell
Invoke-RestMethod "http://localhost:8000/api/v1/addresses?limit=25&offset=0"
```

## Operational Notes

- FastAPI returns a consistent JSON error envelope with `error.code`, `error.message`, and `error.request_id`.
- Security headers and gzip compression are enabled by middleware.
- React output is escaped by default; backend string inputs are trimmed and reject control characters.
- Database migrations create PostGIS and trigram indexes for spatial and fuzzy lookup performance.
- The frontend production container serves static files through Nginx.

## Troubleshooting

- `npm ci` fails: confirm `frontend/package-lock.json` exists and Node.js 20 is active.
- Vite build fails with `spawn EPERM`: rerun from a terminal with permission to execute binaries from `node_modules`.
- Pytest cache warnings on Windows: run pytest from a normal shell with write access to `backend/.pytest_cache`.
- `TrustedHostMiddleware` returns 400: add the hostname to `ALLOWED_HOSTS`.
- Nominatim returns 403 or 429: set a valid `NOMINATIM_USER_AGENT`, add caching, and use a compliant provider or dedicated Nominatim instance for production.
