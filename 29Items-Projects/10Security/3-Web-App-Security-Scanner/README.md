# Web App Security Scanner

Automated DAST platform: **OWASP ZAP** scanning, **SQLMap** SQL-injection
testing, a **custom XSS payload engine**, and **ML-based severity
classification** of findings mapped to the OWASP Top 10.

| Layer | Stack |
|---|---|
| Backend | Python 3.12 · FastAPI · SQLAlchemy 2 (async) · Alembic · PostgreSQL |
| Scanners | ZAP (REST sidecar) · sqlmapapi (REST sidecar) · in-process XSS engine |
| ML | scikit-learn severity classifier (calibrated logistic regression over 24 features) |
| Frontend | Vue 3 · Vite · vue-router (nginx-served SPA) |
| Delivery | Docker Compose · GitHub Actions (CI: lint/type/test/build · CD: GHCR → staging → prod) |

> ⚠️ **Authorized testing only.** The platform enforces target scoping
> (deny-listed metadata/localhost IPs, private-range guard with DNS-rebinding
> re-check, admin-managed target allowlist) but operating it against systems
> you don't own remains your responsibility.

## What it can do

**Authentication & users**
1. Register new users (self-service, lands as read-only `viewer`)
2. Login with email/password → JWT access + rotating refresh token pair
3. Silent token refresh in the UI (single-flight, one retry, auto-logout on failure)
4. Machine auth via expiring API keys (`wss_…` prefix, SHA-256 hashed at rest, shown once) — issue/list/revoke from UI or API
5. Role-based access — `viewer` (read) < `scanner` (launch) < `admin` (manage) — enforced on every endpoint, for JWT *and* API-key principals
6. Admin user management: change roles, deactivate users, first-boot admin auto-seeded
7. Per-principal sliding-window rate limiting on sensitive endpoints (login, scan launch)

**Scanning**
8. Launch scans in 3 profiles: `fast` / `standard` / `deep` (depth raises sqlmap risk/level)
9. ZAP-driven DAST: spider crawl → active scan → alert normalization with plugin-id → OWASP Top-10 2021 mapping
10. SQL-injection testing via the sqlmapapi sidecar — confirmed injections become CWE-89 findings, severity by technique (union/error = critical, blind = high)
11. Custom XSS engine: canary-marked payload corpus (HTML-body/attribute/script/URI contexts) reflected against spider-discovered URLs; a hit = payload survived unescaped
12. Live progress polling per scan (phase + percent), cancellable mid-run (stops ZAP jobs too)
13. Phase-failure degradation: a failed phase marks the scan FAILED with machine-readable detail but keeps every finding already ingested
14. Scan timeouts, platform-wide concurrency cap, per-finding dedup (unique `scan_id + dedup_hash`)

**Findings & reports**
15. Unified findings feed regardless of scanner, with filters (severity, OWASP category, scan) + pagination
16. ML severity classification of every finding (24-feature calibrated logistic regression, deterministic rules fallback on low confidence)
17. Finding detail with evidence (payloads, response excerpts, redacted) via modal/endpoint
18. Severity histograms per scan and globally
19. HTML report export (Jinja2) and SARIF 2.1.0 export (GitHub Code Scanning compatible) per scan
20. `scan.completed` webhook POST with severity counts (Slack-compatible) when configured

**Safety guardrails**
21. Target deny-list (localhost, link-local, cloud metadata IPs) — always enforced
22. Private-range guard + DNS-rebinding re-check at request time (private targets only with an explicit dev/lab flag)
23. Admin-managed target allowlist (host/CIDR) with verify/remove; empty allowlist = bootstrap mode
24. Secrets redacted from stored evidence and logs; evidence auto-purged after a retention window

**Platform & ops**
25. `/health` liveness probe (Docker healthchecks wired to it)
26. `/metrics` Prometheus endpoint (scans by status, findings by severity, phase latencies, HTTP metrics)
27. Request-ID correlation + structured JSON access logs on every request
28. Consistent error envelope `{"error": {code, message, details, request_id}}` on 400/401/403/404/409/422/500
29. Alembic migrations run as a one-shot gated service before backend start
30. gzip compression on responses >1 KiB

