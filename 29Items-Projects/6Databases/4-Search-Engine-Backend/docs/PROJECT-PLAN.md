# Search Engine Backend — Project Plan

Product search platform: **FastAPI + Elasticsearch** (custom analyzers, synonyms, faceted
filtering, Learning-to-Rank re-ranking), **PostgreSQL** as the catalog source of truth,
**Redis** for autocomplete-suggestion caching, and a **Vue 3** storefront search UI.
Deployed to **Google Cloud** (Cloud Run + Cloud SQL + Memorystore + Elasticsearch on GCP),
CI/CD via **GitHub Actions**.

Companion documents: [ARCHITECTURE.md](./ARCHITECTURE.md) · [TECH-NOTES.md](./TECH-NOTES.md)

---

## 0. Assumptions & Interpretation of the Brief

| Brief item | Interpretation |
|---|---|
| "Hedia" in the tech stack | Read as **Redis** — the description explicitly says *"Redis for autocomplete suggestions caching"*. |
| "Google Cloud Elasticsearch" | Either **Elastic Cloud (hosted on GCP)** or **self-managed ES on GKE**. Important nuance: the community **Learning-to-Rank plugin cannot be installed on Elastic Cloud**; there you use the native LTR rescorer (ES ≥ 8.12). Both modes are supported via the `LTR_MODE` setting (`plugin` \| `native` \| `off`) — see `backend/app/search/ltr.py` and TECH-NOTES §3.6. |
| Authentication | Storefront search/suggest endpoints are public and rate-limited; catalog-write and admin endpoints are protected (static admin API key in the scaffold, JWT/IAP as a Phase-2 task). |
| Frontend scope | A search page (search bar with autocomplete, facet panel, results grid, pagination) — not a full storefront. |

---

## 1.1 Project File Structure

