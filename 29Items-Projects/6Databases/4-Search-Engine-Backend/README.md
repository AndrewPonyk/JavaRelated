# Search Engine Backend

Product search platform with faceted filtering, synonyms, autocomplete, click-feedback
collection, and a Learning-to-Rank pipeline. **Fully implemented and tested** — see the
verification section below.

| Layer | Technology |
|---|---|
| Search | Elasticsearch 8.x — custom analyzers, synonym_graph (hot-reloadable synonyms set), completion suggester, disjunctive facets, LTR rescoring |
| API | Python 3.12 · FastAPI (fully async) · transactional outbox PG→ES sync |
| Source of truth | PostgreSQL 16 (SQLAlchemy 2 async + Alembic) |
| Cache | Redis 7 — autocomplete cache-aside (TTL + jitter) |
| Frontend | Vue 3 + TypeScript + Vite + Pinia (autocomplete combobox, facets, price filter) |
| ML | XGBoost LambdaMART: judgments (COEC) → training → upload (plugin & native modes) |
| Cloud / CI | Google Cloud (Cloud Run, Cloud SQL, Memorystore, Elasticsearch) · GitHub Actions · Terraform |

## Documentation

- [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) — structure, roadmap (Phases 1–2 complete), milestones, risks
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — pattern, component interactions, data flows (diagrams), scalability, security
- [docs/TECH-NOTES.md](docs/TECH-NOTES.md) — CI/CD, testing, deployment, environments, pitfalls
- Interactive API docs (OpenAPI): http://localhost:8000/docs once the stack is up

## Quickstart (local)

```bash
# 1. Infrastructure + API (ES needs ~1 GB RAM; first run pulls images)
cp .env.example .env
docker compose up -d --build

# 2. Database schema
cd backend && pip install -e ".[dev]" && alembic upgrade head && cd ..

# 3. Search assets (synonyms set + index + alias) and 1,000 demo products
python scripts/create_indexes.py
python scripts/seed_products.py

# 4. Frontend (dev server proxies /api -> localhost:8000)
cd frontend && npm ci && npm run dev
```

Open http://localhost:5173 and search — try `wireless headphones`, filter by brand,
set a price range, or search `telly` (synonym → televisions).

## API surface (`/api/v1`)

| Endpoint | Description |
|---|---|
| `GET /search?q=&brand=&category=&price_min=&price_max=&sort=&page=&size=` | Faceted search (disjunctive counts), sorting, pagination, optional LTR rescore |
| `GET /suggest?q=&limit=` | Autocomplete (completion suggester, Redis-cached) |
| `POST /events/click` | Click feedback for LTR training (`X-Session-ID` header) |
| `GET/POST/PUT/DELETE /products` | Catalog CRUD (writes need `X-API-Key`); changes flow to ES via the outbox |
| `POST /admin/reindex` | Re-enqueue the whole catalog through the outbox (`X-API-Key`) |
| `GET /healthz`, `GET /readyz` | Liveness / dependency readiness (unversioned) |

Errors are RFC-7807 `application/problem+json` with a stable `code` and `request_id`;
`/search` + `/suggest` + `/events` are rate-limited per client IP (429 + `Retry-After`).

### Examples

```bash
# Faceted search: sony OR lg headphones between $100 and $400, cheapest first
curl "http://localhost:8000/api/v1/search?q=headphones&brand=sony&brand=lg&price_min=100&price_max=400&sort=price_asc"

# Autocomplete
curl "http://localhost:8000/api/v1/suggest?q=lapt&limit=5"

# Create a product (indexed automatically via the outbox within ~2 s)
curl -X POST http://localhost:8000/api/v1/products \
  -H "X-API-Key: change-me" -H "Content-Type: application/json" \
  -d '{"sku":"DEMO-1","name":"Demo Wireless Headphones","brand":"demo","price":"99.90"}'

# Click feedback (position = 0-based rank in the results the user saw)
curl -X POST http://localhost:8000/api/v1/events/click \
  -H "Content-Type: application/json" -H "X-Session-ID: demo-session" \
  -d '{"query":"headphones","product_id":"<uuid-from-search-hit>","position":0}'
```