**UI (Vue 3 SPA)**
31. Login/register page with client-side validation and error states
32. Dashboard: launch scans, watch live progress of the latest scan, cancel it, severity stats
33. Scan detail: progress bar, failure reasons, findings table, one-click HTML/SARIF download
34. Findings browser: filters, pagination, row-click evidence modal
35. Targets admin page (allowlist CRUD) and Users admin page (role/active toggles) — role-gated in nav *and* router
36. Responsive layout down to phone width

**Delivery**
37. `docker compose up --build` runs the whole stack (db, migrations, ZAP, sqlmap, backend, frontend)
38. Production compose: TLS termination (HTTP→HTTPS redirect, HSTS), 2 backend replicas, resource caps, ZAP API locked to backend, no data-plane ports exposed
39. CI: lint + type-check + tests (70% coverage gate) + pip-audit + secret scan + image builds
40. CD (opt-in): GHCR image push → staging deploy → ZAP self-scan dogfood gate that can block the release → production behind manual approval
41. `make test-e2e`: boots the stack + a deliberately vulnerable lab target and runs a real scan asserting findings are produced

*Not included (tracked as open in [PROJECT-PLAN](docs/PROJECT-PLAN.md)): scan scheduling/recurrence, Celery offload, per-user data isolation, audit log, Helm/K8s manifests.*

## What a scan does

`POST /api/v1/scans` dispatches a background pipeline (profiles: `fast`,
`standard`, `deep`):

1. **ZAP spider + active scan** — site crawl, then active rules; alerts are
   normalized with plugin-id → OWASP Top 10 2021 mapping.
2. **SQLMap** (`standard`/`deep`) — REST-driven sqlmapapi task; confirmed
   injections become CWE-89 findings, severity by technique (union/error =
   critical, blind = high). `fast` skips this phase.
3. **Custom XSS engine** — canary-marked payloads from a context-split corpus
   (HTML body / attribute / script / URI) are reflected against
   spider-discovered URLs; a hit means the marked payload survived unescaped.
4. **ML severity classification** — every finding is scored (24 features:
   source, rule bucket, text signals, keyword hits); low-confidence scores
   fall back to the deterministic rules baseline.
5. **Persistence + webhook** — deduplicated findings land in Postgres with
   capped evidence; a `scan.completed` webhook fires if configured.

Phase failures degrade, not abort: the scan is marked `failed` with
machine-readable detail, but findings already ingested stay queryable.
Progress is pollable (`GET /scans/{id}/progress`), cancellable
(`POST /scans/{id}/cancel`), and bounded by `SCAN_TIMEOUT_MINUTES`.

### The scanners are real (no simulation)

Nothing in the application code simulates or mocks scanning — a running
stack produces genuine attack traffic against the target:

