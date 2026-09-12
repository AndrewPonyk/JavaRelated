# LLM-Powered Document Intelligence

Enterprise document **Q&A and summarization** across legal and medical corpora, built on a
Retrieval-Augmented Generation (RAG) architecture. The retrieval → synthesis pipeline is
provider-agnostic; **Claude (via AWS Bedrock)** is the production reasoning engine, Pinecone
the vector index, PostgreSQL the metadata store, and LangSmith the observability layer.

> **Status: fully implemented and tested.** `pytest` runs **33 passing tests** (≈77%
> coverage) and `docker compose up` brings up the whole stack — with **no cloud credentials
> required** thanks to the built-in `local` backend.

## Two real backends, one interface

Everything in `app/rag/` is written against protocols (`Embedder`, `VectorStore`,
`ChatModel`) and a backend is chosen by `RAG_BACKEND`:

| Backend | What it is | When |
| --- | --- | --- |
| **`local`** (default) | Real, dependency-light RAG: deterministic hashing embeddings, in-memory cosine vector store, extractive grounded answering with citations. No external services. | Dev, `docker compose up`, CI/tests |
| **`bedrock`** | Claude Opus 4.8 / Sonnet 4.6 / Haiku 4.5 on **Amazon Bedrock** + Pinecone + Titan embeddings, via LangChain. Lazily imported. | Production |

Neither path is a mock — `local` is a genuinely functioning RAG implementation; `bedrock` is
real production code selected by config. Storage (`local`/`s3`) and ingestion
(`inline`/`queue`) switch the same way.

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Python 3.12 · FastAPI · async SQLAlchemy |
| LLM (prod) | Claude on **Amazon Bedrock** via LangChain (`anthropic.claude-opus-4-8`) |
| Vectors (prod) | Pinecone serverless · Titan Text Embeddings v2 |
| Database | PostgreSQL (async, Alembic migrations) |
| Frontend | Next.js (App Router) · TypeScript |
| Observability | LangSmith |
| Infra / CI-CD | Terraform · GitHub Actions · ECS Fargate |

> **Bedrock note:** Claude model IDs on Bedrock carry an `anthropic.` prefix. Bedrock has no
> Anthropic server-side tools / Managed Agents — agentic behavior is client-side via LangChain.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/health`, `/api/v1/ready` | Liveness / readiness |
| `POST` | `/api/v1/documents` | Upload a document (indexed inline or via queue) → `202` |
| `GET` | `/api/v1/documents?limit=&offset=` | List the tenant's documents (paginated) |
| `GET` | `/api/v1/documents/{id}` | Document status / metadata |
| `DELETE` | `/api/v1/documents/{id}` | Delete a document + its vectors |
| `POST` | `/api/v1/documents/{id}/summarize` | Summarize a document |
| `POST` | `/api/v1/query` | RAG Q&A → answer + citations |
| `POST` | `/api/v1/query/stream` | Same, streamed as Server-Sent Events |
| `POST` | `/api/v1/query/{query_id}/feedback` | Thumbs up/down for eval curation |

Interactive OpenAPI docs at `http://localhost:8000/docs`. All routes are tenant-scoped via a
JWT `tenant` claim and require scopes (`documents:read`, `documents:write`, `query:run`).

### Production hardening

- **Auth fails closed in production:** when `APP_ENV=production`, `JWT_JWKS_URL` is required
  (validated at startup); unsigned/dev tokens are rejected. In dev, tokens are decoded
  without verification so the app runs without an IdP.
- **Errors are opaque:** unhandled exceptions return `500 {"detail": "internal server
  error", "request_id": …}` — stack traces are logged server-side, never returned. Domain
  failures (Bedrock/Pinecone) map to `502`; validation to `422`; auth to `401`/`403`.
- **Responses are gzip-compressed** (`> 1 KB`); list endpoints are **paginated** (`limit`,
  `offset`, `total`).
- **Inputs are validated** by Pydantic; uploads are size-capped (`MAX_UPLOAD_BYTES`);
  storage keys are guarded against path traversal; DB access is parameterized via the ORM.
- **Tenant isolation** is enforced in the service layer (DB rows) and the vector store
  (per-tenant namespaces) — covered by tests.

