# Time-Series Analytics — Project Plan

**Platform:** IoT device metrics storage, real-time aggregation, visualization, and anomaly detection.

| Concern | Technology | Role |
|---|---|---|
| Ingest & query API | Python 3.12 + FastAPI | Stateless HTTP API, async I/O, JWT + API-key auth |
| Raw & rollup time-series storage | Cassandra | Write-heavy storage, TTL-based expiry, TWCS compaction |
| Real-time aggregations | Redis | Atomic per-window stats (Lua), device cache, rate limits, pub/sub |
| Platform/system metrics | InfluxDB 2.x | Ingest rate, API latency, worker health |
| Dashboards | Grafana | Provisioned dashboards over InfluxDB |
| Anomaly detection | Prophet (z-score fallback) | Forecast-band anomaly flagging per device/metric |
| Operator UI | React 18 + TypeScript + Vite | Login, device management, metric charts, anomaly feed |
| Deployment | DigitalOcean (Droplet → DOKS growth path) | Docker Compose first, Kubernetes when needed |
| CI/CD | GitLab CI/CD (`.gitlab-ci.yml`) | lint → test (unit + live-stack integration) → build → deploy |

> Note: the brief said "GitLab Actions" — the GitLab-native CI system is **GitLab CI/CD**,
> configured via `.gitlab-ci.yml`. That is what this repo uses.

---

## 1.1 Project File Structure