```
4-Search-Engine-Backend/
├── Claude-Fable-5.txt                  # model marker file (per brief)
├── README.md                           # quickstart + repo map
├── .gitignore / .editorconfig
├── .env.example                        # canonical env template (see TECH-NOTES §3.4)
├── .pre-commit-config.yaml             # ruff, format, yaml/json checks, prettier
├── Makefile                            # dev entrypoints: up, lint, test, seed, reindex…
├── docker-compose.yml                  # local stack: ES + PostgreSQL + Redis + API (+ web)
│
├── docs/                               # ── architecture & planning ──
│   ├── PROJECT-PLAN.md                 # this file
│   ├── ARCHITECTURE.md                 # pattern, components, data flow, security
│   └── TECH-NOTES.md                   # CI/CD, testing, deployment, env mgmt, pitfalls
│
├── .github/                            # ── CI/CD (GitHub Actions) ──
│   ├── workflows/
│   │   ├── ci.yml                      # lint → typecheck → unit → integration → build
│   │   ├── deploy-staging.yml          # main → staging (auto)
│   │   └── deploy-production.yml       # release tag → prod (gated environment)
│   └── PULL_REQUEST_TEMPLATE.md
│
├── backend/                            # ── FastAPI service (Python 3.12) ──
│   ├── pyproject.toml                  # deps + ruff/mypy/pytest/coverage config
│   ├── Dockerfile                      # slim multi-stage, non-root, Cloud Run ready
│   ├── .dockerignore
│   ├── alembic.ini
│   ├── app/
│   │   ├── main.py                     # app factory, lifespan (ES/Redis), middleware
│   │   ├── core/
│   │   │   ├── config.py               # pydantic-settings (env-driven)
│   │   │   ├── logging.py              # structlog JSON logs + request-id contextvar
│   │   │   └── exceptions.py           # AppError hierarchy + RFC-7807 handlers
│   │   ├── api/
│   │   │   ├── deps.py                 # DI providers (db/es/redis/services/auth)
│   │   │   └── v1/
│   │   │       ├── router.py           # aggregates v1 endpoints
│   │   │       └── endpoints/
│   │   │           ├── search.py       # GET /api/v1/search  (facets, sort, paging)
│   │   │           ├── suggest.py      # GET /api/v1/suggest (Redis-cached)
│   │   │           ├── products.py     # catalog CRUD (writes go to PG, then ES)
│   │   │           ├── admin.py        # POST /api/v1/admin/reindex (API-key)
│   │   │           └── health.py       # /healthz, /readyz (unversioned)
│   │   ├── schemas/                    # Pydantic DTOs (common, search, suggest, product)
│   │   ├── services/                   # business logic layer
│   │   │   ├── search_service.py       # orchestrates query build → ES → DTO mapping
│   │   │   ├── suggest_service.py      # cache-aside autocomplete
│   │   │   ├── product_service.py      # catalog use-cases (PG + index sync)
│   │   │   └── indexing_service.py     # product → ES document, bulk/reindex
│   │   ├── search/                     # Elasticsearch gateway (no business logic)
│   │   │   ├── es_client.py            # AsyncElasticsearch factory
│   │   │   ├── query_builder.py        # typed request → query DSL (facets, sort, LTR)
│   │   │   ├── facets.py               # facet registry: aggs build + response parse
│   │   │   └── ltr.py                  # sltr (plugin) / learning_to_rank (native) rescore
│   │   ├── db/
│   │   │   ├── base.py                 # Declarative Base + naming conventions
│   │   │   ├── session.py              # async engine / session factory
│   │   │   └── models/                 # Product, Category, SearchEvent (LTR signals)
│   │   ├── repositories/               # SQLAlchemy data access (product_repository.py)
│   │   └── cache/redis_client.py       # redis.asyncio factory
│   ├── migrations/                     # Alembic (async env.py) + versions/0001_…
│   └── tests/
│       ├── conftest.py                 # app fixture with DI overrides (mock ES/Redis)
│       ├── unit/                       # query builder, facets, ltr, outbox, limiter…
│       └── integration/                # API contract tests + gated full-stack suite
│
├── elasticsearch/                      # ── search assets (versioned with the code) ──
│   ├── indexes/products.index.json     # settings (custom analyzers) + strict mappings
│   ├── synonyms/synonyms_en.txt        # synonym source → uploaded as a synonyms set
│   └── ltr/featureset.json             # LTR feature definitions (+ README)
│
├── ml/                                 # ── Learning-to-Rank training loop ──
│   └── ltr/
│       ├── build_judgments.py          # search_events → graded judgment list
│       ├── train.py                    # XGBoost ranker training
│       └── upload_model.py             # push featureset + model to ES
│
├── scripts/                            # operational one-shots (idempotent)
│   ├── create_indexes.py               # synonyms set + index vN + alias bootstrap
│   ├── reindex.py                      # zero-downtime reindex with alias swap
│   └── seed_products.py                # local demo data (PG + ES)
│
├── frontend/                           # ── Vue 3 + TypeScript + Vite + Pinia ──
│   ├── package.json / vite.config.ts / tsconfig.json / index.html
│   ├── eslint.config.js / .prettierrc.json / .env.example
│   ├── Dockerfile / nginx.conf         # static build behind nginx (optional path)
│   └── src/
│       ├── main.ts / App.vue
│       ├── api/client.ts               # fetch wrapper: errors, params, AbortSignal
│       ├── api/search.ts               # /search + /suggest calls
│       ├── types/search.ts             # DTOs mirroring the API contract
│       ├── stores/search.ts            # Pinia store (state machine for search page)
│       ├── composables/useDebounceFn.ts
│       ├── views/SearchView.vue
│       └── components/                 # SearchBar, FacetPanel, SearchResults, ProductCard
│
└── infrastructure/
    ├── docker/elasticsearch/Dockerfile # local ES image (+ LTR plugin install hook)
    └── terraform/                      # GCP: Cloud Run, Cloud SQL, Memorystore, ES
        ├── main.tf / variables.tf / outputs.tf
        └── environments/{dev,staging,prod}.tfvars
```

### Tooling summary

| Concern | Tool | Config lives in |
|---|---|---|
| Python lint/format | ruff (lint + formatter) | `backend/pyproject.toml` |
| Python types | mypy | `backend/pyproject.toml` |
| Python tests | pytest + pytest-asyncio + httpx | `backend/pyproject.toml` |
| DB migrations | Alembic (async) | `backend/alembic.ini`, `backend/migrations/` |
| JS lint/format | ESLint 9 (flat) + Prettier | `frontend/eslint.config.js`, `.prettierrc.json` |
| JS tests | Vitest + Vue Test Utils | `frontend/vite.config.ts` |
| Git hooks | pre-commit | `.pre-commit-config.yaml` |
| Local stack | Docker Compose | `docker-compose.yml` |
| CI/CD | GitHub Actions | `.github/workflows/*` |
| IaC | Terraform (google + ec providers) | `infrastructure/terraform/` |

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority) — ✅ COMPLETE

