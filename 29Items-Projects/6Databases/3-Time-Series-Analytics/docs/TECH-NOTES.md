# Time-Series Analytics — Technical Notes

## 3.1 CI/CD Pipeline Design (GitLab CI/CD)

Root `.gitlab-ci.yml` declares the stage graph and includes per-stage job files from `ci/`:

```
lint  ──►  test  ──►  build  ──►  deploy
```

| Stage | Jobs | Trigger | Notes |
|---|---|---|---|
| **lint** | `lint:backend` (ruff + mypy), `lint:frontend` (eslint + tsc + prettier --check) | every MR + main | Fast fail (< 1 min); pip/npm caches keyed on lockfiles |
| **test** | `test:backend` (pytest, `--cov-fail-under=70`), `test:backend:integration` (the `requires_stack` suite against **real Cassandra + Redis GitLab services**), `test:frontend` (vitest + production build check) | every MR + main | The integration fixture waits out Cassandra's slow boot (`TSA_STACK_RETRIES`); locally the same tests run against `docker compose up cassandra redis` and self-skip when absent |
| **build** | `build:backend-image` (api target), `build:worker-image` (worker target, +Prophet), `build:frontend-image` via **kaniko** (no privileged DinD) | main + tags | Tags: `$CI_COMMIT_SHORT_SHA` always, plus `vX.Y.Z` on tag pipelines; pushed to GitLab Container Registry |
| **deploy** | `deploy:staging` (auto on `main`), `deploy:production` (**manual**, on `v*` tags, protected) | see left | SSH to droplet → sync migrations → data plane up → `apply_migrations.sh` → `compose pull && up -d --wait`; rollback = redeploy previous tag |

Conventions:
- MR pipelines must be green to merge (`lint` + `test` are required checks).
- `deploy:production` uses a protected environment + protected variables — only maintainers.
- Pipeline files live in `ci/*.gitlab-ci.yml`; keep the root file a table of contents.

## 3.2 Testing Strategy

**Pyramid: many unit / some integration / few E2E.**

- **Unit (pytest)** — pure logic: window bucketing, z-score detection, ingest age policy,
  decimation, credential primitives, worker orchestration (fake repos). No network, no
  containers. Coverage gate: **`--cov-fail-under=70`** enforced in CI (currently ~76% without
  the stack suite). API-level tests run the real FastAPI app forced into degraded mode with
  the repository layer faked — auth planes, error shapes, routing, and policy are all
  asserted without a database. Frontend: **vitest + React Testing Library** for the API
  client, login form, and device list (loading/error/empty states).
- **Integration (`requires_stack` marker)** — the same app against **real Cassandra and
  Redis**: migration idempotency (the whole CQL set applied twice), admin bootstrap + login,
  register → per-device-key ingest → raw query round trip, live Redis aggregates, catalog
  maintenance, key scoping, disable semantics. In CI the databases are GitLab `services:`;
  locally `docker compose up -d cassandra redis` and the suite runs, otherwise it self-skips.
  Mocking Cassandra semantics lies to you; don't.
- **E2E (Playwright)** — 2–3 golden paths only, against staging after deploy: login → pick
  device → chart renders → anomaly appears after seeding a spike (via `scripts/seed_demo_data.py`).
  Keep the suite < 5 min; E2E is a smoke alarm, not a test suite.
- **Load (Locust, Phase 3)** — ingest throughput per droplet size; documented, re-run before
  capacity decisions.

## 3.3 Deployment Strategy

**Containerize everything; compose-first on DigitalOcean; DOKS is the growth path, not the start.**

- **Images:** `backend` (FastAPI; same image runs workers via different command) and `frontend`
  (multi-stage: node build → nginx). Data services run as official images with pinned versions.
- **Environments:**
  - *Staging:* 1 DO droplet (4 vCPU / 8 GB) running the full `docker-compose.prod.yml` stack.
    Auto-deployed from `main`.
  - *Production:* app droplet(s) behind a **DO Load Balancer** (TLS termination); Cassandra on
    dedicated droplet(s) with attached block storage; Redis/InfluxDB/Grafana co-located or
    managed equivalents (DO Managed Redis is a drop-in). All inter-service traffic on the
    **VPC private network**; only LB + SSH public.
- **Deploy mechanics:** CI SSHes to the host, writes the release tag into `.env`, then
  `docker compose pull && docker compose up -d --wait` (healthcheck-gated). Rollback is the
  same command with the previous tag — images are immutable, config is env-only.
- **Migrations:** applied explicitly (CI job or operator) via `scripts/apply_migrations.sh`
  *before* the app rollout; all CQL is `IF NOT EXISTS`-idempotent.
- **When to move to DOKS:** >2 app droplets, or workers need independent autoscaling, or
  zero-downtime deploys become contractual. The compose files translate 1:1 to manifests.

## 3.4 Environment Management

- **Rule: config lives in the environment; code never branches on environment names** —
  it reads typed settings (`app/core/config.py`, pydantic-settings). `ENVIRONMENT` is used for
  log formatting and safety rails only.
- Local dev: copy `.env.example` → `.env` (gitignored); compose interpolates the same file.
- CI: GitLab CI/CD variables (masked; protected for prod scope).
- Servers: `/opt/tsa/.env` provisioned by `infra/digitalocean/droplet-setup.sh` + CI deploy job.
- **`.env.example` is the contract:** every variable the app reads appears there with a safe
  default or a `changeme` placeholder. Adding a setting without updating it fails code review.

Template (kept authoritative in [`/.env.example`](../.env.example) — the groups, with defaults
elided here):

