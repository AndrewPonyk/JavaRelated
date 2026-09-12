# E-Commerce Platform — Project Plan

> **Tech Stack:** Python 3.12 · Django · Celery · PostgreSQL · Redis · Elasticsearch · NumPy · PyTorch · React · TypeScript · TailwindCSS
> **Deployment:** AWS ECS · GitHub Actions · Terraform
> **Document owner:** Platform Architecture
> **Status:** Draft v1.0

---

## 1.1 Project File Structure

The repository is a **polyglot monorepo**: a Django backend, a React/TypeScript
frontend, infrastructure-as-code, and CI/CD definitions live side by side. This
keeps shared contracts (API schemas, types) reviewable in a single pull request
while still allowing each subsystem to build and deploy independently.

```text
1E-Commerce-Platform-Opus4-8/
│
├── docs/                              # Architecture & engineering docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── backend/                           # Django + Celery + ML services
│   ├── manage.py
│   ├── pyproject.toml                 # Ruff, Black, mypy, isort config
│   ├── pytest.ini                     # pytest + coverage config
│   ├── requirements/                  # Layered dependency pinning
│   │   ├── base.txt
│   │   ├── development.txt
│   │   └── production.txt
│   │
│   ├── config/                        # Django project (not a feature app)
│   │   ├── settings/                  # Split settings per environment
│   │   │   ├── base.py
│   │   │   ├── development.py
│   │   │   ├── staging.py
│   │   │   └── production.py
│   │   ├── urls.py                    # Root URL conf
│   │   ├── celery.py                  # Celery app + beat schedule
│   │   ├── asgi.py
│   │   └── wsgi.py
│   │
│   ├── apps/                          # Domain-bounded Django apps
│   │   ├── accounts/                  # AuthN/AuthZ, customer & vendor users
│   │   ├── catalog/                   # Products, categories, pricing
│   │   ├── cart/                      # Session & persistent carts
│   │   ├── orders/                    # Checkout, payments, fulfilment
│   │   ├── inventory/                 # Stock levels, reservations
│   │   ├── vendors/                   # Vendor onboarding & management
│   │   ├── search/                    # Elasticsearch indexing & queries
│   │   └── recommendations/           # PyTorch collaborative filtering
│   │       └── ml/                    # Model definition, training, inference
│   │
│   ├── common/                        # Cross-cutting backend utilities
│   │   ├── middleware/                # Request ID, audit, error envelope
│   │   └── utils/                     # Pagination, money, validators
│   │
│   └── tests/                         # pytest suites + shared fixtures
│       ├── conftest.py
│       ├── unit/
│       └── integration/
│
├── frontend/                          # React + TypeScript SPA
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   ├── .eslintrc.cjs
│   ├── public/
│   └── src/
│       ├── api/                       # Typed API client (fetch wrapper)
│       ├── components/                # Reusable UI (common/product/cart)
│       ├── pages/                     # Route-level views
│       ├── hooks/                     # Custom React hooks
│       ├── store/                     # Client state (Zustand/Redux)
│       ├── types/                     # Shared TS domain types
│       └── utils/                     # Formatters, guards
│
├── infrastructure/                    # Infrastructure as Code
│   ├── docker/                        # Dockerfiles & nginx config
│   └── terraform/
│       ├── modules/                   # Reusable: ecs, rds, elasticache, ...
│       └── environments/              # staging/, production/ tfvars + backend
│
├── .github/
│   └── workflows/                     # CI/CD pipelines
│       ├── ci.yml                     # Lint + test + build on every PR
│       ├── cd-staging.yml             # Deploy to staging on merge to main
│       └── cd-production.yml          # Gated deploy to production on tag
│
├── scripts/                           # Dev & ops helper scripts
├── docker-compose.yml                 # Local dev orchestration
├── .env.example                       # Template for required env vars
├── .gitignore
└── README.md
```

### Why this layout