- **ZAP** — the official `ghcr.io/zaproxy/zaproxy:stable` daemon runs as a
  sidecar; the backend drives its REST API exactly like the ZAP UI would:
  `POST /JSON/spider/action/scan` (a real crawler walks the target's links),
  `POST /JSON/ascan/action/scan` (the real active scanner attacks the
  target — genuine DAST probe traffic leaves the container), then
  `GET /JSON/alert/view/alerts` fetches the vulnerabilities it actually found.
- **SQLMap** — a real `sqlmapapi` server runs as a sidecar; the backend
  creates a task, aims it at the target, runs it, and parses the real sqlmap
  log for confirmed injections (parameter, technique, DBMS). Defaults are
  conservative (`risk=1, level=1`, no `--dump`/`--os-shell`) — prove the
  flaw, don't operate it; the `deep` profile raises risk/level to 2/3.
- **Custom XSS engine** — in-process, sends real HTTP requests (httpx)
  carrying canary-marked payloads (`<script>{canary}…</script>` and the rest
  of the corpus) to every parameterized URL the spider discovered, then
  inspects the real response bodies: a finding is recorded only when the
  full marked payload survives unescaped. No guessing, no heuristic claiming.

Mocking exists in exactly one place: the unit/integration test suite, where
`respx` fakes the scanner HTTP traffic so `pytest` needs no sidecars. The
shipped application contains none of it.

### See it for yourself

`make test-e2e` boots the full stack plus `vuln-lab` — a deliberately
vulnerable target with a raw `?q=` reflection and a fake MySQL error on
`/item?id='` — launches a real scan over HTTP, and asserts that real findings
come back: XSS hits on `?q=` with the actual reflected payload captured as
evidence, ZAP alerts normalized, ML severity assigned, SARIF rendered. During
this project's end-to-end verification that exact flow produced 51
ML-classified findings across 3 scans, visible in `/metrics`.

## Quick start (Docker)

```bash
cp .env.example .env          # set POSTGRES_PASSWORD, SECRET_KEY, ZAP_API_KEY
docker compose up --build     # db + migrate + zap + sqlmap + backend + frontend
```

| Endpoint | URL |
|---|---|
| Web UI | http://localhost:8090 |
| API (OpenAPI docs) | http://localhost:8000/docs |
| Prometheus metrics | http://localhost:8000/metrics |
| ZAP UI (dev) | http://localhost:8080 |

A default admin is seeded on first boot: `admin@scanner.local` /
`change-me-admin` — override via `ADMIN_EMAIL` / `ADMIN_PASSWORD`, and change
it before anything real. New self-registrations land as `viewer` (read-only).

### First scan

**UI:** log in → Dashboard → New scan → `https://your-test-app.example.com`,
profile `standard` → watch progress → findings table → row click for
evidence → download HTML / SARIF report.

**API:**

```bash
TOKEN=$(curl -s http://localhost:8000/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@scanner.local","password":"change-me-admin"}' \
  | python -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

curl -X POST http://localhost:8000/api/v1/scans \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"target_url":"https://your-test-app.example.com","profile":"standard"}'

curl http://localhost:8000/api/v1/scans/1/progress -H "Authorization: Bearer $TOKEN"
curl http://localhost:8000/api/v1/findings -H "Authorization: Bearer $TOKEN"
curl -o report.html http://localhost:8000/api/v1/reports/1/html -H "Authorization: Bearer $TOKEN"
```

### Scoping

Targets must pass three gates: a deny-list (localhost, link-local, cloud
metadata IPs), a private-range guard (private targets need
`ALLOW_PRIVATE_TARGETS=true` — default in the dev compose, with a DNS
rebinding re-check at request time), and — once the admin adds any — the
`/targets` allowlist (empty allowlist = bootstrap mode, everything allowed).
Admins manage it in the UI (**Targets**).

## Configuration

All variables live in `.env.example` with defaults + commentary. The ones
you'll actually touch:

| Variable | Default | Purpose |
|---|---|---|
| `SECRET_KEY` | dev value | JWT/API-key signing — **set a real one** |
| `POSTGRES_PASSWORD` | `secscanner` | DB password |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | see above | First-boot admin seed |
| `ZAP_API_KEY` | `dev-zap-key` | Shared secret with the ZAP sidecar |
| `ALLOW_PRIVATE_TARGETS` | `true` (dev compose) | Permit RFC1918 scan targets |
| `SCAN_TIMEOUT_MINUTES` / `MAX_CONCURRENT_SCANS` | `45` / `3` | Pipeline bounds |
| `SCAN_WEBHOOK_URL` | empty | `scan.completed` POST target |
| `EVIDENCE_RETENTION_DAYS` | `90` | Evidence purge window |

### Production deployment & TLS

The reference production stack is `docker compose -f docker-compose.yml -f
docker-compose.prod.yml up -d`. The prod override: runs the backend with
`ENV=production` (OpenAPI/docs disabled) and `ALLOW_PRIVATE_TARGETS=false`,
removes host ports from all data planes, adds restart policies + resource
caps, locks the ZAP API to the backend container, and terminates TLS at the
frontend nginx (`frontend/nginx-prod.conf`: HTTP→HTTPS redirect, TLS 1.2/1.3,
HSTS, the same security headers as dev).

Certificates are **never committed** (`deploy/certs/` is gitignored). Provide:

```
deploy/certs/fullchain.pem   # certificate + chain
deploy/certs/privkey.pem     # private key
```

then start the stack. Rotate by replacing the files and `docker compose
... restart frontend`. Put your real `SECRET_KEY`, DB password, admin
credentials, and `CORS_ORIGINS` in the environment — never in a commit.

## Day-to-day

```bash
make up / down     # compose lifecycle
make logs          # follow backend logs
make test          # backend suite: SQLite, scanners mocked, coverage-gated
make lint          # ruff (check + format) + eslint
make typecheck     # mypy (strict on app/services)
make dev           # hot-reload backend + infra sidecars via compose
make migrate       # alembic upgrade head (make revision m='...' to generate)
make test-e2e      # full stack vs. vulnerable lab target (slow, -m e2e)
```

### Tests

```bash
cd backend  && pytest --cov=app --cov-fail-under=70   # 141 tests, ~88% coverage
cd frontend && npm test                               # 31 vitest tests
cd frontend && npm run lint && npm run build          # eslint + production build
```

Backend tests run on SQLite (aiosqlite) with scanner HTTP mocked via respx —
no sidecars needed. `make test-e2e` boots the real stack against the
vuln-lab target instead.

## CI/CD

**CI** (`.github/workflows/ci.yml`) — on every PR / push to `master`, and
reused by CD as the deploy gate: ruff + mypy · pytest with a 70% coverage
gate · pip-audit on the pinned requirements · eslint + vitest + vite build ·
both Docker images built (GHA cache) · gitleaks secret scan.

**CD** (`.github/workflows/cd.yml`) — on `v*.*.*` tags, opt-in: set the
repository variable `DEPLOY_ENABLED=true` plus the environment secrets
(`STAGING_HOST/USER/SSH_KEY/URL`, `PROD_*`) to activate. Pipeline: full CI →
push backend/frontend images to GHCR (tag + sha) → SSH compose deploy to
staging → **ZAP baseline self-scan of staging** (dogfood gate: FAIL-level
rules per `tools/zap/policies/baseline-policy.xml` block the release; report
uploaded as an artifact) → production behind a GitHub-environment manual
approval. Rollback = re-deploy the previous immutable tag.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `migrate` container exits non-zero | Look at `docker compose logs migrate` — usually bad `POSTGRES_*` values or an unreachable `db`. The backend won't start until migrations succeed (by design). |
| Backend up but scans hang in `spider` | The ZAP daemon is still booting (30–60 s first time). A scan launched during that window fails with a `zap` phase error; the next one succeeds. |
| `target_not_allowed` on scan create | Scope guardrails fired: metadata/localhost IPs are always denied; RFC1918 targets need `ALLOW_PRIVATE_TARGETS=true`; and if any `/targets` allowlist entries exist, the host must match one. |
| `401` right after login | Token expired or the `SECRET_KEY` changed between requests (each backend replica signs with its own key — set one `SECRET_KEY` for all replicas). |
| Frontend loads but API calls fail | The SPA proxies `/api` to the `backend` service — check `docker compose logs backend` and that you're reaching the frontend port (8090 dev / 443 prod), not the backend directly from the browser. |
| ZAP self-scan in CD fails the release | A FAIL-level rule fired (e.g. the CSP header was lost) — the HTML report artifact (`zap-baseline-report`) names the rule; triage, fix, or pin the level in `tools/zap/policies/baseline-policy.xml`. |
| `e2e` tests skip locally | They only run with `WSS_E2E=1` and the compose stack up — use `make test-e2e`. |
| Lost admin password | Set `ADMIN_EMAIL`/`ADMIN_PASSWORD` and restart; seeding is idempotent only for *missing* admins — for a reset, delete the user row and restart. |

## Repository map

- `docs/` — [PROJECT-PLAN](docs/PROJECT-PLAN.md) · [ARCHITECTURE](docs/ARCHITECTURE.md) · [TECH-NOTES](docs/TECH-NOTES.md)
- `backend/app/api/v1/` — auth, users, targets, scans, findings, reports
- `backend/app/services/` — orchestrator, ZAP/SQLMap clients, XSS engine, ML classifier, webhooks
- `backend/app/services/payloads/` — XSS corpus (html_body / attribute / script / uri)
- `backend/ml/` — feature definition + model training (`python -m ml.train`)
- `backend/alembic/` — migrations (users, api_keys, scans, scan_targets, findings)
- `backend/tests/` — pytest suite (unit + API integration, scanners mocked)
- `frontend/src/` — auth store, router, views (Login/Dashboard/ScanDetail/Targets/AdminUsers), components
- `tools/` — ZAP baseline policy + sqlmap safe defaults (mounted into sidecars)
- `.github/workflows/` — ci.yml, cd.yml

## Status

Phase 1–3 implemented and verified end-to-end (API, UI, scanner pipeline,
ML model, CI/CD, docs) — per-file status in the
[implementation TODO list](docs/PROJECT-PLAN.md#12-implementation-todo-list).
