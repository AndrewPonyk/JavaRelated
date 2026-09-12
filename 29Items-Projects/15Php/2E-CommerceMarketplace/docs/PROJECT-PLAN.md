# E-Commerce Marketplace — Project Plan

> Multi-vendor marketplace. Symfony 7 modular monolith with DDD bounded contexts,
> event sourcing for order history, Elasticsearch product search, ML fraud
> detection, React/TypeScript frontend. Deployed to Azure Container Apps.

---

## 1.1 Project File Structure

The repository is a **polyglot monorepo**. Three deployable units (`backend`,
`frontend`, `ml-service`) plus infrastructure and docs. The backend is a
**modular monolith**: one Symfony app, but `src/` is split by **bounded context**,
each with its own `Domain / Application / Infrastructure / UI` layers (hexagonal).

```
2E-CommerceMarketplace/
│
├── docs/                              # Architecture & planning docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── backend/                           # Symfony 7 modular monolith (DDD)
│   ├── bin/console                    # Symfony CLI entrypoint
│   ├── public/index.php               # HTTP front controller
│   ├── config/
│   │   ├── bundles.php
│   │   ├── services.yaml              # DI: autowire + per-context bindings
│   │   ├── routes.yaml
│   │   └── packages/
│   │       ├── doctrine.yaml          # ORM + DBAL, per-context entity mappings
│   │       ├── messenger.yaml         # command/event buses + async transport
│   │       ├── security.yaml          # JWT auth, role hierarchy
│   │       ├── framework.yaml
│   │       └── elasticsearch.yaml     # ES client config (custom bundle)
│   │
│   ├── src/
│   │   ├── Kernel.php
│   │   │
│   │   ├── Shared/                    # Shared Kernel (cross-context primitives)
│   │   │   ├── Domain/                #   Money, AggregateRoot, DomainEvent, buses
│   │   │   ├── Application/
│   │   │   └── Infrastructure/        #   Messenger bus adapters, Doctrine base types
│   │   │
│   │   ├── Catalog/                   # BC: products & categories  (CRUD showcase)
│   │   │   ├── Domain/                #   Product aggregate, repo interface, events
│   │   │   ├── Application/           #   Commands/Queries + handlers, DTOs
│   │   │   ├── Infrastructure/        #   Doctrine repository, ORM mapping
│   │   │   └── UI/Http/               #   ProductController (REST)
│   │   │
│   │   ├── Ordering/                  # BC: orders  (EVENT-SOURCED)
│   │   │   ├── Domain/                #   Order aggregate rebuilt from events
│   │   │   ├── Application/
│   │   │   ├── Infrastructure/        #   Doctrine-backed event store
│   │   │   └── UI/Http/
│   │   │
│   │   ├── Search/                    # BC: product search (ES read model / CQRS)
│   │   │   ├── Application/           #   Search queries + projection handlers
│   │   │   ├── Infrastructure/        #   Elasticsearch adapters & indexer
│   │   │   └── UI/Http/
│   │   │
│   │   ├── Vendor/                    # BC: sellers, dashboards, commission
│   │   │   ├── Domain/                #   Seller, CommissionRate, payout ledger
│   │   │   ├── Application/           #   Dashboard read models
│   │   │   └── UI/Http/
│   │   │
│   │   ├── Payment/                   # BC: transactions & settlement
│   │   │   ├── Domain/
│   │   │   └── Application/
│   │   │
│   │   ├── FraudDetection/            # BC: ML risk scoring (anti-corruption layer)
│   │   │   ├── Domain/                #   FraudAssessment, RiskScore VO
│   │   │   ├── Application/           #   AssessTransactionRisk handler (on event)
│   │   │   └── Infrastructure/        #   HTTP client to ml-service (ACL)
│   │   │
│   │   └── Identity/                  # BC: users, auth, roles
│   │       ├── Domain/
│   │       └── Infrastructure/Security/
│   │
│   ├── migrations/                    # Doctrine migrations (write model + event store)
│   ├── tests/
│   │   ├── Unit/                      # Pure domain logic, no I/O
│   │   ├── Integration/               # Repositories, ES, messaging against real infra
│   │   └── Functional/                # HTTP API through the kernel
│   ├── composer.json
│   ├── phpunit.xml.dist
│   ├── phpstan.neon                   # level 9 static analysis
│   ├── .php-cs-fixer.dist.php         # @Symfony + @PHP83Migration rules
│   └── Dockerfile                     # multi-stage: composer → php-fpm + nginx
│
├── frontend/                          # React 18 + TypeScript + Vite
│   ├── index.html
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── api/                       # typed fetch client + endpoints
│   │   ├── components/                # presentational components
│   │   ├── hooks/                     # data-fetching hooks (React Query style)
│   │   ├── types/                     # shared TS domain types
│   │   └── features/
│   │       ├── catalog/               # product browsing/search UI
│   │       └── seller-dashboard/      # vendor analytics & commission UI
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── .eslintrc.cjs
│   ├── .prettierrc
│   └── Dockerfile                     # build → static assets served by nginx
│
├── ml-service/                        # Python fraud-scoring micro-service
│   ├── src/
│   │   ├── main.py                    # FastAPI app exposing /score
│   │   └── model.py                   # model load + feature pipeline
│   ├── tests/
│   ├── requirements.txt
│   └── Dockerfile
│
├── infra/
│   ├── azure/
│   │   ├── main.bicep                 # resource group composition
│   │   └── container-apps.bicep       # Container Apps env + 3 apps + scaling
│   └── docker/                        # shared base images / nginx conf
│
├── .github/workflows/
│   ├── backend-ci.yml                 # lint → static analysis → test → image
│   ├── frontend-ci.yml                # lint → typecheck → test → build
│   └── deploy.yml                     # build & push images → az containerapp update
│
├── docker-compose.yml                 # local infra parity
├── Makefile                           # task runner
├── .env.example                       # documented env template
├── .gitignore
└── README.md
```