## Quick start — Docker (full stack, no cloud needed)

```bash
docker compose up --build        # postgres + api (runs migrations, then serves)
# API → http://localhost:8000/docs
```

Try it (the dev-token bearer maps to a default tenant):

```bash
TOKEN=dev-token
curl -s -XPOST localhost:8000/api/v1/documents -H "Authorization: Bearer $TOKEN" \
  -F doc_type=legal -F 'file=@README.md'
curl -s -XPOST localhost:8000/api/v1/query -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"question":"What is this project?"}'
```

## Quick start — local (no Docker)

```bash
cd backend
pip install -e ".[dev]"                 # base + dev tools (local backend needs no cloud SDKs)
export DATABASE_URL="sqlite+aiosqlite:///./.data/dev.db"   # or a Postgres URL
mkdir -p .data && alembic upgrade head
uvicorn app.main:app --reload           # http://localhost:8000/docs

cd ../frontend
npm install && npm run dev              # http://localhost:3000
```

## Tests

```bash
cd backend
pytest                                  # 33 tests, ~77% coverage, fully offline
```

Tests cover chunking, the hashing embedder, the cosine vector store (incl. tenant
isolation + metadata filtering), the end-to-end pipeline (ingest → grounded answer →
"I don't know" → summarize → stream), the service layer with a real DB, and the full HTTP
API (upload/list/get/delete/summarize/query/stream/feedback, auth, scopes, validation).

## Enabling the production (Bedrock) backend

```bash
pip install -r backend/requirements-bedrock.txt   # or: pip install -e ".[bedrock]"
# set in .env:
RAG_BACKEND=bedrock
STORAGE_BACKEND=s3
INGEST_MODE=queue
AWS_REGION=us-east-1
PINECONE_API_KEY=...   PINECONE_INDEX=doc-intelligence
SQS_QUEUE_URL=...      S3_BUCKET=...
```

Run the ingestion worker (production): `python -m app.worker`. Provision Bedrock model
access + IAM, RDS, S3, SQS with the Terraform in `infrastructure/terraform/`.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `401 missing bearer token` | Send `Authorization: Bearer <token>`. In dev any token works; `dev-token` maps to the default tenant. |
| `403 missing required scope` | The token lacks the scope (`documents:write`, etc.). Mint one with `app.core.security.make_dev_token(tenant, scopes=...)`. |
| Startup error: `JWT_JWKS_URL is required when APP_ENV=production` | Production fails closed — set `JWT_JWKS_URL` (and `SQS_QUEUE_URL` if `INGEST_MODE=queue`). |
| `ValueError: rag_chunk_overlap must be smaller than rag_chunk_size` | Fix the two `RAG_CHUNK_*` env values. |
| `unable to open database file` (sqlite) | The target directory must exist: `mkdir -p .data` before `alembic upgrade head`. |
| `ModuleNotFoundError: langchain_aws` with `RAG_BACKEND=bedrock` | Install the cloud extra: `pip install -r backend/requirements-bedrock.txt`. |
| `IngestionError: PDF/DOCX support requires …` | Optional parsers: `pip install pypdf python-docx` (already in `requirements.txt`). |
| Query returns "I don't know…" with no citations | The corpus has nothing relevant for that tenant — upload/index a document first (status must be `indexed`). |
| Bedrock `403` / validation error | Enable model access in the Bedrock console for the region, and use the `anthropic.`-prefixed model IDs. |
| SSE stream looks buffered behind a proxy | Disable proxy buffering (the app sets `X-Accel-Buffering: no`). |

## Documentation

- [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) — structure + (completed) implementation checklist
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — pattern, interactions, data flow (Mermaid), scalability, security
- [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) — CI/CD, testing, deployment, env management, pitfalls

## Repository layout

```
backend/         FastAPI app (app/api, app/services, app/rag, app/models, migrations, tests)
frontend/        Next.js + TypeScript (src/app, src/components, src/lib)
infrastructure/  Terraform (S3, SQS, Bedrock IAM, …)
docs/            PROJECT-PLAN · ARCHITECTURE · TECH-NOTES
.github/         CI + CD workflows
```
