# Time-Series Analytics

IoT device metrics platform: **Cassandra** for time-series storage (TTL + TWCS), **Redis** for
real-time aggregations and pub/sub, **InfluxDB + Grafana** for platform telemetry and dashboards,
**FastAPI** backend with **Prophet**-based anomaly detection (z-score fallback), **React**
operator UI with JWT auth. Deployed to **DigitalOcean** via **GitLab CI/CD**.

## Features

**Device fleet management**
- Register devices via UI or API; each device gets a one-time API key (only its hash is stored)
- Enable / disable / delete devices — disabling instantly revokes the device's ingest rights
- Browse the fleet in the dashboard with per-device status

**Metric ingestion**
- Batched HTTP ingestion authenticated by per-device keys (`<device_id>.<secret>`) or a shared gateway key
- Per-point policy enforcement: too-old / future-dated points and unknown or disabled devices are
  rejected and counted back to the caller (`{"accepted": n, "rejected": m}`)
- Per-device rate limiting (`429` + `Retry-After`) and batch-size caps

**Time-series storage**
- Raw samples in Cassandra: day-bucketed partitions, 30-day TTL, TWCS compaction (expiry = SSTable drop)
- Automatic hourly rollups (min/max/avg/sum/count) kept 365 days, with a post-outage `--backfill` mode
- Series catalog maintained automatically — the platform learns which metrics each device reports

**Querying & live data**
- Range queries with automatic raw-vs-rollup routing (ranges >48 h served from rollups) and
  stride decimation of oversized responses
- Real-time 1-minute window stats (count / sum / avg / min / max / latest) straight from Redis,
  folded atomically by a Lua script
- Server-sent-events stream of anomalies as they are detected (`/api/v1/events/anomalies`)

**Anomaly detection**
- Prophet forecast-band detection per (device, metric) with an always-available z-score fallback
- Periodic fleet scans driven by the series catalog; results persisted 90 days and published to
  Redis pub/sub
- Horizontal worker scale-out via fleet sharding (`WORKER_SHARD_INDEX` / `WORKER_SHARD_COUNT`)

**Operator dashboard (React)**
- JWT sign-in with role-based UI (viewer / operator / admin)
- Device list + registration form with one-time key reveal
- Metric charts with anomaly overlay, light/dark theme, an accessible table view, and
  time-range + metric pickers driven by the catalog
- Recent-anomalies feed per device

**Platform observability**
- Ingest rate/rejections/latency, API latency by route template, and worker scan stats shipped
  to InfluxDB; provisioned Grafana dashboard over that telemetry
- Liveness/readiness probes with per-dependency detail; the API runs in degraded mode (503s on
  affected endpoints only) instead of crashing when a backend is down

**Security & operations**
- Two credential planes: device keys can only ingest, user tokens can only query/manage
- PBKDF2 password hashing, hashed API keys, bootstrap admin, and a production safety rail that
  refuses to start with placeholder secrets
- Unified error shape with request-ID correlation, gzip on API and UI, one-command Docker stack,
  GitLab CI/CD pipeline (lint → test → build → deploy)

## Documentation

- [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) — file structure + phased implementation checklist
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — pattern, component/data-flow diagrams, security
- [docs/TECH-NOTES.md](docs/TECH-NOTES.md) — CI/CD, testing, deployment, env management, pitfalls
- [docs/API.md](docs/API.md) — every endpoint with curl examples and error semantics

## Quickstart (local)

```bash
cp .env.example .env                      # then edit secrets

# 1. Data plane (Cassandra, Redis, InfluxDB, Grafana)
docker compose up -d cassandra redis influxdb grafana
./scripts/apply_migrations.sh             # waits on cassandra being healthy

# 2. Backend API (http://localhost:8000/docs)
cd backend
pip install -e .[dev]
uvicorn app.main:app --reload
# a bootstrap admin is created on startup: ADMIN_USERNAME / ADMIN_PASSWORD

# 3. Workers (separate terminals)
python -m app.workers.anomaly_worker
python -m app.workers.downsampler                 # hourly rollups
python -m app.workers.downsampler --backfill 24   # one-off: re-rollup 24h

# 4. Frontend (http://localhost:5173 — sign in with the admin credentials)
cd ../frontend
npm install
npm run dev

# 5. Demo traffic (registers sim devices via the API, then streams metrics)
python scripts/seed_demo_data.py
```

