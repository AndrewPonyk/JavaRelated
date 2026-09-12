# Vector Search Platform — Technical Notes

> Actionable engineering guidance: CI/CD, testing, deployment, environments, git workflow,
> and the sharp edges specific to this stack.

---

## 3.1 CI/CD Pipeline Design

Three workflows under `.github/workflows/` keep concerns separated and fast.

```mermaid
flowchart LR
    PR[PR / push] --> LINT[Lint<br/>ruff / eslint]
    LINT --> TYPE[Type-check<br/>mypy / tsc]
    TYPE --> TEST[Test<br/>pytest / vitest]
    TEST --> BUILD[Build image<br/>docker buildx]
    BUILD --> SCAN[Scan<br/>trivy / pip-audit]
    SCAN --> PUSH{main?}
    PUSH -->|yes| ECR[Push to ECR]
    ECR --> DEPLOY[Deploy ECS<br/>dev -> staging -> prod]
    PUSH -->|no| STOP[Stop: PR gate only]
```

**Stages:**

1. **Lint** — `ruff check` (backend), `eslint` (frontend). Fast, runs first, fails cheap.
2. **Format check** — `ruff format --check`, `prettier --check`.
3. **Type-check** — `mypy app` (strict on `app/`), `tsc --noEmit`.
4. **Test** — `pytest` with coverage gate; `vitest`/`jest` for frontend. Integration tests spin up
   `docker-compose` services (pgvector, weaviate, milvus) via GitHub Actions service containers.
5. **Build** — multi-stage Docker image; tag with git SHA.
6. **Security scan** — `pip-audit` / `npm audit`, `trivy image` on the built container.
7. **Deploy** (only on `main`, and only after all gates) — push to **ECR**, render new **ECS task
   definition**, `aws ecs update-service` with a fresh task def; wait for stable. Promote
   dev → staging → prod behind manual approval (GitHub Environments protection rules).

**Principles:** every gate is required for merge; deploys are triggered only from `main`; images are
immutable and tagged by SHA (never redeploy `latest`); secrets come from GitHub OIDC → AWS role (no
long-lived AWS keys in CI).

---

## 3.2 Testing Strategy

| Layer | Tooling | Target |
|-------|---------|--------|
| Unit | `pytest`, `pytest-asyncio` | Fast, no I/O; mock the `VectorStore` + embedding model |
| Contract | `pytest` parametrized over adapters | **Every backend passes the same suite** |
| Integration | `pytest` + `httpx.AsyncClient` + docker-compose services | Real pgvector/weaviate/milvus |
| E2E | Playwright (frontend↔backend) | Critical user journeys (search, benchmark view) |
| Load | k6 / Locust | Latency p95 + QPS budgets per backend |

**Coverage targets:** ≥ 85% on `app/services/` and `app/vectorstores/` (the logic that matters),
≥ 70% overall. Coverage is a gate, not a vanity metric — the contract suite is the real safety net.

**Key patterns:**

- **Contract test the abstraction.** One `test_vectorstore_contract.py` parametrized over each adapter
  (using a `FakeVectorStore` in unit mode and real backends in integration mode) guarantees behavioral
  parity — the single most valuable test in a multi-backend system.
- **Deterministic embeddings in tests.** Inject a stub embedder (fixed vectors) so search assertions
  are deterministic; the real Sentence Transformers model is exercised only in a few slow integration tests.
- **Golden recall@k dataset.** Ship a tiny labeled fixture (queries → relevant doc ids) and assert the
  benchmark math (recall@k, MRR) against known-good numbers.
- **Async everywhere.** Use `pytest-asyncio` and `httpx.AsyncClient(app=...)` for endpoint tests.

---

## 3.3 Deployment Strategy

**Containerized, on AWS ECS Fargate.**

- **Image:** one multi-stage `Dockerfile` produces a slim runtime image (build deps discarded).
  The *same* image runs the API (`uvicorn app.main:app`) and, in Phase 3, a worker entrypoint —
  one artifact, multiple entrypoints.
- **Compute:** ECS **Fargate** service behind an **ALB**; target-tracking autoscaling on CPU +
  request count. No servers to patch.
- **Data:**
  - **RDS PostgreSQL** with the `vector` extension (pgvector) — reference backend + system of record.
  - **ElastiCache Redis** — embedding/query cache.
  - **Pinecone** (managed SaaS), **Weaviate** / **Milvus** (managed or self-hosted on ECS/EKS) —
    external backends, reached over private networking where possible.
- **Rollout:** rolling deploy by default; **blue/green via CodeDeploy** (or canary) for prod so a bad
  build drains without downtime. Health checks: ALB → `/api/v1/health/ready`.
- **IaC:** `infra/terraform/` (stub) provisions VPC, ECS cluster/service, ALB, RDS, ElastiCache, ECR,
  IAM, and Secrets Manager entries. `infra/ecs/task-definition.json` is the deployable task spec.

Local parity is provided by `infra/docker-compose.yml` (postgres+pgvector, redis, weaviate, milvus),
so developers run the full backend matrix on their machine.

---

## 3.4 Environment Management