```dotenv
# runtime         ENVIRONMENT, LOG_LEVEL, LOG_FORMAT
# api             API_HOST, API_PORT, CORS_ORIGINS, JWT_SECRET, JWT_ALGORITHM,
#                 ACCESS_TOKEN_EXPIRE_MINUTES, ADMIN_USERNAME, ADMIN_PASSWORD,
#                 DEVICE_API_KEY, MAX_INGEST_BATCH_SIZE
# ingest policy   INGEST_MAX_AGE_HOURS, INGEST_FUTURE_TOLERANCE_MINUTES,
#                 INGEST_RATE_LIMIT_PER_MINUTE
# cassandra       CASSANDRA_CONTACT_POINTS, CASSANDRA_PORT, CASSANDRA_KEYSPACE,
#                 CASSANDRA_LOCAL_DC, ROLLUP_QUERY_THRESHOLD_HOURS, MAX_SERIES_POINTS,
#                 RAW_TTL_SECONDS / ROLLUP_TTL_SECONDS (informational; schema enforces)
# redis           REDIS_URL, LIVE_WINDOW_SECONDS, LIVE_KEY_TTL_SECONDS,
#                 DEVICE_CACHE_TTL_SECONDS
# influxdb        INFLUXDB_URL, INFLUXDB_ORG, INFLUXDB_BUCKET, INFLUXDB_TOKEN
#                 (+ INFLUXDB_INIT_* consumed only by compose bootstrap)
# detection       DETECTION_INTERVAL_SECONDS, DETECTION_METHOD, PROPHET_INTERVAL_WIDTH,
#                 ZSCORE_THRESHOLD, MIN_HISTORY_POINTS,
#                 WORKER_SHARD_INDEX, WORKER_SHARD_COUNT
# grafana         GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD (compose bootstrap)
# frontend        VITE_API_BASE_URL (build-time)
# deploy (server) IMAGE_TAG, CI_REGISTRY_IMAGE (written by the deploy job)
```

## 3.5 Version Control Workflow

**Trunk-based development with short-lived branches (GitLab Flow flavor).**

- `main` is always deployable; it auto-deploys to **staging**.
- Feature branches (`feat/...`, `fix/...`) live **< 2–3 days**, merge via MR with green pipeline
  + 1 review. Squash-merge for a linear, revertable history.
- **Production = annotated tags** (`v1.4.0`): tagging triggers the build+manual-deploy pipeline.
  Rollback = redeploy previous tag. Hotfix = branch from `main` (or the tag if main has drifted),
  fix, tag.
- Incomplete features ship behind flags/env toggles rather than long-lived branches.

*Why not Gitflow:* one small team, one product, continuous deployment — `develop`/`release`
branches add merge ceremony and stale-branch drift with zero benefit here. *Why not pure GitHub
Flow:* we want an explicit, auditable gate (tag + manual job) between staging and production.

## 3.6 Common Pitfalls (this stack specifically)

1. **Cassandra: unbounded partitions.** Omitting the time bucket from the partition key makes
   partitions grow forever and read latency climb for the *oldest, most-queried* devices.
   Bucket by day for raw, by month for rollups. Non-negotiable.
2. **Cassandra: TTL ≠ free deletes unless compaction cooperates.** Use TWCS with the window
   matched to TTL granularity, write in time order, and **never mix wildly different TTLs in
   one table** — otherwise SSTables can't drop whole and you inherit tombstone pain. Also:
   changing table-level `default_time_to_live` does not rewrite already-written cells.
3. **Cassandra driver blocks the event loop.** `cassandra-driver` is synchronous; calling
   `session.execute()` inside `async def` freezes every request. Wrap in `asyncio.to_thread`
   (as the repos do) or adopt an async driver deliberately.
4. **`ALLOW FILTERING` is a design smell.** If a query needs it, the table doesn't match the
   query — create a purpose-built table (query-first modeling) instead of shipping it.
5. **Prophet operational weight.** Fits are CPU-bound and need history (≥ 2 seasonal cycles for
   useful daily seasonality). Enforce min-history guardrails, fit on rollups not raw, keep it
   out of the API image if image size hurts (`[ml]` extra), and always keep the z-score
   fallback path working — Prophet install (cmdstan) can fail on fresh platforms.
6. **Redis as accidental system of record.** Live aggregates must be rebuildable from Cassandra.
   Every key gets a TTL; memory policy `volatile-ttl`; if Redis flushes, the platform limps —
   never lies. Watch hot keys when one device dominates traffic.
7. **InfluxDB cardinality explosions.** Tag values must be low-cardinality (site, env, droplet).
   `device_id` as a *tag* on platform metrics will melt the index once the fleet grows — keep
   per-device data in Cassandra, aggregate counts in Influx.
8. **Grafana click-ops drift.** Dashboards edited in the UI and never exported silently diverge
   from git. Provisioning is code (`infra/grafana/`); export JSON back into the repo as part of
   any dashboard change.
9. **Clock skew and late data.** Devices lie about time. Record server receive-time alongside
   device timestamps, reject points older than a policy window, and make the anomaly worker
   tolerate gaps — Prophet handles missing points, but only if you don't zero-fill them.
10. **Compose-on-Windows dev quirks.** Cassandra in Docker Desktop wants ≥4 GB for the VM;
    first boot takes ~1–2 min before `cqlsh` responds (healthcheck `start_period` covers this).
    Line endings: keep `.sh` scripts LF (`.gitattributes` if the team is mixed-OS).
11. **One `.env` to rule them all — accidentally shared.** Compose interpolation and app runtime
    both read `.env`; a prod value pasted into a dev file (or vice versa) is the classic incident.
    The deploy pipeline writes server env files; humans don't.
