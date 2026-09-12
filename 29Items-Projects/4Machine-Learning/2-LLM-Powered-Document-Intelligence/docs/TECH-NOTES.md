# Technical Notes — LLM-Powered Document Intelligence

Actionable guidance for building, testing, shipping, and operating the system.

---

## 3.1 CI/CD Pipeline Design

Two GitHub Actions workflows (see `.github/workflows/`):

**`ci.yml` — on every PR and push to `main`**

```
lint ──► typecheck ──► test ──► build
```

| Stage | Backend | Frontend |
| --- | --- | --- |
| **lint** | `ruff check` + `ruff format --check` | `eslint` + `prettier --check` |
| **typecheck** | `mypy app` | `tsc --noEmit` |
| **test** | `pytest --cov` (fail under threshold) | `vitest` / `jest` (when added) |
| **build** | `docker build` (no push) | `next build` |

Run backend and frontend as parallel jobs; gate the merge on both. Cache pip/npm and Docker layers for speed.

**`cd.yml` — on push to `main` (or tag) after CI passes**

```
build image ──► push to ECR ──► run DB migrations ──► deploy ECS (dev) ──► smoke test
        └──► (manual approval) ──► deploy staging ──► deploy prod (blue/green)
```

- Authenticate to AWS via **OIDC federation** (no long-lived AWS keys in GitHub).
- Migrations run as a one-off ECS task *before* the new revision serves traffic.
- Environment promotion is gated: `dev` auto, `staging`/`prod` require approval.

---

## 3.2 Testing Strategy

| Layer | Tooling | Target |
| --- | --- | --- |
| **Unit** | `pytest`, `pytest-asyncio` | Pure logic: chunking, prompt assembly, services with mocked Bedrock/Pinecone. **Coverage target: ≥ 80%** on `services/` and `rag/`. |
| **Integration** | `pytest` + `testcontainers` / ephemeral Postgres, mocked external APIs | API → service → DB round trips; ingestion pipeline against a fake vector store. |
| **Contract** | `schemathesis` against the OpenAPI schema | Request/response shapes stay stable for enterprise consumers. |
| **E2E** | Playwright | Upload → index → ask → cited answer, through the real frontend against a seeded backend. |
| **LLM evals** | LangSmith datasets + evaluators | Retrieval recall@k, answer **faithfulness/groundedness**, citation accuracy. Run nightly and on prompt/model changes — these are quality gates, not unit tests. |

Principles:
- **Never call real Bedrock/Pinecone in unit tests** — inject fakes via the service constructors / FastAPI deps.
- Treat prompts and chains as code: snapshot the assembled prompt, assert on structure.
- LLM output is non-deterministic — assert on *properties* (cites sources, stays on-context, valid JSON), not exact strings.

---

## 3.3 Deployment Strategy

- **Containerization** — Backend ships as a single Docker image (multi-stage build, non-root user, slim Python 3.12 base). The ingestion worker runs the *same* image with a different entrypoint.
- **Runtime** — **ECS Fargate** behind an ALB for the API; Fargate tasks (or Lambda for spiky loads) for the worker. No servers to patch.
- **Frontend** — Next.js deployed to **Vercel** or **AWS Amplify / S3+CloudFront**; talks to the API over HTTPS.
- **Data plane** — RDS PostgreSQL (Multi-AZ in prod), S3 for raw docs, Pinecone serverless for vectors, Bedrock for inference.
- **Rollout** — Blue/green via ECS + CodeDeploy: shift traffic to the new task set, run smoke tests, auto-rollback on alarm.
- **IaC** — Everything in Terraform (`infrastructure/terraform/`); no click-ops. Bedrock model access + IAM are codified (`bedrock.tf`).

---

## 3.4 Environment Management

Configuration is **12-factor**: all behavior is driven by environment variables, validated at startup by Pydantic `Settings` (`app/core/config.py`). Three environments — `development`, `staging`, `production` — differ only by env values, never by code.

- Local dev: `.env` (git-ignored), loaded via docker-compose.
- Staging/prod: values come from **AWS Secrets Manager** + ECS task definition env, never from files.

### `.env.example` (root template)