```text
3-Time-Series-Analytics/
├── claude-fable-5.txt                  # scaffold marker (generating model)
├── README.md                           # quickstart, API surface, test commands
├── .gitignore / .gitattributes
├── .env.example                        # single source of truth for env vars
├── .gitlab-ci.yml                      # root pipeline: stages + includes
├── Makefile                            # dev shortcuts (up, migrate, test, lint)
├── docker-compose.yml                  # local dev: data plane + app profile
├── docker-compose.prod.yml             # production overlay: registry images
│
├── ci/                                 # GitLab CI job definitions (included by root)
│   ├── lint.gitlab-ci.yml              # ruff + mypy / eslint + tsc + prettier
│   ├── test.gitlab-ci.yml              # pytest (cov ≥70) + live-stack integration + vitest
│   ├── build.gitlab-ci.yml             # kaniko: backend(api) / worker(ml) / frontend images
│   └── deploy.gitlab-ci.yml            # staging (auto, main) / prod (manual, tags); migrations first
│
├── docs/
│   ├── PROJECT-PLAN.md                 # this file
│   ├── ARCHITECTURE.md                 # pattern, diagrams, data flow, security
│   └── TECH-NOTES.md                   # CI/CD, testing, deployment, env mgmt, pitfalls
│
├── backend/                            # FastAPI service + background workers (one context, two image targets)
│   ├── Dockerfile                      # targets: api (slim) / worker (+Prophet)
│   ├── pyproject.toml                  # deps + ruff + mypy + pytest/coverage config
│   ├── app/
│   │   ├── main.py                     # app factory, lifespan, admin bootstrap,
│   │   │                               # unified error shape, request-id + latency telemetry
│   │   ├── api/
│   │   │   ├── deps.py                 # JWT user / operator gate / ingest principal (gateway|device key)
│   │   │   └── v1/
│   │   │       ├── router.py           # aggregates v1 under /api/v1
│   │   │       ├── health.py           # /health/live, /health/ready (per-dep status)
│   │   │       ├── auth.py             # POST /auth/token, GET /auth/me
│   │   │       ├── devices.py          # registry CRUD + enable/disable
│   │   │       ├── ingest.py           # batch intake (202 accepted/rejected)
│   │   │       ├── metrics.py          # catalog, range query (raw/rollup + decimation), live
│   │   │       ├── anomalies.py        # anomaly feed
│   │   │       └── events.py           # SSE stream from Redis pub/sub
│   │   ├── core/
│   │   │   ├── config.py               # pydantic-settings (all env vars)
│   │   │   ├── logging.py              # plain dev / JSON prod logs
│   │   │   └── security.py             # PBKDF2 passwords, device keys, JWTs
│   │   ├── db/                         # lazy connection lifecycles, degraded-mode tolerant
│   │   │   ├── cassandra.py            # prepared-stmt cache + single-partition unlogged batches
│   │   │   ├── redis.py
│   │   │   └── influx.py               # fire-and-forget telemetry writes
│   │   ├── repositories/               # ALL CQL lives here
│   │   │   ├── devices.py / metrics.py / anomalies.py
│   │   │   ├── users.py                # dashboard users (LWT bootstrap)
│   │   │   └── series_catalog.py       # device → metric names
│   │   ├── schemas/                    # pydantic request/response models
│   │   │   ├── device.py / metric.py / anomaly.py / auth.py
│   │   ├── services/                   # business logic, no HTTP or CQL details
│   │   │   ├── ingestion.py            # policy filter → auth → rate limit → write → side effects
│   │   │   ├── aggregation.py          # window math + atomic Lua fold + pub/sub
│   │   │   ├── device_registry.py      # repo + Redis permission cache + key checks
│   │   │   └── anomaly_detection.py    # Prophet bands / z-score
│   │   └── workers/
│   │       ├── anomaly_worker.py       # catalog-driven scan, fleet sharding, pub/sub
│   │       └── downsampler.py          # hourly rollups + --backfill N
│   ├── migrations/
│   │   ├── README.md
│   │   └── cassandra/                  # ordered, idempotent CQL
│   │       ├── 001_keyspace.cql
│   │       ├── 002_metrics_tables.cql  # raw (30d TTL, TWCS 1d) + rollups (365d TTL)
│   │       ├── 003_devices_and_anomalies.cql
│   │       └── 004_users_and_catalog.cql
│   └── tests/
│       ├── conftest.py                 # offline app client + auth-header fixtures
│       ├── unit/                       # security, policy, decimation, windows, buckets,
│       │   │                           # z-score detection, worker orchestration
│       └── integration/
│           ├── test_*_api.py           # auth/devices/ingest/metrics/health flows (fakes)
│           ├── stack_utils.py          # CQL loader + reachability probes
│           └── test_stack_roundtrip.py # REAL Cassandra/Redis end-to-end (requires_stack)
│
├── frontend/                           # React 18 + TS + Vite dashboard
│   ├── Dockerfile / nginx.conf         # node build → nginx (+ /api proxy, SSE-safe, sec headers)
│   ├── package.json / package-lock.json
│   ├── tsconfig.json / vite.config.ts  # vitest configured (jsdom)
│   ├── eslint.config.js / .prettierrc
│   └── src/
│       ├── main.tsx / App.tsx          # auth gate: login → dashboard, sign-out
│       ├── theme.ts                    # validated palette tokens, light+dark
│       ├── api/client.ts               # typed fetch, ApiError, authStore
│       ├── types/index.ts
│       ├── hooks/useMetrics.ts         # polling hooks: series/anomalies/catalog
│       ├── pages/
│       │   ├── LoginPage.tsx           # validated sign-in form
│       │   └── DashboardPage.tsx       # catalog-driven metric picker, ranges
│       ├── components/
│       │   ├── DeviceList.tsx          # + operator actions (enable/disable/delete)
│       │   ├── DeviceForm.tsx          # registration + one-time API key reveal
│       │   ├── MetricsChart.tsx        # line + anomaly overlay + table view
│       │   └── AnomalyList.tsx
│       └── test/                       # vitest + RTL: client, LoginPage, DeviceList
│
├── infra/
│   ├── grafana/
│   │   ├── provisioning/{datasources,dashboards}/*.yml
│   │   └── dashboards/iot-platform-overview.json
│   └── digitalocean/
│       ├── README.md                   # droplet sizing, topology, DNS/firewall notes
│       └── droplet-setup.sh            # one-time host bootstrap
│
└── scripts/
    ├── apply_migrations.sh             # ordered CQL through the cassandra container
    └── seed_demo_data.py               # admin login → register sim devices → stream metrics
```

### Layout rationale

- **`backend/app` layering** — `api → services → repositories → db` is strictly one-directional.
  CQL never appears above `repositories/`; HTTP concerns never appear below `api/`. The ingest
  path is independently extractable (see ARCHITECTURE.md growth path).
- **Workers share the backend build context** — one dependency set, two image targets
  (`api` slim, `worker` +Prophet). No drift between API-side and worker-side code.
- **Migrations are plain, ordered, idempotent CQL** — numbered files + a tiny apply script;
  idempotency is asserted by the integration suite (applies the set twice).
- **`ci/` split by stage** — the root `.gitlab-ci.yml` stays a 20-line table of contents.
- **`infra/` is deployment-only** — nothing in `backend/` or `frontend/` knows about
  DigitalOcean or Grafana provisioning.

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority) — ✅ complete