Strict **12-factor**: configuration comes from the environment, validated once by
`pydantic-settings` in `app/core/config.py`. No config literals in code.

- `.env` (gitignored) for local dev; `.env.example` is the checked-in template (below).
- **dev / staging / prod** differ only by env values injected into the ECS task definition from
  **AWS SSM / Secrets Manager** — the image is identical across environments.
- Feature flags (e.g., enable Pinecone, enable reranking) are env booleans so paid/optional backends
  stay off unless explicitly turned on.

### `.env.example` (canonical template — mirrored in `backend/.env.example`)

```dotenv
# ── App ──────────────────────────────────────────────
APP_ENV=development              # development | staging | production
LOG_LEVEL=INFO
API_V1_PREFIX=/api/v1
CORS_ORIGINS=["http://localhost:3000"]

# ── Auth ─────────────────────────────────────────────
API_KEY=dev-local-key-change-me  # dev only; prod pulls from Secrets Manager

# ── PostgreSQL (pgvector) — system of record + reference backend ──
DATABASE_URL=postgresql+asyncpg://vsp:vsp@localhost:5432/vsp

# ── Redis (embedding / query cache) ──────────────────
REDIS_URL=redis://localhost:6379/0
EMBEDDING_CACHE_TTL=86400

# ── Embeddings ───────────────────────────────────────
EMBEDDING_MODEL=sentence-transformers/all-MiniLM-L6-v2
EMBEDDING_DIM=384                # must match the model + backend index dim!
EMBEDDING_BATCH_SIZE=32

# ── Default backend + feature flags ──────────────────
DEFAULT_BACKEND=pgvector         # pgvector | pinecone | weaviate | milvus
ENABLE_PINECONE=false
ENABLE_WEAVIATE=false
ENABLE_MILVUS=false
ENABLE_RERANKER=false

# ── Pinecone ─────────────────────────────────────────
PINECONE_API_KEY=
PINECONE_INDEX=vsp-index

# ── Weaviate ─────────────────────────────────────────
WEAVIATE_URL=http://localhost:8080
WEAVIATE_API_KEY=

# ── Milvus ───────────────────────────────────────────
MILVUS_URI=http://localhost:19530
MILVUS_COLLECTION=vsp_collection
```

---

## 3.5 Version Control Workflow

**Trunk-based development with short-lived feature branches.**

- `main` is always deployable and protected (required CI + 1 review, linear history).
- Branch names: `feat/…`, `fix/…`, `chore/…`; keep them < 2 days of work.
- **Conventional Commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`) → enables automated
  changelogs and semantic-ish versioning.
- Merge via **squash** to keep `main` history clean and each change atomic/revertable.
- Deploys are triggered by merges to `main` (dev auto, staging/prod gated by environment approvals).

**Why trunk-based (not Gitflow):** this is a single deployable service with CI/CD to ECS. Gitflow's
long-lived `develop`/`release` branches add ceremony and merge pain that a continuously deployed
service doesn't need. Short-lived branches + feature flags give us safe incremental delivery without
branch sprawl.

---

## 3.6 Common Pitfalls (stack-specific)

**Vector dimensions must line up everywhere.** `EMBEDDING_DIM` must equal the model's output dim *and*
every backend index's configured dim. A mismatch fails loudly on pgvector but can silently misbehave on
some SDKs. The factory asserts dim/metric on startup.

**Distance metric consistency.** Recall numbers are meaningless if pgvector uses cosine while another
backend defaults to L2/dot. **Normalize vectors** (unit length) and pin cosine across all backends;
assert it per adapter. This is the #1 source of bogus benchmark results.

**Async all the way down.** FastAPI is async; a single blocking call (a sync DB driver, a sync SDK, a
CPU-bound `model.encode` on the event loop) stalls the whole worker. Use `asyncpg`, run blocking SDK
calls and embedding in a threadpool (`run_in_executor` / `anyio.to_thread`), and never mix sync
SQLAlchemy into async paths.

**Sentence Transformers cold start + memory.** The model loads on first use (hundreds of MB). Load it
once at startup (lifespan), not per request; size ECS task memory accordingly; consider a warmup call
in the readiness probe so the first real request isn't slow.

**pgvector index tuning.** Default flat scan is exact but slow at scale. Create an **HNSW** index and
tune `ef_search` for the recall/latency tradeoff; remember HNSW is approximate, so pgvector recall@k
is also a tunable, not a constant.

**Managed-backend rate limits & cost.** Pinecone (and hosted Weaviate/Milvus) throttle and bill per
operation. Bound concurrency with semaphores, batch upserts, and keep paid backends behind feature
flags so CI and local dev don't rack up cost.

**Next.js App Router data fetching.** Decide deliberately between Server Components (fetch on server,
keep the API key server-side) and client fetching. Never ship the backend API key to the browser —
proxy through a Next.js route handler or use server components for authenticated calls.

**Migrations for the `vector` type.** Alembic doesn't know about pgvector's `vector` column out of the
box — register the type (or use `sqlalchemy` + `pgvector.sqlalchemy.Vector`) and ensure
`CREATE EXTENSION IF NOT EXISTS vector;` runs before the first migration that uses it.