- [x] Repository scaffold: structure, docs, configs, stubs
- [x] `docker compose up` green: ES 8.x, PostgreSQL 16, Redis 7, API healthchecks pass
- [x] Alembic migrations applied (`0001` core schema, `0002` outbox + impressions)
- [x] `scripts/create_indexes.py`: synonyms set + versioned index + `products` alias (idempotent)
- [x] Product CRUD end-to-end: PG write → transactional outbox → ES document indexed
- [x] `GET /api/v1/search`: multi-field match, term/range filters, facet aggregations, paging, sort
- [x] `GET /api/v1/suggest`: completion suggester + Redis cache-aside (TTL + jitter)
- [x] Error contract: RFC-7807 responses, structured JSON logs with `request_id`
- [x] CI pipeline: ruff + mypy + pytest (70% coverage gate) + eslint + vue-tsc + vitest + image builds
- [x] Frontend search page wired to the API (search, facets, pagination, loading/error states)
- [x] Seed script with representative demo catalog (1,000 products, deterministic)

### Phase 2 — Core features (medium priority) — ✅ COMPLETE (except noted)

- [x] Synonyms lifecycle: `synonyms_en.txt` → synonyms set API (hot-reloadable, deploy step)
- [x] Disjunctive (multi-select) facet counts via `post_filter` + per-facet filtered aggs
- [x] Search-event logging (impressions with shown ids, clicks with position) — async, PII-free
- [x] LTR pipeline v1: `build_judgments.py` (COEC debias) → `train.py` (XGBoost, baseline gate) → `upload_model.py` (plugin + native)
- [x] LTR rescore behind `LTR_MODE` flag; automatic BM25 fallback when the rescore fails
- [x] Zero-downtime full reindex (`scripts/reindex.py`): bulk from PG, count check, atomic alias swap, catch-up
- [x] Outbox table + worker for PG→ES sync (in-process loop; SKIP LOCKED, dead-lettering)
- [x] Integration tests on real containers: analyzers/synonyms, suggester, outbox, CRUD (`RUN_INTEGRATION=1`)
- [ ] Relevance regression suite: golden query set + nDCG@10 gate in CI *(needs real click data first)*
- [x] Admin/write endpoints protected (API key); IAM/IAP is the cloud follow-up
- [x] Terraform: Cloud Run service + migrate job, Cloud SQL, Memorystore, Elastic Cloud, Secret Manager, web bucket
- [x] Staging auto-deploy from `main` + smoke tests

### Phase 3 — Polish & optimization (lower priority) — partially done, rest is backlog

- [x] Rate limiting on `/search` + `/suggest` + `/events` (in-process sliding window; Cloud Armor at the edge in prod)
- [x] Response compression (GZip on API payloads ≥ 1 KB; nginx gzip for static assets)
- [x] Fail-fast config validation: placeholder/missing secrets refuse to boot outside dev
- [x] Performance indexes: products (is_active, created_at); partial index on clicked_product_id
- [x] Frontend keyboard navigation + ARIA combobox for autocomplete; responsive layout
- [x] Production canary deploy (Cloud Run traffic split 10% → 100% with error-budget check)
- [ ] `search_after`-based deep pagination (the 10k window is capped and enforced today)
- [ ] Highlighting, spell-correction ("did you mean") via term suggester
- [ ] Personalized / contextual boosting (category affinity as LTR features)
- [ ] Popularity signal pipeline: nightly job recomputes `popularity` rank_feature from events
- [ ] Observability: OpenTelemetry traces (API ↔ ES ↔ PG), RED dashboards, alerting SLOs
- [ ] URL-synced search state (shareable searches)
- [ ] E2E happy-path suite (Playwright) in nightly pipeline
- [ ] Cost pass: ES sizing/ILM, Redis eviction policy, Cloud Run min-instances tuning

---

## 2. Milestones & Definition of Done

| Milestone | Contents | DoD |
|---|---|---|
| **M1 — Walking skeleton** | Phase 1 complete | Demo: type query → results + facets from local stack; CI green |
| **M2 — Relevant search** | Synonyms live, facets multi-select, events logged | Relevance suite ≥ baseline nDCG; staging deployed |
| **M3 — Learned ranking** | LTR trained + serving behind flag | A/B: LTR ≥ BM25 on CTR; rollback path tested |
| **M4 — Production** | Phase 3 hardening | SLOs: p95 search < 300 ms, suggest < 80 ms; alerting live |

## 3. Top Risks

| Risk | Mitigation |
|---|---|
| LTR **plugin** unavailable on Elastic Cloud | `LTR_MODE=native` (ES ≥ 8.12) or self-managed ES on GKE; abstraction in `app/search/ltr.py` |
| PG ↔ ES drift (missed index updates) | Outbox pattern (Phase 2), nightly reconciliation count-check, full reindex runbook |
| Mapping changes require reindex | Versioned indexes (`products_vN`) + alias swap from day one |
| Sparse click data → weak LTR model | Keep BM25 fallback; train only when judgment volume threshold met; interleaving tests |
| Autocomplete latency under load | Redis cache-aside with jittered TTL; completion suggester (in-memory FST) as the miss path |