```dotenv
# ─── App ───────────────────────────────────────────────
APP_ENV=development                 # development | staging | production
LOG_LEVEL=INFO
API_V1_PREFIX=/api/v1

# ─── AWS / Bedrock ─────────────────────────────────────
AWS_REGION=us-east-1
# Default Claude model on Bedrock (note the anthropic. prefix on Bedrock IDs)
BEDROCK_MODEL_ID=anthropic.claude-opus-4-8
BEDROCK_MODEL_ID_FAST=anthropic.claude-haiku-4-5
BEDROCK_EMBED_MODEL_ID=amazon.titan-embed-text-v2:0
# Local dev only — in AWS, use an IAM role (IRSA / task role), not static keys
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

# ─── Pinecone ──────────────────────────────────────────
PINECONE_API_KEY=
PINECONE_INDEX=doc-intelligence
PINECONE_CLOUD=aws
PINECONE_REGION=us-east-1

# ─── PostgreSQL ────────────────────────────────────────
DATABASE_URL=postgresql+asyncpg://postgres:postgres@localhost:5432/docintel

# ─── LangSmith (observability) ─────────────────────────
LANGSMITH_TRACING=true
LANGSMITH_API_KEY=
LANGSMITH_PROJECT=doc-intelligence

# ─── Auth ──────────────────────────────────────────────
JWT_ISSUER=https://your-idp.example.com/
JWT_AUDIENCE=doc-intelligence-api
JWT_JWKS_URL=https://your-idp.example.com/.well-known/jwks.json

# ─── RAG tuning ────────────────────────────────────────
RAG_TOP_K=6
RAG_CHUNK_SIZE=1000
RAG_CHUNK_OVERLAP=150
RAG_MAX_TOKENS=4096
```

The frontend has its own `frontend/.env.example` (only `NEXT_PUBLIC_*` values reach the browser).

---

## 3.5 Version Control Workflow

**Trunk-based development with short-lived feature branches.**

- `main` is always deployable; protected (required CI, ≥1 review, no direct pushes).
- Feature branches are small and merge within days, behind feature flags when needed.
- **Conventional Commits** (`feat:`, `fix:`, `chore:`…) drive changelogs and semver tags.
- Squash-merge to keep `main` linear and bisectable.

*Rationale:* trunk-based suits a small team shipping continuously and pairs naturally with CD. Long-lived release branches (Gitflow) would add merge overhead this project doesn't need; flags handle in-progress work instead.

---

## 3.6 Common Pitfalls (this stack)

**Bedrock / Claude**
- **Model IDs differ on Bedrock** — they carry an `anthropic.` prefix (`anthropic.claude-opus-4-8`), *not* the bare first-party ID. Using the bare ID returns a validation error.
- **No server-side tools or Managed Agents on Bedrock** — code execution, web search, and Managed Agents are first-party-only. Implement agentic/tool behavior **client-side via LangChain**.
- **Refusal-fallback `fallbacks` param is unavailable on Bedrock** — use client-side retry/fallback (e.g., LangChain `with_fallbacks`) instead.
- **Thinking config** — on Opus 4.8/4.7 and Sonnet 4.6, use adaptive thinking; `budget_tokens` and sampling params (`temperature`/`top_p`/`top_k`) are removed on 4.7+ and will error. Don't lowball `max_tokens`; stream for large outputs.
- **Model access must be enabled** — Bedrock requires explicit model-access grants per account/region (codified in `bedrock.tf`); inference 403s until granted.

**RAG / LangChain**
- **Embedding dimension mismatch** — the Pinecone index dimension must match the embedding model exactly (Titan v2 = 1024). Changing models means re-indexing.
- **Chunking quality dominates retrieval quality** — naive fixed-size splits cut sentences/clauses; use recursive splitting with overlap and preserve metadata. Garbage chunks → confident wrong answers.
- **Prompt injection from documents** — retrieved context is untrusted; instruct the model to answer only from context and ignore embedded instructions.
- **Silent context truncation** — never trim oversized context to fit; detect and chunk/summarize, or you'll fabricate answers from partial data.
- **Non-deterministic outputs in tests** — assert on properties, not exact strings.
- **LangChain churn** — pin versions; the `langchain` / `langchain-aws` / `langchain-pinecone` packages move fast and split frequently.

**Pinecone / Postgres**
- **Namespace isolation** — forgetting per-tenant namespaces leaks data across tenants.
- **Eventual consistency** — a just-upserted vector may not be immediately queryable; surface ingestion status rather than promising instant search.
- **Async DB pitfalls** — mixing sync and async sessions, or sharing a session across tasks, causes subtle deadlocks. One session per request.

**Frontend**
- **SSE buffering** — proxies/ALB can buffer streams; disable buffering and flush, or first-token latency looks broken.
- **Secrets leakage** — only `NEXT_PUBLIC_*` vars reach the browser; never expose the Pinecone/LangSmith/AWS keys client-side.

---

## Quick start (local)

```bash
cp .env.example .env                  # fill in keys
docker compose up -d postgres         # start DB
cd backend
pip install -r requirements.txt
alembic upgrade head                  # apply migrations
uvicorn app.main:app --reload         # http://localhost:8000/docs

cd ../frontend
npm install
npm run dev                           # http://localhost:3000
```
