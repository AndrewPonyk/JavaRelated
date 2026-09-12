# Search Engine Backend — Technical Notes

Actionable engineering guidance. See [ARCHITECTURE.md](./ARCHITECTURE.md) for design and
[PROJECT-PLAN.md](./PROJECT-PLAN.md) for the roadmap.

---

## 3.1 CI/CD Pipeline Design

```mermaid
flowchart LR
    PR["PR opened / push"] --> L["lint + typecheck<br/>ruff · mypy · eslint · vue-tsc"]
    L --> UT["unit tests<br/>pytest · vitest"]
    UT --> IT["integration tests<br/>ES + PG + Redis service containers"]
    IT --> B["build Docker images<br/>(no push on PR)"]
    B -->|"merge to main"| S["deploy staging<br/>migrate → index assets → deploy → smoke"]
    S -->|"tag vX.Y.Z + manual approval"| P["deploy production<br/>traffic split 10% → 100%"]
```

| Stage | What runs | File |
|---|---|---|
| Lint & typecheck | `ruff check` + `ruff format --check` + `mypy app` ; `eslint` + `vue-tsc --noEmit` | `.github/workflows/ci.yml` |
| Unit tests | `pytest tests/unit` (coverage gate); `vitest run` | `ci.yml` |
| Integration | `pytest -m integration` against ES/PG/Redis **service containers** | `ci.yml` |
| Build | `docker build` backend + frontend (proves images build on every PR) | `ci.yml` |
| Staging deploy | Auth via WIF → push to Artifact Registry → Alembic job → `create_indexes.py` (synonyms set + mappings diff) → `gcloud run deploy` → smoke `/readyz` | `deploy-staging.yml` |
| Production deploy | Same steps, gated by GitHub **environment approval**, canary traffic split, tagged releases only | `deploy-production.yml` |

Principles: every PR proves the whole build; deploys are *promotions of an already-built
image* (immutable `sha`-tagged), never rebuilds; migrations and search-asset updates are
explicit pipeline steps, not side effects of app boot.

## 3.2 Testing Strategy

| Level | Tooling | Target |
|---|---|---|
| Unit (backend) | pytest + pytest-asyncio, mocks for ES/Redis | ≥ 85% on `services/` and `search/` (query builder is pure → test exhaustively) |
| Unit (frontend) | Vitest + Vue Test Utils (jsdom) | Store logic + components with mocked API |
| Integration | pytest `-m integration`; real ES/PG/Redis via testcontainers locally, service containers in CI | Analyzer behavior (synonyms/stemming!), facet counts, suggester, Alembic up/down |
| API contract | httpx `ASGITransport` against the app with DI overrides (`tests/conftest.py`) | Status codes, RFC-7807 shape, validation edges |
| Relevance regression | Golden query set + judged results → nDCG@10 computed in CI (Phase 2) | No silent relevance drops from analyzer/model changes |
| E2E | Playwright: type → suggest → search → facet → paginate (staging, nightly) | Happy paths only, keep < 10 scenarios |

Key habit for search: **treat analyzers as code under test.** A one-line synonym edit can
reorder every result page; the integration suite asserts tokenization (`_analyze` API) and
the relevance suite gates ranking drift.

## 3.3 Deployment Strategy

- **Containerization:** one image per deployable — `backend/Dockerfile` (python:3.12-slim,
  non-root, honors Cloud Run `$PORT`), `frontend/Dockerfile` (Node build → nginx static).
  Frontend can alternatively ship to Cloud Storage + Cloud CDN (cheaper; the default in
  the deploy workflow).
- **Runtime topology (GCP):** Cloud Run service `search-api` (min-instances ≥ 1 in prod);
  Cloud Run **Jobs** for Alembic migrations, full reindex, and the outbox worker; Cloud SQL
  PostgreSQL 16 (private IP); Memorystore Redis; Elasticsearch on GCP — **Elastic Cloud**
  (managed, use native LTR) or **ES on GKE** (self-managed, allows the LTR plugin).
- **Provisioning:** Terraform in `infrastructure/terraform/`, one state per environment,
  `environments/*.tfvars` for sizing. CI never clicks consoles.
- **Release flow:** merge → staging (auto) → tag `vX.Y.Z` → prod behind environment
  approval → Cloud Run traffic split 10%/100% with instant rollback to the previous
  revision (`gcloud run services update-traffic`).
- **Search asset deploys are deploys too:** index settings/mappings and synonyms changes go
  through `scripts/create_indexes.py` / `scripts/reindex.py` in the pipeline — never edited
  by hand in Kibana.

## 3.4 Environment Management

- **Single source of truth:** `pydantic-settings` (`app/core/config.py`). Precedence:
  process env > `.env` file > defaults. Fail-fast on missing/invalid values at boot.
- **Local:** copy `.env.example` → `.env` (gitignored). Compose overrides hostnames to
  service names.
- **Staging/prod:** plain config as Cloud Run env vars (Terraform-managed); secrets
  referenced from Secret Manager (`--set-secrets`), never stored in CI or the repo.
- **Frontend:** `VITE_*` vars are baked at build time (`frontend/.env.example`); per-env
  builds happen in the deploy workflow.

