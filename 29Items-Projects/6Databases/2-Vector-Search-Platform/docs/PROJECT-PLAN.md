# Vector Search Platform — Project Plan

> Comparative vector database platform for enterprise semantic document retrieval.
> Backends: **pgvector**, **Pinecone**, **Weaviate**, **Milvus**. Embeddings via **Sentence Transformers**.
> Hybrid (keyword + vector) search, plus **recall@k** benchmarking across backends.

- **Status:** Architecture / scaffolding
- **Owners:** Platform team
- **Last updated:** 2026-07-01

---

## 1. Overview

The Vector Search Platform is a **backend-pluggable semantic search service**. A single, uniform
API sits in front of four interchangeable vector stores so that teams can:

1. Ingest documents, generate embeddings, and index them into one or more backends.
2. Run **hybrid search** (BM25/keyword + dense vector) over any backend behind one contract.
3. **Benchmark** backends against a labeled ground-truth set (recall@k, latency p50/p95, QPS)
   to make data-driven infrastructure decisions.

The value proposition is *comparability*: identical queries and datasets, apples-to-apples metrics,
one codebase. The `VectorStore` abstraction is the architectural heart of the system.

---

## 1.1 Project File Structure (Code + CI + Tools)

```text
2-Vector-Search-Platform/
├── docs/                              # Architecture & engineering docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── backend/                          # FastAPI async service
│   ├── app/
│   │   ├── main.py                    # ASGI app factory, middleware, lifespan
│   │   ├── core/                      # Cross-cutting concerns
│   │   │   ├── config.py              # pydantic-settings, 12-factor config
│   │   │   ├── logging.py             # structlog JSON logging + request IDs
│   │   │   ├── security.py            # API-key / JWT auth dependency
│   │   │   └── exceptions.py          # domain errors + FastAPI handlers
│   │   ├── api/
│   │   │   ├── deps.py                # DI: settings, db session, current user
│   │   │   └── v1/
│   │   │       ├── router.py          # aggregates versioned routers
│   │   │       └── endpoints/
│   │   │           ├── health.py      # liveness / readiness
│   │   │           ├── documents.py   # ingest / index documents
│   │   │           ├── search.py      # hybrid & vector search
│   │   │           └── benchmarks.py  # recall@k benchmark runs
│   │   ├── models/                    # SQLAlchemy ORM models
│   │   │   └── document.py
│   │   ├── schemas/                   # Pydantic request/response DTOs
│   │   │   ├── document.py
│   │   │   ├── search.py
│   │   │   └── benchmark.py
│   │   ├── services/                  # Business logic (orchestration)
│   │   │   ├── embedding_service.py   # Sentence Transformers + Redis cache
│   │   │   ├── search_service.py      # hybrid fusion (RRF), reranking
│   │   │   └── benchmark_service.py   # recall@k, latency, QPS
│   │   ├── vectorstores/              # ★ Backend adapters (Strategy pattern)
│   │   │   ├── base.py                # abstract VectorStore contract
│   │   │   ├── pgvector_store.py
│   │   │   ├── pinecone_store.py
│   │   │   ├── weaviate_store.py
│   │   │   ├── milvus_store.py
│   │   │   └── factory.py             # name -> VectorStore resolver
│   │   └── db/
│   │       ├── base.py                # declarative Base + metadata
│   │       └── session.py             # async engine / session factory
│   ├── migrations/                    # Alembic migrations
│   │   ├── env.py
│   │   └── versions/
│   │       └── 0001_initial.py
│   ├── tests/
│   │   ├── conftest.py                # fixtures (async client, fake store)
│   │   ├── unit/
│   │   └── integration/
│   ├── alembic.ini
│   ├── pyproject.toml                 # ruff, mypy, pytest config
│   ├── requirements.txt
│   ├── Dockerfile
│   └── .env.example
│
├── frontend/                         # Next.js 14 (App Router) + TypeScript
│   ├── src/
│   │   ├── app/                       # routes (layout, page, /search)
│   │   ├── components/                # SearchBar, SearchResults, BenchmarkPanel
│   │   ├── lib/api.ts                 # typed API client
│   │   └── types/                     # shared TS types
│   ├── package.json
│   ├── tsconfig.json
│   ├── next.config.js
│   ├── .eslintrc.json
│   └── .env.example
│
├── infra/                            # Deployment & local orchestration
│   ├── docker-compose.yml            # postgres+pgvector, redis, weaviate, milvus
│   ├── ecs/task-definition.json      # AWS ECS Fargate task def
│   └── terraform/                    # (stub) IaC for ECS/ALB/RDS/ElastiCache
│
├── .github/workflows/                # CI/CD (GitHub Actions)
│   ├── backend-ci.yml                # lint → type → test → build image
│   ├── frontend-ci.yml               # lint → type → test → build
│   └── deploy.yml                    # push to ECR → deploy to ECS
│
├── scripts/
│   └── benchmark.py                  # CLI harness to run recall@k offline
│
├── .gitignore
├── .pre-commit-config.yaml
└── README.md
```

### Rationale for the structure