## Tests

```bash
# Backend: 63 unit + API tests, coverage gate 70% (actual ~89%)
cd backend && pytest tests -m "not integration" --cov=app

# Backend: full-stack integration against real ES/PG/Redis (compose must be up)
RUN_INTEGRATION=1 pytest tests -m integration          # PowerShell: $env:RUN_INTEGRATION="1"

# Frontend: 29 Vitest tests (store, client, components) + typecheck + build
cd frontend && npm test && npm run build
```

## Learning to Rank (once real traffic has produced events)

```bash
pip install -e "backend[ml]"
python ml/ltr/build_judgments.py --days 30          # search_events -> judgments.tsv
python ml/ltr/train.py --judgments judgments.tsv    # XGBoost; refuses to export if it loses to BM25
python ml/ltr/upload_model.py --mode plugin|native  # push featureset + model to ES
# then set LTR_MODE accordingly (staging first)
```

## Operations

- `python scripts/reindex.py` — zero-downtime reindex: new versioned index, bulk from PG,
  count verification, atomic alias swap, catch-up of writes made during the bulk.
- Synonym edits: change `elasticsearch/synonyms/synonyms_en.txt`, run
  `python scripts/create_indexes.py` — hot reload via the synonyms set API, no reindex.
- `make help` for all developer entrypoints (`up`, `lint`, `test`, `migrate`, `seed`, …).

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `docker compose up` hangs or errors immediately | Docker Desktop isn't running — start it first. First run also pulls ~1 GB of images. |
| Elasticsearch container exits / unhealthy | It needs ~1 GB heap free. On Linux also check `sysctl vm.max_map_count` ≥ 262144. |
| `You must have 'aiohttp' installed` | Reinstall backend deps: `pip install -e "backend[dev]"` — the ES async client needs the `[async]` extra (already declared). |
| API answers but search returns 503 | Index/alias missing — run `python scripts/create_indexes.py`, then seed. Check `GET /readyz` for which dependency is down. |
| Search returns 0 hits after seeding | ES refresh lag is ~1 s; also confirm the seed reported `indexed 1000`. |
| Created product not searchable | The outbox worker polls every `OUTBOX_POLL_INTERVAL_S` (2 s) + 1 s refresh. Check API logs for `outbox_batch_processed` / `outbox_dead_letter`. |
| 429 responses during load tests | Per-IP limiter (`RATE_LIMIT_*` in `.env`) — raise the limits or disable in dev. |
| App refuses to boot in staging/prod | Intentional fail-fast: placeholder `ADMIN_API_KEY` or missing `ES_API_KEY` (set real values via Secret Manager). |
| Ports 8000/9200/5432/6379 already in use | Stop the conflicting service or change the published ports in `docker-compose.yml`. |
| Windows: integration tests fail with `'NoneType' object has no attribute 'send'` | Run them via `pytest -m integration` as documented — the suite disposes the PG pool per test; don't reuse a session-scoped event loop. |

## Repository map

```
backend/        FastAPI service (api → services → gateways), outbox worker, migrations, tests
frontend/       Vue 3 search UI (SearchBar combobox + FacetPanel + SearchResults)
elasticsearch/  Index settings/mappings, synonyms, LTR featureset — versioned as code
ml/             LTR pipeline: judgments (COEC) → XGBoost training → model upload
scripts/        create_indexes, reindex (zero-downtime), seed (1k products)
infrastructure/ Terraform (GCP: Cloud Run + Job, SQL, Redis, ES, secrets, web bucket)
.github/        CI (lint/type/test/coverage/integration/build) + staging & prod deploys
docs/           Plan, architecture, tech notes
```
