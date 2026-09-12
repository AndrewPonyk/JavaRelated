# Web App Security Scanner — Project Plan

> Automated DAST platform: OWASP ZAP–driven scanning, SQLMap SQL-injection testing,
> custom XSS payload engine, and ML-based vulnerability severity classification.
> FastAPI + PostgreSQL backend, Vue 3 frontend, Docker + GitHub Actions delivery.

---

## 1.1 Project File Structure

```text
3-Web-App-Security-Scanner/
├── docs/                                # Architecture & technical documentation
│   ├── PROJECT-PLAN.md                  # This file
│   ├── ARCHITECTURE.md                  # System architecture & diagrams
│   └── TECH-NOTES.md                    # CI/CD, testing, deployment know-how
│
├── backend/                             # FastAPI application (Python 3.12)
│   ├── app/
│   │   ├── main.py                      # App factory, lifespan (DB seed, retention), middleware wiring
│   │   ├── core/
│   │   │   ├── config.py                # Pydantic settings (env-driven)
│   │   │   ├── logging.py               # Structured JSON logging + secret redaction
│   │   │   ├── security.py              # JWT (HS256) + API keys (wss_) + PBKDF2 + RBAC ranks
│   │   │   ├── errors.py                # Error envelope {"error": {code, message}} handlers
│   │   │   ├── middleware.py            # Request logging / timing middleware
│   │   │   ├── ratelimit.py             # Per-principal sliding-window limiter
│   │   │   └── metrics.py               # Prometheus text-format registry + timers
│   │   ├── api/
│   │   │   ├── deps.py                  # Shared deps: db session, current principal, role guards
│   │   │   └── v1/
│   │   │       ├── router.py            # v1 aggregate router
│   │   │       ├── auth.py              # register/login/refresh/me + API key lifecycle
│   │   │       ├── users.py             # Admin user management (role, active)
│   │   │       ├── targets.py           # ScanTarget allowlist CRUD + verify
│   │   │       ├── scans.py             # Scan create/list/detail/progress/cancel/counts
│   │   │       ├── findings.py          # Filtered/paginated queries + stats + detail
│   │   │       └── reports.py           # HTML (Jinja2) + SARIF 2.1.0 exports
│   │   ├── models/                      # SQLAlchemy ORM models
│   │   │   ├── user.py                  # users
│   │   │   ├── api_key.py               # api_keys (hashed, prefixed, expiring)
│   │   │   ├── scan.py                  # scans + scan_targets allowlist
│   │   │   └── finding.py               # findings (JSONB evidence/raw, dedup hash)
│   │   ├── schemas/                     # Pydantic request/response schemas
│   │   │   ├── auth.py, scan.py, finding.py, target.py
│   │   ├── services/                    # Business logic (framework-free where possible)
│   │   │   ├── scan_orchestrator.py     # ZAP → SQLMap → XSS pipeline + phase-failure model
│   │   │   ├── zap_client.py            # OWASP ZAP REST API client (httpx)
│   │   │   ├── sqlmap_client.py         # sqlmapapi REST client (Basic auth, GET lifecycle)
│   │   │   ├── xss_scanner.py           # Canary XSS engine (corpus + block-aware contexts)
│   │   │   ├── payloads/                # XSS corpus: html_body/attribute/script/uri .txt
│   │   │   ├── ml_features.py           # 24 shared features (services + training)
│   │   │   ├── ml_classifier.py         # Calibrated logreg w/ rules fallback
│   │   │   ├── webhooks.py              # scan.completed webhook (Slack-compatible)
│   │   │   └── retention.py             # Evidence retention purge job
│   │   └── db/
│   │       ├── session.py               # Async engine / session factory
│   │       ├── base.py                  # Declarative base, naming conventions, utcnow
│   │       └── seed.py                  # First-boot admin seed
│   ├── ml/
│   │   ├── features.py                  # FEATURE_NAMES (24) — single source of truth
│   │   └── train.py                     # Synthetic labeled corpus → severity_model.joblib
│   ├── alembic/versions/
│   │   ├── 0001_initial_schema.py       # users, scans, findings
│   │   └── 0002_api_keys_targets.py     # api_keys, scan_targets, users seed
│   ├── tests/                           # 141 tests + e2e (SQLite + respx, no sidecars)
│   ├── Dockerfile                       # Multi-stage: deps → train ML → runtime (non-root)
│   ├── requirements.txt / requirements-dev.txt / alembic.ini / pyproject.toml
│
├── frontend/                            # Vue 3 + Vite SPA (vue-router; no state lib by design)
│   ├── src/
│   │   ├── main.js                      # Bootstrap: wire auth failure → router, session restore
│   │   ├── App.vue                      # Shell + nav (role-gated) + router outlet
│   │   ├── api/client.js                # Fetch wrapper: tokens, silent refresh, retry, downloads
│   │   ├── stores/auth.js               # Reactive auth store (module-scope singleton)
│   │   ├── router/index.js              # Routes + auth/role guards
│   │   ├── components/
│   │   │   ├── ScanForm.vue             # Launch scan (validation/loading/error states)
│   │   │   ├── FindingsTable.vue        # Filters, pagination, row → detail modal
│   │   │   ├── FindingDetailModal.vue   # Full finding + evidence (fetched on open)
│   │   │   └── SeverityBadge.vue        # Color-coded severity chip
│   │   └── views/
│   │       ├── LoginView.vue            # Login/register with client validation
│   │       ├── DashboardView.vue        # Launch + latest scan polling + severity stats
│   │       ├── ScanDetailView.vue       # Progress, failures, findings, report downloads
│   │       ├── TargetsView.vue          # Admin allowlist management
│   │       └── AdminUsersView.vue       # Admin user role/active management
│   ├── tests/                           # 31 vitest tests (client, store, router, components)
│   ├── Dockerfile                       # Multi-stage: node build → nginx serve
│   ├── nginx.conf                       # SPA fallback + /api proxy + security headers
│   ├── eslint.config.js / vite.config.js / package.json / index.html
│
├── tools/                               # Scanner tooling configuration
│   ├── zap/policies/baseline-policy.xml # zap-baseline -c rules (CD self-scan gate)
│   ├── sqlmap/                          # sqlmapapi image + safe defaults
│   └── vuln-lab/                        # Deliberately vulnerable e2e target (e2e profile)
│
├── .github/workflows/
│   ├── ci.yml                           # ruff+mypy · pytest(70% gate)+pip-audit · eslint+vitest+build · images · gitleaks
│   └── cd.yml                           # CI gate → GHCR push → staging + ZAP self-scan → prod (opt-in)
│
├── docker-compose.yml                   # Local stack: db, migrate, zap, sqlmap, backend, frontend (+e2e profile)
├── docker-compose.prod.yml              # Production overrides (replicas, TLS, no ports)
├── .env.example                         # Documented env template
├── .pre-commit-config.yaml              # ruff, eslint, secret-scan hooks
├── .gitignore / .dockerignore
├── Makefile                             # dev, test, lint, typecheck, up, down, migrate, test-e2e
├── README.md
└── glm.txt
```

