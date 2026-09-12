# Project Plan — LLM-Powered Document Intelligence

> Enterprise document Q&A and summarization across legal/medical corpora, built on a
> Retrieval-Augmented Generation (RAG) pipeline. LangChain orchestrates multi-step
> retrieval + synthesis; Claude (via AWS Bedrock) is the reasoning engine; Pinecone is the
> vector index; LangSmith provides end-to-end observability.

---

## 1.1 Project File Structure

The repository is a polyglot monorepo with a clear seam between the Python backend, the
Next.js frontend, infrastructure-as-code, and CI/CD.

```
2-LLM-Powered-Document-Intelligence/
├── README.md
├── .env.example                     # Root env template (see TECH-NOTES §3.4)
├── .gitignore
├── docker-compose.yml               # Local dev: api + postgres (+ optional pgadmin)
├── Makefile                         # Common dev tasks (lint, test, run, migrate)
│
├── docs/                            # Architecture & engineering docs
│   ├── PROJECT-PLAN.md              # ← this file
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── backend/                         # Python 3.12 · FastAPI · LangChain · Bedrock
│   ├── pyproject.toml               # Deps, ruff, mypy, pytest config
│   ├── requirements.txt             # Pinned runtime deps (pip fallback)
│   ├── Dockerfile
│   ├── .env.example
│   ├── alembic.ini                  # DB migration config
│   ├── app/
│   │   ├── main.py                  # FastAPI app factory + middleware
│   │   ├── core/                    # Cross-cutting concerns
│   │   │   ├── config.py            # Pydantic Settings (12-factor config)
│   │   │   ├── logging.py           # Structured JSON logging
│   │   │   └── security.py          # AuthN/Z helpers (JWT, API keys)
│   │   ├── api/
│   │   │   ├── deps.py              # FastAPI dependencies (db, auth, services)
│   │   │   └── v1/
│   │   │       ├── router.py        # Aggregates v1 endpoints
│   │   │       └── endpoints/
│   │   │           ├── health.py    # Liveness/readiness probes
│   │   │           ├── documents.py # Upload / list / delete docs
│   │   │           └── query.py     # RAG Q&A + summarization
│   │   ├── models/                  # SQLAlchemy ORM models
│   │   │   ├── base.py
│   │   │   ├── document.py
│   │   │   └── query_log.py
│   │   ├── schemas/                 # Pydantic request/response DTOs
│   │   │   ├── document.py
│   │   │   └── query.py
│   │   ├── services/                # Business logic (orchestration layer)
│   │   │   ├── document_service.py
│   │   │   └── rag_service.py
│   │   ├── rag/                     # RAG building blocks
│   │   │   ├── llm.py               # Bedrock Claude client factory
│   │   │   ├── embeddings.py        # Bedrock Titan / embedding factory
│   │   │   ├── vector_store.py      # Pinecone wrapper
│   │   │   ├── chunking.py          # Document splitting strategy
│   │   │   ├── ingestion.py         # Load → chunk → embed → upsert pipeline
│   │   │   └── chains.py            # LangChain retrieval + QA chains
│   │   └── db/
│   │       └── session.py           # Async engine + session factory
│   ├── migrations/                  # Alembic revisions
│   │   ├── env.py
│   │   └── versions/
│   │       └── 0001_initial.py
│   └── tests/
│       ├── conftest.py
│       ├── test_health.py
│       └── test_rag_service.py
│
├── frontend/                        # Next.js (App Router) · TypeScript
│   ├── package.json
│   ├── tsconfig.json
│   ├── next.config.mjs
│   ├── .eslintrc.json
│   ├── .env.example
│   └── src/
│       ├── app/
│       │   ├── layout.tsx
│       │   ├── page.tsx              # Document Q&A page
│       │   └── globals.css
│       ├── components/
│       │   ├── DocumentUpload.tsx
│       │   └── DocumentQA.tsx        # ← reference component (TECH-NOTES §4.1)
│       └── lib/
│           └── api.ts               # Typed fetch client for the backend
│
├── infrastructure/
│   └── terraform/                   # AWS infra (Bedrock access, RDS, ECS, S3, IAM)
│       ├── versions.tf
│       ├── variables.tf
│       ├── main.tf
│       ├── bedrock.tf               # Bedrock model access + IAM policy
│       └── outputs.tf
│
└── .github/
    └── workflows/
        ├── ci.yml                   # Lint → typecheck → test → build
        └── cd.yml                   # Build image → push ECR → deploy ECS
```

### Design rationale