| Decision | Rationale |
|----------|-----------|
| **Monorepo** | A single PR can change the API contract and its TS consumer together, preventing drift. One CI graph, one issue tracker. |
| **`apps/` by domain** | Each Django app is a bounded context (catalog, orders, inventory…). Encourages clear ownership and lets us extract a service later if needed. |
| **Split `settings/`** | Environment-specific config without `if DEBUG` spaghetti; secrets injected at runtime, never committed. |
| **Layered `requirements/`** | `production.txt` stays lean; heavy dev/test tooling (pytest, ruff, locust) never ships to the runtime image. |
| **`recommendations/ml/`** | Isolates PyTorch model code (training scripts, checkpoints, inference) from web request handling so the GPU/CPU training job can run as a separate Celery worker pool. |
| **`infrastructure/terraform/modules/`** | Reusable modules composed per environment; staging mirrors production at smaller scale. |

---

## 1.2 Implementation TODO List

### ✅ Phase 1 — Foundation (High Priority) — **COMPLETE**

- [x] Initialize Django project with split settings (`base/dev/staging/prod` + `test`)
- [x] Configure PostgreSQL connection + connection pooling (`CONN_MAX_AGE`, pgbouncer-ready)
- [x] Set up Redis as cache backend and Celery broker/result store
- [x] Wire Celery app + beat; add a health-check task (`/healthz` + `debug_task`)
- [x] Implement `accounts` app: custom user model, JWT auth, role/permission model (customer / vendor / staff)
- [x] Add `common` middleware: request-ID propagation, structured JSON logging, standard error envelope
- [x] Scaffold React app with Vite + TypeScript + Tailwind; configure ESLint/Prettier
- [x] Create typed API client and auth flow (login, refresh, logout w/ token blacklist)
- [x] Author `docker-compose.yml` (web, worker, worker-ml, beat, db, redis, elasticsearch, frontend) for local parity
- [x] Set up CI pipeline: lint → type-check → test → build
- [x] Bootstrap Terraform: ECS/RDS/ElastiCache modules + staging/production environments

### 🟡 Phase 2 — Core Features (Medium Priority) — **COMPLETE**

- [x] `catalog`: product/category models, admin, list & detail APIs, pricing, CRUD with vendor ownership
- [x] `inventory`: stock model, atomic reservation (`SELECT FOR UPDATE`), release on timeout (Celery beat)
- [x] `cart`: persistent carts, add/update/remove/clear, line-item validation against inventory
- [x] `orders`: checkout flow, order state machine, **idempotent** payment gateway abstraction (fake + Stripe), async confirmation emails via Celery
- [x] `vendors`: vendor onboarding, product ownership, payout-account field
- [x] `search`: Elasticsearch index mappings, post-save signals to keep ES in sync, faceted search API, autocomplete
- [x] `recommendations`: PyTorch matrix-factorization model, nightly training Celery task, inference endpoint with Redis-cached results + cold-start fallback
- [x] Frontend: product browse/search, product detail, cart, checkout, order history, vendor dashboard, auth
- [x] Integration tests across cart → inventory → order happy/edge paths
- [ ] Deploy staging environment end-to-end via CD pipeline *(pipeline authored; needs a live AWS account)*

### 🟢 Phase 3 — Polish & Optimization (Lower Priority)

- [x] Read-only reporting queries via SQLAlchemy against the replica (`common/utils/reporting.py`)
- [x] Cache hot catalog responses; cache invalidation on writes
- [ ] Rate limiting + WAF rules; API throttling per user/IP
- [ ] Observability: OpenTelemetry traces, Prometheus metrics, Grafana dashboards, Sentry error tracking *(Sentry hook stubbed in prod settings)*
- [ ] Load testing with Locust; tune Gunicorn/uvicorn worker counts and DB pool
- [ ] Blue/green or canary deploys on ECS; automated rollback on health-check failure *(CodeDeploy path scripted)*
- [ ] Recommendation model A/B testing harness and offline evaluation metrics
- [ ] Accessibility (WCAG 2.1 AA) and Lighthouse performance budget enforcement in CI
- [ ] Cost optimization: spot capacity for training workers, S3 lifecycle policies