### Structure rationale

| Concern | Decision |
|---|---|
| **Backend layering** | `api → services → models` — routers stay thin, all scanner/tool logic lives in `services/`, making ZAP/SQLMap/XSS engines independently testable and replaceable. |
| **Scanner isolation** | ZAP and SQLMap run as their own containers (sidecars), driven over their REST APIs — no subprocess spawning inside the API process, no tool dependencies in the backend image. |
| **Migrations** | Alembic versioned next to the app it migrates; one migration per logical change, reviewable in PRs. |
| **Frontend** | Standard Vite layout; `api/client.js` is the single place that knows about auth headers and error shapes. |
| **CI/CD** | `.github/workflows/` split ci (fast feedback) / cd (deploy); tool configs (`tools/`) are mounted into scanner containers, not baked in, so policy tuning doesn't require image rebuilds. |

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority)
- [x] Scaffold repository structure (this plan, architecture & tech notes)
- [x] FastAPI app factory with `/health` + settings via pydantic-settings
- [x] SQLAlchemy async models + Alembic initial migration (users, scans, findings, evidence)
- [x] Docker Compose stack: postgres, zap, sqlmap, backend, frontend
- [x] CI workflow: ruff + mypy + pytest + frontend build
- [x] JWT auth skeleton (`core/security.py`) + `/api/v1/auth` stubs
- [x] ZAP REST client: start spider → active scan → poll progress → fetch alerts
- [x] Scan orchestrator state machine (PENDING → RUNNING → COMPLETED/FAILED/CANCELLED)
- [x] Scan CRUD endpoints wired to orchestrator (create/start/stop/status/list)
- [x] Finding ingestion: normalize ZAP alerts + SQLMap findings into one schema

### Phase 2 — Core features (medium priority)
- [x] SQLMap REST client (create task → set options → run → parse log)
- [x] Custom XSS engine: payload corpus loader, reflection detection, context-aware encoding
- [x] ML severity classifier: feature extraction + first model (logistic regression baseline)
- [x] ML training job: labeled synthetic corpus → model artifact → baked at image build
      (re-run `python -m ml.train` against an NVD-derived corpus to retrain)
- [x] Findings API: filtering (severity, OWASP category), pagination, dedup hashing
- [x] Vue dashboard: scan launch form, live progress polling, findings table
- [x] Report exports: HTML summary + SARIF for CI integration
- [x] Alerting webhook on High/Critical findings (Slack-compatible)
- [x] CD workflow: image build/push + staging deploy on tag

### Phase 3 — Polish & optimization (lower priority)
- [ ] Celery/Redis task queue for long scans (orchestrator logic is framework-free
      below the dispatch line — lifts into Celery without rewrites; asyncio tasks
      are fine at current scale)
- [ ] Scan scheduling (cron-style recurring scans of registered targets)
- [ ] OWASP Top 10 2021 mapping coverage report per target (per-finding mapping ships)
- [x] Model upgrade: calibration shipped (`CalibratedClassifierCV`); offline eval
      harness in `ml/train.py`; gradient-boosted swap is a config change
- [x] Rate limiting + per-target concurrency caps (sliding-window limiter +
      `MAX_CONCURRENT_SCANS` platform semaphore)
- [ ] Multi-tenant hardening: scans/findings are team-visible by design;
      per-user isolation + audit log not yet
- [x] Metrics endpoint (Prometheus): scans by status, findings by severity, phase latencies
- [x] E2E tests against a deliberately vulnerable app (`tools/vuln-lab`,
      `make test-e2e` — real ZAP + XSS pipeline over HTTP)
- [ ] Helm chart / K8s manifests as deployment alternative to compose

### Definition of Done (per feature)
1. Unit/integration tests green in CI; 2. `.env.example` and docs updated;
3. `docker compose up` works end-to-end; 4. No High findings from self-scan (dogfood).