- **`vectorstores/` is a first-class package**, not buried in services. Every backend implements the
  same `VectorStore` ABC, so adding a fifth backend (e.g., Qdrant) is one file + one factory line.
- **`services/` holds orchestration** (embedding, hybrid fusion, benchmarking) and is unaware of
  *which* backend it talks to — it depends on the `VectorStore` abstraction, not concretions.
- **`schemas/` (Pydantic) vs `models/` (SQLAlchemy)** are deliberately separated so wire contracts
  never leak persistence details.
- **Config lives in `.env` files consumed by `pydantic-settings`** — strict 12-factor, no secrets in code.

---

## 1.2 Implementation TODO List

### ✅ Phase 0 — Scaffolding (this deliverable)
- [x] Directory structure, docs, config stubs
- [x] `VectorStore` abstract contract + four adapter stubs
- [x] FastAPI app skeleton with versioned router
- [x] CI/CD workflow stubs, Docker & docker-compose

### ✅ Phase 1 — Foundation (High priority) — DONE
- [x] Implement `EmbeddingService` with pluggable providers (Sentence Transformers **and** an
      offline hashing embedder) + optional Redis cache
- [x] Implement `PgVectorStore` fully (asyncpg pool, HNSW index, cosine distance) as the reference
      backend; add a real in-process `memory` backend for dev/tests
- [x] Wire Alembic migrations; create `documents`, `document_chunks`, and pgvector `chunk_embeddings`
- [x] Implement `/documents` ingest endpoint (chunk → embed → persist → upsert)
- [x] Implement `/search` vector endpoint (+ keyword + hybrid)
- [x] API-key auth dependency + structured logging + global exception handlers
- [x] Unit + integration tests; CI green (ruff + mypy + pytest, 76% coverage)

### ✅ Phase 2 — Core features (Medium priority) — DONE
- [x] Implement Pinecone, Weaviate, Milvus adapters against the same contract
- [x] Hybrid search: portable keyword search fused with vector via **RRF**
- [x] `BenchmarkService`: recall@k, MRR, latency p50/p95, QPS over a labeled dataset
- [x] `/benchmarks` endpoint (graceful skip of disabled backends)
- [x] Next.js UI: search, document management (ingest/list/delete), benchmark runner + panel
- [x] Full document CRUD (create/read/list/update/delete) with re-indexing
- [x] Integration tests (offline: memory backend + SQLite + hashing embedder)
- [x] CI/CD workflows + `docker compose up` full stack (deploy pipeline ready for ECS)

### 🟢 Phase 3 — Production polish
Done in the hardening pass:
- [x] Reranking stage, feature-flagged (`ENABLE_RERANKER`) — real lexical reranker (cross-encoder is a drop-in)
- [x] Fail-fast production secret guard (refuse to boot in prod with a default/blank `API_KEY`)
- [x] GZip response compression
- [x] Dialect-aware keyword search (Postgres FTS via the GIN index; LIKE fallback on SQLite)
- [x] Fixed list/detail over-fetch (chunk counts via aggregate; DB-level `ON DELETE CASCADE` + SQLite FK pragma)
- [x] Raised test coverage to ≥80% (adapters, factory, config, embedder selection)
- [x] Bumped Next.js to the latest patched 14.2.x (install-flagged CVE)

Tracked follow-ups:
- [ ] Upgrade Next.js 14 → 16 + React 19 (residual advisories need the major bump; app avoids the affected surfaces)
- [ ] Streaming ingestion for large corpora; batch embedding with backpressure
- [ ] Retries + circuit breakers around external backends *(pgvector pooling done)*
- [ ] Observability: OpenTelemetry traces, Prometheus metrics, dashboards
- [ ] Cost/latency comparison report auto-generated from benchmark runs
- [ ] Blue/green (or canary) ECS deploys; staging + prod promotion
- [ ] Load testing (Locust/k6) and capacity plan

---

## 2. Milestones & Rough Sequencing

| Milestone | Deliverable | Exit criteria |
|-----------|-------------|---------------|
| M1 | pgvector reference path | Ingest + search working end-to-end, tests green |
| M2 | Multi-backend parity | All 4 adapters pass the same contract test suite |
| M3 | Hybrid + benchmarks | recall@k report reproducible across backends |
| M4 | UI + dev deploy | Next.js app live against dev ECS service |
| M5 | Prod-ready | Observability, canary deploy, load-tested |

---

## 3. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Backend API drift (Pinecone/Weaviate/Milvus SDKs) | Adapters break | Pin SDK versions; contract tests; thin adapters |
| Embedding model latency on CPU | Slow ingest | Batch + cache in Redis; optional GPU task on ECS |
| Distance-metric mismatch across backends | Wrong recall numbers | Normalize vectors; assert metric per backend in factory |
| Large-corpus memory pressure | OOM on ECS | Stream + chunk; bounded batch sizes; backpressure |
| Cost of managed Pinecone in benchmarks | Budget | Feature-flag paid backends; gate behind env config |

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the system design and [`TECH-NOTES.md`](./TECH-NOTES.md)
for CI/CD, testing, and environment guidance.