Everything in containers instead: `docker compose --profile app up -d --build`
→ UI at http://localhost:8080, API at http://localhost:8000, Grafana at
http://localhost:3000 (see `GRAFANA_ADMIN_*` in `.env`).

## Tests

```bash
# backend: unit + API tests (fast, no databases needed)
cd backend && python -m pytest tests -q --cov=app

# backend: integration tests against the real data plane
docker compose up -d cassandra redis
python -m pytest tests/integration -q -m requires_stack

# frontend
cd frontend && npm test
```

Lint/type: `ruff check app tests && mypy app` (backend), `npm run lint && npx tsc --noEmit`
(frontend). CI runs all of the above (see `.gitlab-ci.yml` + `ci/`).

## API surface (v1)

| Area | Endpoints |
|---|---|
| Auth | `POST /api/v1/auth/token`, `GET /api/v1/auth/me` |
| Ingest | `POST /api/v1/ingest` (X-API-Key: gateway key or per-device `<id>.<secret>`) |
| Devices | `GET/POST /api/v1/devices`, `GET/DELETE /api/v1/devices/{id}`, `PATCH /api/v1/devices/{id}/enabled` |
| Metrics | `GET /api/v1/devices/{id}/metrics` (catalog), `GET .../metrics/{m}` (range; raw/rollup auto), `GET .../metrics/{m}/live` |
| Anomalies | `GET /api/v1/devices/{id}/anomalies`, `GET /api/v1/events/anomalies` (SSE) |
| Health | `GET /api/v1/health/live`, `GET /api/v1/health/ready` |

Interactive OpenAPI docs at `/docs` (non-production). Full reference with curl examples:
[docs/API.md](docs/API.md).

## Troubleshooting

- **Cassandra container "unhealthy" right after start** — first boot takes 60–90 s before
  `cqlsh` responds; the healthcheck's `start_period` covers this. Check progress with
  `docker logs tsa-cassandra-1`. Docker Desktop needs ≥4 GB for its VM.
- **Port already in use** — the UI host port is `FRONTEND_PORT` (default 8080) in `.env`;
  API 8000, Grafana 3000, InfluxDB 8086, Cassandra 9042, Redis 6379 are fixed in
  `docker-compose.yml`.
- **API exits at startup with "placeholder secrets in production"** — deliberate: with
  `ENVIRONMENT=production` the app refuses `changeme*` values for `JWT_SECRET`,
  `ADMIN_PASSWORD`, `DEVICE_API_KEY`. Set real values in the server `.env`.
- **`cassandra.DependencyException` on Python 3.12 (Windows dev)** — the driver needs the
  `asyncore` shim when the libev extension is missing; it is a declared dependency
  (`pyasyncore`), so `pip install -e .[dev]` inside `backend/` fixes it.
- **`requires_stack` tests skip** — expected without a running data plane; start it with
  `docker compose up -d cassandra redis` first. In CI they run against GitLab services.
- **Login fails with the README credentials** — the bootstrap admin is created only when
  Cassandra was reachable at API startup *and* migrations were applied; check
  `/api/v1/health/ready` and re-run `./scripts/apply_migrations.sh`, then restart the API.
- **No anomalies appearing** — the worker needs `MIN_HISTORY_POINTS` (default 48) samples
  per series and scans every `DETECTION_INTERVAL_SECONDS` (default 300); lower both in
  `.env` for demos. Check `docker logs tsa-anomaly-worker-1` for "scan complete".
- **`docker compose exec` can't find migration files (Git Bash)** — path mangling; use the
  provided `scripts/apply_migrations.sh` (it sets `MSYS_NO_PATHCONV`).

## Layout

```
backend/   FastAPI app, workers, Cassandra migrations, tests
frontend/  React 18 + TS + Vite dashboard (JWT auth, device management)
infra/     Grafana provisioning, DigitalOcean setup
ci/        GitLab CI job definitions (included by .gitlab-ci.yml)
scripts/   migrations runner, demo data seeder
docs/      plan / architecture / tech notes
```