`.env.example` template (kept at repo root; full commented version in the file):

```dotenv
ENVIRONMENT=dev
DEBUG=true
DATABASE_URL=postgresql+asyncpg://search:search@localhost:5432/search
ES_URL=http://localhost:9200
ES_API_KEY=                       # required in staging/prod
ES_PRODUCTS_ALIAS=products
REDIS_URL=redis://localhost:6379/0
SUGGEST_CACHE_TTL_S=300
OUTBOX_ENABLED=true               # PG->ES sync worker (poll interval, batch, retries)
RATE_LIMIT_ENABLED=true           # per-instance limiter on /search + /suggest
LTR_MODE=off                      # off | plugin | native
LTR_MODEL_NAME=products_ltr_v1
ADMIN_API_KEY=change-me
CORS_ORIGINS=["http://localhost:5173"]
```

## 3.5 Version Control Workflow

**Trunk-based development** with short-lived branches:

- `main` is always deployable (staging tracks it); feature branches live ≤ 2–3 days,
  merge via PR (CI green + 1 review); production = annotated tags `vX.Y.Z`.
- Incomplete features hide behind config flags (`LTR_MODE` is the built-in example) rather
  than long-lived branches.
- Conventional Commits (`feat:`, `fix:`, `perf:`, `chore:`) → changelog automation later.

*Why not Gitflow:* one small team, one service, continuous deployment — release/develop
branches add merge ceremony without benefit. *Why not pure trunk (commit to main):* search
relevance changes benefit from PR review + the CI relevance gate before hitting staging.

## 3.6 Common Pitfalls (this stack specifically)

1. **LTR plugin vs Elastic Cloud.** The community `elasticsearch-learning-to-rank` plugin
   cannot be installed on Elastic Cloud. Decide early: self-managed ES (GKE) for the
   plugin, or ES ≥ 8.12 native `learning_to_rank` rescorer (eland-uploaded XGBoost).
   `app/search/ltr.py` isolates the difference; the plugin zip version must match the ES
   version *exactly* (pin it in `infrastructure/docker/elasticsearch/Dockerfile`).
2. **Mappings are immutable.** You cannot change an existing field's type/analyzer.
   Always create `products_v(N+1)` and swap the alias (`scripts/reindex.py`); never
   `PUT _mapping` your way out of an analyzer change.
3. **Synonyms:** index-time synonyms bake into stored tokens (require full reindex on every
   edit) — use **search-time** `synonym_graph` backed by the **synonyms set API** (ES ≥
   8.10) so edits are a `PUT _synonyms/...` + automatic analyzer reload. Multi-word
   synonyms need `synonym_graph` (not `synonym`) and break `match_phrase` in subtle ways.
4. **Facet counts with multi-select** require disjunctive faceting (`post_filter` + each
   facet's agg excluding its own filter). Plain `bool.filter` makes other options in a
   selected group show shrunken counts. Implemented in `app/search/facets.py`; the
   integration suite asserts other brands keep counts when one brand is selected.
5. **Deep pagination:** `from + size > 10000` throws. Cap pages (done in `query_builder`)
   and use `search_after` for anything deeper.
6. **PG ↔ ES drift.** In-request dual writes lose updates on crashes. Move to the outbox
   pattern early; add a nightly count/checksum reconciliation. Treat ES as rebuildable at
   all times — full reindex must stay a one-command, zero-downtime runbook.
7. **Cache stampede & stale suggests.** Expire suggest keys with TTL **+ jitter** (done);
   bump the key namespace (`suggest:v2:`) when suggestion logic changes — don't try to
   invalidate selectively.
8. **rank_feature fields must be positive** — clamp `popularity ≥ 1` when indexing
   (`indexing_service.py`), or ES rejects the document.
9. **Refresh interval vs bulk:** set `refresh_interval: -1` + `replicas: 0` during full
   reindex, restore after; otherwise bulk throughput craters.
10. **asyncpg + Cloud SQL:** use the private-IP connector and keep pool sizes modest
    (Cloud Run scales instances — total connections = pool × instances; add pgbouncer if
    instance count grows).
11. **ES memory in local Docker:** single-node ES needs ~1 GB heap
    (`ES_JAVA_OPTS=-Xms1g -Xmx1g`) and `discovery.type=single-node`; on Linux hosts check
    `vm.max_map_count ≥ 262144`.
12. **Vite dev CORS:** the dev server proxies `/api` → `localhost:8000`
    (`vite.config.ts`), so the browser never does cross-origin calls locally; keep prod
    CORS origins explicit in `CORS_ORIGINS`.
13. **Cancelled keystrokes:** without debounce + `AbortController` (and a stale-response
    guard in the Pinia store), fast typers get out-of-order suggest/search responses
    rendering wrong results.
14. **Don't score what you filter.** Facet selections belong in `bool.filter`
    (cached, unscored); putting them in `must` skews BM25 and slows queries.
15. **Training/serving skew in LTR:** features used in `train.py` must be computed from the
    same featureset the rescorer executes (`elasticsearch/ltr/featureset.json`) — log the
    featureset version with every model.