### Why this layout

- **Bounded contexts as top-level folders** make the module boundaries explicit
  and enforceable (e.g. `deptrac`/`phpat` can fail the build if `Catalog` reaches
  into `Ordering` internals). Each context can later be extracted into its own
  service with minimal churn.
- **Hexagonal layers per context** keep the domain free of framework/I/O concerns
  — `Domain` has zero Symfony/Doctrine imports; adapters live in `Infrastructure`.
- **Shared Kernel** holds only stable, universally-agreed primitives (Money,
  AggregateRoot, the bus interfaces) to avoid a god "common" dump.

---

## 1.2 Implementation TODO List

### ✅ Phase 0 — Scaffolding (this deliverable)
- [x] Monorepo skeleton, bounded-context folders, hexagonal layers
- [x] Local infra via docker-compose (PG, RabbitMQ, ES, ml-service)
- [x] CI/CD workflow skeletons, Bicep infra, env template
- [x] Reference stubs: Catalog CRUD, Ordering event sourcing, fraud ACL, React feature

### ✅ Phase 1 — Foundation (HIGH priority)
- [x] Symfony app boots; health-check endpoint `/health` (liveness + readiness)
- [x] Doctrine connection + first migration; CI runs migrations on ephemeral PG
- [x] **Shared Kernel**: `Money`, `AggregateRoot`, `DomainEvent`, command/query/event bus over Symfony Messenger
- [x] **Identity**: user registration/login, JWT issuance, role hierarchy (`CUSTOMER`, `SELLER`, `ADMIN`)
- [x] **Catalog**: Product aggregate, create/update/list, Doctrine repository, REST API + validation
- [x] CI green: php-cs-fixer, PHPStan L9, PHPUnit; frontend lint+typecheck+test (pipelines wired)
- [x] Frontend shell: routing, typed API client, auth context, product list + auth pages
- [ ] Containerised images build & deploy to a `dev` Container Apps environment (Dockerfiles + workflow ready; deploy not exercised)

### ✅ Phase 2 — Core Features (MEDIUM priority)
- [x] **Ordering (event sourcing)**: Order aggregate from events, Doctrine event store, optimistic concurrency (snapshotting → Phase 3)
- [x] **Search (CQRS)**: project `ProductCreated/PriceChanged` → Elasticsearch; full-text search API + UI; create-index & reindex commands
- [x] **Vendor**: event-driven seller onboarding, dashboard read model, **commission tracking** (per-order ledger, per-seller rate)
- [x] **Payment**: capture flow, idempotency keys, transaction records, fraud-hold gating (refund → Phase 3)
- [x] **FraudDetection**: on `OrderPlaced`, async risk scoring via ml-service; block/flag/allow + persisted assessments + manual review queue
- [x] **ml-service**: feature pipeline, model versioning, `/score` + `/health`, heuristic fallback when model unavailable
- [x] Async pipeline hardened: retries (backoff), dead-letter transport, idempotent consumers
- [x] Seller dashboard UI: commission owed, payout history (ledger)
- [ ] staging environment + smoke tests in deploy pipeline (deploy workflow ready; not exercised)

### ☐ Phase 3 — Polish & Optimization (LOWER priority)
- [ ] Event-store snapshotting + projection rebuild tooling/CLI
- [ ] Read-model caching, ES query tuning, N+1 audit, DB indexing pass
- [ ] Observability: OpenTelemetry traces, structured logs, dashboards, alerts, SLOs
- [ ] Fraud model retraining loop + feedback labels from review outcomes
- [ ] Rate limiting, WAF rules, security headers, dependency/CVE scanning in CI
- [ ] Blue/green or canary rollout via Container Apps revisions
- [ ] Load & resilience testing (k6); autoscaling rule tuning
- [ ] Accessibility (a11y) + i18n pass on the SPA
- [ ] ADRs for key decisions; runbooks for on-call

---

## Milestone view

| Milestone | Contexts live | Exit criteria |
|-----------|---------------|---------------|
| **M1 Walking skeleton** | Identity, Catalog | Login + browse products in `dev`, CI/CD green end-to-end |
| **M2 Transact** | Ordering, Payment, Search | Place an order, pay, search products; order history reconstructed from events |
| **M3 Marketplace** | Vendor, FraudDetection | Sellers onboard & see commissions; suspicious transactions auto-flagged |
| **M4 Hardened** | — | Observability, autoscaling, security gates, snapshot/rebuild tooling |
