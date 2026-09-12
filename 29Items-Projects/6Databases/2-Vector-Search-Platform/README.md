# Vector Search Platform

Comparative vector database platform for **enterprise semantic document retrieval**. One uniform,
async FastAPI API in front of interchangeable backends — an in-process **memory** store plus
**pgvector, Pinecone, Weaviate, Milvus** — with **hybrid** (keyword + vector) search and **recall@k**
benchmarking to compare them apples-to-apples.

```
Next.js UI  ──HTTPS──►  FastAPI  ──►  Services (embed · hybrid search · benchmark · documents)
                                        │
                                        ▼
                          VectorStore contract  ──►  memory | pgvector | Pinecone | Weaviate | Milvus
                                        │
                       Postgres (source of truth) · Redis (cache) · Sentence Transformers / hashing
```

## What works today

- **Full document lifecycle** — `POST/GET/PUT/DELETE /documents`: chunk → embed → persist (Postgres) →
  index (vector backend). Postgres is the system of record so any backend can be re-indexed from source.
- **Search** — `POST /search` in `vector`, `keyword`, or `hybrid` mode. Hybrid fuses the two rankings
  with **Reciprocal Rank Fusion**; optional lexical **reranking** behind `ENABLE_RERANKER`.
- **Benchmarks** — `POST /benchmarks`: recall@k, MRR, p50/p95 latency, QPS across backends.
- **Embeddings** — pluggable: real **Sentence Transformers**, or a deterministic **hashing** embedder
  that needs no model download (great for offline/dev/CI). `auto` prefers the model, falls back to hashing.
- **Frontend** — Search, Documents (ingest/list/delete), and Benchmark pages, all talking to the API
  through server-side proxy routes (the API key never reaches the browser).

## Quickstart — one command (Docker)

```bash
docker compose up --build          # API :8000, web :3000, Postgres(pgvector), Redis
# open http://localhost:3000  ·  API docs at http://localhost:8000/docs
# add the external backends too:  docker compose --profile backends up --build
```

The API container runs `alembic upgrade head` on start, then serves. It defaults to the `pgvector`
backend and the offline `hashing` embedder (set `EMBEDDING_PROVIDER=auto` for real embeddings).

## Quickstart — local, zero external services

The `memory` backend + `hashing` embedder + SQLite need **nothing** installed but Python deps:

```bash
cd backend
python -m venv .venv && source .venv/bin/activate     # Windows: .venv\Scripts\activate
pip install -r requirements.txt
DEFAULT_BACKEND=memory EMBEDDING_PROVIDER=hashing \
  DATABASE_URL=sqlite+aiosqlite:///./vsp.db REDIS_URL= \
  uvicorn app.main:app --reload                        # http://localhost:8000/docs
```

Frontend:

```bash
cd frontend
npm install
cp .env.example .env.local        # points BACKEND_URL at http://localhost:8000/api/v1
npm run dev                        # http://localhost:3000
```

## Tests

```bash
cd backend
pytest                             # 34 tests, offline (memory backend + SQLite + hashing embedder)
pytest --cov=app --cov-report=term-missing   # ~76% coverage
```

Everything runs without Docker or external services. CI (`.github/workflows/backend-ci.yml`) additionally
spins up Postgres+Redis service containers and runs `ruff`, `mypy`, then `pytest`.

## Project layout

```
backend/    FastAPI service
  app/
    api/            versioned routers + DI
    services/       embedding · search (RRF) · benchmark · documents · chunking · reranker
    vectorstores/   VectorStore contract + memory/pgvector/pinecone/weaviate/milvus adapters
    repositories/   document data access + portable keyword search
    models/ schemas/ db/ core/
  migrations/       Alembic (documents, document_chunks, pgvector chunk_embeddings)
  tests/            unit + integration
frontend/   Next.js 14 (App Router) + TypeScript — pages, components, server proxy routes
infra/      docker-compose (datastores), ECS task def, Terraform stub
.github/    CI (backend, frontend) + deploy (ECR -> ECS via OIDC)
docker-compose.yml   full stack (API + web + Postgres + Redis)
```

## Configuration