| Concern | Decision |
| --- | --- |
| **Monorepo vs polyrepo** | Monorepo — backend, frontend, and IaC version together; one PR can change an API contract and its consumer atomically. |
| **`rag/` as a distinct package** | The RAG primitives (LLM, embeddings, vector store, chains) are the core IP and the most volatile code. Isolating them keeps the FastAPI layer thin and the chains independently testable. |
| **`services/` layer** | Endpoints stay declarative; all orchestration (ingestion, retrieval, logging) lives in services so it can be reused by background workers and tested without HTTP. |
| **Versioned API (`api/v1`)** | Enterprise consumers need stable contracts; new behavior ships under `v2` without breaking integrations. |
| **Schemas vs models split** | Pydantic DTOs (wire contract) are deliberately decoupled from SQLAlchemy models (persistence) to avoid leaking storage details to clients. |

---

## 1.2 Implementation TODO List

> **Phase 1 & 2 are implemented.** The codebase ships a working RAG application with two
> real backends behind one interface (`local` for offline dev/CI; `bedrock` for production),
> 33 passing tests (~77% coverage), a runnable Alembic migration, and `docker compose up`
> working with no cloud credentials. Phase 3 is the forward roadmap.

### ✅ Phase 1 — Foundation (high priority) — DONE

- [x] Initialize repo, `.gitignore`, lint/format config (ruff).
- [x] Backend skeleton: FastAPI app factory, settings, structured JSON logging, health/readiness checks.
- [x] AWS Bedrock model access + least-privilege IAM codified in Terraform (`infrastructure/terraform/bedrock.tf`).
- [x] PostgreSQL (async SQLAlchemy + local docker-compose) and Alembic baseline migration (runs cleanly).
- [x] Vector store with per-tenant namespaces (in-memory for `local`; Pinecone wrapper for `bedrock`).
- [x] LangSmith tracing wired (env-gated in the app lifespan).
- [x] CI pipeline: lint → typecheck → test (backend) + lint → typecheck → build (frontend).

### ✅ Phase 2 — Core features (medium priority) — DONE

- [x] Ingestion pipeline: upload → storage → parse (PDF/DOCX/TXT) → chunk → embed → upsert, with per-document metadata (tenant, doc type). Inline (local) or SQS worker (prod).
- [x] RAG Q&A pipeline: retriever → context assembly → Claude synthesis → cited answer.
- [x] Summarization (`/documents/{id}/summarize`) over a document's chunks.
- [x] Multi-tenant namespace isolation + metadata (`doc_type`) filtering — enforced and tested.
- [x] Streaming responses (SSE) at `/query/stream`.
- [x] Frontend: upload UI + Q&A chat with loading/error/success/citation states.
- [x] Query logging + feedback capture (`/query/{id}/feedback` → `feedback` column for the eval dataset).
- [x] Integration tests against an ephemeral DB + the offline `local` backend (no live Bedrock/Pinecone).

### ☐ Phase 3 — Polish & optimization (lower priority) — ROADMAP

- [ ] Prompt caching for the shared system prompt + retrieved context (cost/latency).
- [ ] Reranking (e.g., Cohere/Bedrock rerank) to lift retrieval precision.
- [ ] Domain fine-tuning / few-shot prompt library for legal vs medical QA.
- [ ] LangSmith eval suite: retrieval recall@k, answer faithfulness, citation accuracy.
- [ ] Hybrid search (dense + sparse/BM25) and query rewriting.
- [ ] Autoscaling, rate limiting per tenant, cost dashboards.
- [ ] Blue/green deploy, canary rollout, automated rollback on SLO breach.
- [ ] PII detection/redaction guardrails for medical documents (HIPAA posture).

---

## Models in use (AWS Bedrock)

All inference runs on Claude via Amazon Bedrock. Bedrock model IDs carry an `anthropic.` prefix:

| Role | Model | Bedrock model ID |
| --- | --- | --- |
| Primary synthesis / hardest QA | Claude Opus 4.8 | `anthropic.claude-opus-4-8` |
| Balanced default / high-volume | Claude Sonnet 4.6 | `anthropic.claude-sonnet-4-6` |
| Cheap/fast (classification, routing) | Claude Haiku 4.5 | `anthropic.claude-haiku-4-5` |
| Embeddings | Amazon Titan Text Embeddings v2 | `amazon.titan-embed-text-v2:0` |

> Default model is **Claude Opus 4.8** (`anthropic.claude-opus-4-8`). Bedrock does not support
> Anthropic server-side tools or Managed Agents — agentic behavior is implemented client-side
> through LangChain. See `docs/TECH-NOTES.md §3.6`.