- [x] Repository scaffold: directory layout, docs, stubs, configs
- [x] `docker compose up` brings up Cassandra, Redis, InfluxDB, Grafana with healthchecks
- [x] Cassandra migrations 001–004 via `scripts/apply_migrations.sh`; TTL + TWCS in schema
- [x] `core/config.py` settings wired through all db clients; readiness (not liveness) reports down backends
- [x] Ingest happy path end-to-end: `POST /api/v1/ingest` → `metrics_raw` write → Redis window update
- [x] Raw metric range query: `GET /devices/{id}/metrics/{metric}?start&end`
- [x] Unit tests green (window math, z-score detector, buckets); API smoke tests green
- [x] GitLab pipeline: lint + test stages defined and consistent with local commands
- [x] Seed script generating realistic device traffic (self-registers via the API)

### Phase 2 — Core features (medium priority) — ✅ complete

- [x] Device registry: register/list/disable/delete; per-device API keys **hashed at rest**
      (`<device_id>.<secret>` format; secret never stored)
- [x] JWT auth: `users` table (PBKDF2), bootstrap admin from env, `POST /auth/token`,
      role gates (viewer read, operator/admin mutate)
- [x] Downsampler worker: hourly 1h rollups (min/max/avg/sum/count), `--backfill N` recovery mode
- [x] Query planner: ranges >48h automatically served from `metrics_rollup_1h`
- [x] Redis live aggregates: 1-minute windows with **atomic min/max via Lua**; `/metrics/{m}/live`
- [x] Anomaly worker: Prophet (z-score fallback) per (device, metric) discovered from the
      series catalog; persists to `anomalies`; publishes `anomalies:{device_id}`
- [x] InfluxDB platform metrics: ingest rate/rejections/latency, API request latency by route
      template, worker scan stats
- [x] Grafana: provisioned InfluxDB datasource + platform-overview dashboard as JSON
- [x] React dashboard: login, device list + management + registration (one-time key reveal),
      catalog-driven metric picker, chart with anomaly overlay + table view, anomaly feed
- [x] Integration tests against real Cassandra/Redis (`requires_stack` suite; runs in CI as
      GitLab services, locally against `docker compose up cassandra redis`; asserts migration
      idempotency and the full register→ingest→query→disable lifecycle)
- [x] Build stage: kaniko images (backend/worker/frontend) → GitLab registry;
      deploy stage: staging auto-deploy from `main` (needs DO host + secrets in GitLab)

### Phase 3 — Polish & optimization (lower priority) — partially complete

- [x] Rate limiting on ingest: per-device points/minute budget in Redis (fail-open), 429 + Retry-After
- [x] Late/out-of-order data policy: reject points older than `INGEST_MAX_AGE_HOURS` or beyond
      clock-skew tolerance; counted in the `rejected` response field
- [x] Real-time push: `GET /api/v1/events/anomalies` SSE endpoint fed by Redis pub/sub
      (UI currently polls; switching it to SSE is a client-only change)
- [x] Worker fleet sharding: `WORKER_SHARD_INDEX/COUNT` (crc32 partitioning) for both workers
- [x] Production deploy gate: manual, tag-driven (`vX.Y.Z`), migrations applied before rollout
- [x] Response decimation: series larger than `MAX_SERIES_POINTS` stride-sampled (flagged in payload)
- [x] Response compression: gzip on the API (large series JSON) and nginx (static + proxied)
- [x] Production safety rail: API refuses to start with placeholder (`changeme*`) secrets
      when `ENVIRONMENT=production`; JWT secret placeholder meets the RFC 7518 length floor
- [x] Docs round 2: full endpoint reference with curl examples (docs/API.md),
      README troubleshooting section
- [ ] Prophet tuning: per-metric seasonality config, model artifact caching, retrain cadence
- [ ] Grafana alerting rules (ingest stalls, anomaly bursts, worker lag) → email/Slack
- [ ] Load testing (Locust) against staging; document max sustained points/sec per droplet size
- [ ] Capacity & retention review after real traffic: partition sizes, TTL tuning, TWCS windows
- [ ] Observability: OpenTelemetry traces; ship JSON logs to a central sink; Sentry
- [ ] Security hardening round 2: TLS to data stores, secret rotation runbook, dependency scanning
- [ ] Growth decision point: extract ingest service and/or migrate to DOKS when a droplet saturates

### Definition of done (per feature)

Code + tests + lint clean + docs touched (if behavior changed) + deployed to staging + verified via seed traffic.
