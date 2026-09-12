# Static Analyzer

A local-first static analysis application for C/C++ code. The backend extracts source facts, evaluates configurable rules, uses Z3 for simple path feasibility checks, persists results, and exposes them to a React dashboard.

## What Runs

- FastAPI backend on `http://localhost:8000`
- React dashboard on `http://localhost:5173`
- SQLite database for local and Docker development
- Optional C++ analyzer binary under `analyzer/libtooling`

## Quick Start

Prerequisites:

- Python 3.11+
- Node.js 20+
- Docker Desktop, if you want the containerized path

Fastest start with Docker:

```powershell
docker compose up --build
```

Then open:

- Dashboard: `http://localhost:5173`
- Backend API docs: `http://localhost:8000/docs`

Start locally for development:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r backend\requirements.txt

Push-Location frontend
npm ci
Pop-Location

.\scripts\run-dev.ps1
```

Open `http://localhost:5173`.

Use the app:

1. Enter a project name, source path, and C/C++ source code in the left panel.
2. Click `Run`.
3. Select the created analysis from the list.
4. Review findings, severity counts, function/call metrics, and rule results.
5. Use the toolbar to download SARIF, rerun the analysis, or delete it.

Run the full test suite after setup:

```powershell
.\scripts\test.ps1
```

## Run Locally

```powershell
.\.venv\Scripts\Activate.ps1
.\scripts\run-dev.ps1
```

Open `http://localhost:5173`.

## Run With Docker

```powershell
docker-compose up --build
```

Open `http://localhost:5173`. The frontend proxies `/api` requests to the backend container.

## Backend API

OpenAPI is served by FastAPI:

- Swagger UI: `http://localhost:8000/docs`
- OpenAPI JSON: `http://localhost:8000/openapi.json`

Main endpoints:

- `GET /api/health`
- `GET /api/rules`
- `POST /api/analyses`
- `GET /api/analyses`
- `GET /api/analyses/{id}`
- `PATCH /api/analyses/{id}`
- `POST /api/analyses/{id}/rerun`
- `GET /api/analyses/{id}/findings`
- `GET /api/analyses/{id}/sarif`
- `DELETE /api/analyses/{id}`

List endpoints accept pagination:

- `GET /api/analyses?limit=50&offset=0`
- `GET /api/analyses/{id}/findings?limit=100&offset=0`

Create an analysis from inline source:

```powershell
$body = @{
  project_name = "demo"
  source_path = "sample.cpp"
  source_code = "int main(int argc, char** argv) { if (argc > 0) return 0; return 1; }"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8000/api/analyses -Body $body -ContentType "application/json"
```

## Analysis Rules

The default rule pack is in `config/rules.example.yaml`.

Implemented checks:

- unsafe C/C++ calls
- local null dereference
- unreachable statements after terminal flow
- tainted command execution
- Z3-backed simple numeric branch feasibility

## Tests

Backend:

```powershell
$env:PYTHONPATH='backend'
New-Item -ItemType Directory -Force -Path ".coverage-data" | Out-Null
$env:COVERAGE_FILE=".coverage-data\.coverage-local"
.\.venv\Scripts\python.exe -m coverage run -m pytest backend\tests
.\.venv\Scripts\python.exe -m coverage report
```

Frontend:

```powershell
Push-Location frontend
npm test
npm run build
Pop-Location
```

## Configuration

Copy `.env.example` to `.env` for local overrides. Production secrets should come from the deployment platform, not committed files.

Important variables:

- `DATABASE_URL`
- `RULES_CONFIG`
- `MIGRATIONS_PATH`
- `CORS_ORIGINS`
- `MAX_SOURCE_BYTES`
- `MAX_PAGE_SIZE`
- `GZIP_MIN_SIZE`
- `ENFORCE_HTTPS`
- `ALLOWED_HOSTS`

## Troubleshooting

- If frontend tests fail with `spawn EPERM` inside a sandboxed PowerShell session, run `.\scripts\test.ps1` outside the sandbox or run `npm test` from the `frontend` directory.
- If Docker health checks fail, inspect backend logs with `docker compose logs backend`.
- If file-based analysis returns a failed job, confirm the backend process can read the submitted `source_path`.
- If CORS blocks browser requests, add the frontend origin to `CORS_ORIGINS`.