All config is environment-driven (`app/core/config.py`, validated by pydantic-settings). See
[`backend/.env.example`](backend/.env.example). Key knobs: `DEFAULT_BACKEND`, `EMBEDDING_PROVIDER`,
`DATABASE_URL`, `REDIS_URL`, `API_KEY`, and the `ENABLE_*` feature flags.

## The core idea

Every backend implements one `VectorStore` contract (`backend/app/vectorstores/base.py`). Services
depend on that abstraction, never on a concrete backend, so **adding a backend is one file plus one
factory line**, and the *same* behavior is expected of all of them — which is what makes the recall@k
comparison trustworthy.

## API examples

Full interactive docs at `/docs` (Swagger) and `/redoc`. All write/search endpoints require the
`X-API-Key` header. Quick tour (`KEY` = your `API_KEY`):

```bash
# Ingest a document (chunk -> embed -> index)
curl -X POST localhost:8000/api/v1/documents -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"text":"Vector databases run approximate nearest-neighbor search over embeddings.","source":"docs"}'
# -> {"document_id":"…","chunks_indexed":1,"backend":"pgvector"}

# Hybrid search (vector + keyword via RRF)
curl -X POST localhost:8000/api/v1/search -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"query":"nearest neighbor search","k":5,"mode":"hybrid"}'

# List / get / delete
curl localhost:8000/api/v1/documents -H "X-API-Key: $KEY"
curl localhost:8000/api/v1/documents/<id> -H "X-API-Key: $KEY"
curl -X DELETE localhost:8000/api/v1/documents/<id> -H "X-API-Key: $KEY"

# Benchmark recall@k across backends
curl -X POST localhost:8000/api/v1/benchmarks -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"backends":["memory","pgvector"],"k":5,"cases":[{"query":"…","relevant_ids":["<doc-id>"]}]}'

# Health (no key required)
curl localhost:8000/api/v1/health/ready
```

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/documents` | ✔ | Ingest + index a document |
| GET | `/api/v1/documents` | ✔ | List documents (paginated: `?limit=&offset=`) |
| GET | `/api/v1/documents/{id}` | ✔ | Get a document |
| PUT | `/api/v1/documents/{id}` | ✔ | Replace + re-index (preserves id) |
| DELETE | `/api/v1/documents/{id}` | ✔ | Delete document + its vectors |
| POST | `/api/v1/search` | ✔ | `vector` / `keyword` / `hybrid` search |
| POST | `/api/v1/benchmarks` | ✔ | recall@k / MRR / latency / QPS |
| GET | `/api/v1/health/{live,ready}` | — | Liveness / readiness probes |

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| API exits on start with *"API_KEY must be a strong, non-default value"* | `APP_ENV=production` with a default/blank `API_KEY`. Set a real `API_KEY` (or use `APP_ENV=development` locally). |
| `401 unauthorized` | Missing/incorrect `X-API-Key` header; it must equal the server's `API_KEY`. |
| `503 backend_unavailable` on search/ingest | The selected backend is down or disabled. For pgvector, ensure Postgres is up + migrated; for Pinecone/Weaviate/Milvus, set `ENABLE_*=true` and the connection vars. Use `DEFAULT_BACKEND=memory` to run with no external services. |
| Ingest/search very slow on first call with `EMBEDDING_PROVIDER=auto` | The Sentence Transformers model is downloading/loading. Use `EMBEDDING_PROVIDER=hashing` for offline/fast, or pre-warm. |
| `docker compose up` backend build is slow | The image installs `torch` (large). It's a one-time build; subsequent starts reuse the image. |
| pgvector error *"type vector does not exist"* | The `vector` extension isn't installed. Migrations run `CREATE EXTENSION vector`; use the `pgvector/pgvector` image (compose does). |
| Tests can't find `app` package | Run `pytest` from `backend/` (pytest is configured with `pythonpath = ["."]`). |
| `next dev` calls fail with 500 | Set `BACKEND_URL` + `BACKEND_API_KEY` in `frontend/.env.local` (proxy routes call the API server-side). |

## Docs

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — structure, phased TODO (Phases 1 & 2 done), milestones
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, diagrams, scaling, security, error handling
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deployment, env, pitfalls
