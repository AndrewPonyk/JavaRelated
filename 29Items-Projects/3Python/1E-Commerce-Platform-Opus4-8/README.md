# E-Commerce Platform

A full-featured, **working** e-commerce platform: catalog, cart, checkout,
inventory, and vendor management, with Elasticsearch-powered search and a
PyTorch collaborative-filtering recommendation engine.

> Architected and implemented by **Claude Opus 4.8**.
> Status: backend **75 tests passing @ 93% coverage**; frontend builds, lints, and tests green.
> Hardened: Argon2 password hashing, JWT + token blacklist, API rate limiting,
> gzip, security headers, OpenAPI docs, and audited dependencies (every CVE with
> an available patch upgraded; `npm audit`: 0 findings).

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Python 3.12, Django 5, Django REST Framework, SimpleJWT |
| Async | Celery + Redis, separate `default` / `ml` / `email` queues, Celery beat |
| Data | PostgreSQL (primary + read replica), SQLAlchemy for reporting reads |
| Search | Elasticsearch (full-text, faceting, edge-ngram autocomplete) |
| ML | PyTorch + NumPy (matrix-factorization recommendations) |
| Frontend | React 18, TypeScript (strict), TailwindCSS, Vite, TanStack Query, Zustand, React Router |
| Infra | AWS ECS (Fargate), Terraform, GitHub Actions CI/CD |

## Documentation

- **[docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md)** — file structure & phased TODO (all of Phase 1 & 2 ✅)
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — patterns, data flow, scaling, security (Mermaid diagrams)
- **[docs/TECH-NOTES.md](docs/TECH-NOTES.md)** — CI/CD, testing, deployment, env management, pitfalls

## Quick Start (Docker — full stack)

```bash
cp .env.example .env            # local dev defaults already point at the compose services
docker compose up --build       # web, worker, worker-ml, beat, db, redis, elasticsearch, frontend

# In another shell — migrate and create an admin user:
docker compose exec web python manage.py migrate
docker compose exec web python manage.py createsuperuser   # prompts for email + password
```

- API:        http://localhost:8000/api/v1/
- Admin:      http://localhost:8000/admin/
- Frontend:   http://localhost:5173/
- Health:     http://localhost:8000/healthz

## Quick Start (backend without Docker)

```bash
cd backend
python -m venv .venv && source .venv/Scripts/activate   # Windows; use bin/activate on *nix
pip install -r requirements/development.txt
# Point DATABASE_URL/REDIS_URL/ELASTICSEARCH_URL at local services, then:
python manage.py migrate
python manage.py runserver
```

## Running tests

```bash
# Backend — unit + integration, with coverage gate (70%)
cd backend && pytest                 # uses config.settings.test (SQLite, eager Celery, no external services)

# Frontend — type-check, lint, unit tests, production build
cd frontend
npm install
npm run lint
npm run test
npm run build
```

The backend test suite is **hermetic**: it runs on in-memory SQLite with eager
Celery and a mocked Elasticsearch client, so no Postgres/Redis/ES server is
required. Integration tests (`tests/integration/`) use `transaction=True` so the
post-commit email + recommendation side effects actually fire.

## API surface (`/api/v1`)

> **Interactive API docs** (OpenAPI 3): Swagger UI at **http://localhost:8000/api/docs/**,
> raw schema at **/api/schema/**. Every endpoint, parameter, and response is
> documented and explorable there.

| Area | Endpoints |
|------|-----------|
| Auth | `POST /auth/register/` · `POST /auth/token/` · `POST /auth/token/refresh/` · `POST /auth/logout/` · `GET/PATCH /auth/me/` |
| Catalog | `GET/POST /catalog/products/` · `GET/PUT/PATCH/DELETE /catalog/products/{slug}/` (filter: `?category=`, `?status=`, `?search=`) |
| Cart | `GET/DELETE /cart/` · `POST /cart/items/` · `PATCH/DELETE /cart/items/{id}/` |
| Orders | `GET /orders/` · `GET /orders/{number}/` · `POST /orders/checkout/` · `POST /orders/{number}/cancel/` |
| Search | `GET /search/?q=&category=&min_price=&max_price=` · `GET /search/autocomplete/?q=` |
| Recommendations | `GET /recommendations/` · `POST /recommendations/track/` |
| Vendors | `GET /vendors/` · `GET /vendors/{slug}/` · `POST /vendors/` · `GET /vendors/me/` |

### Demo payment gateway

Checkout uses a deterministic, idempotent **fake** gateway by default
(`PAYMENT_GATEWAY=fake`). Use payment token `tok_visa` to succeed or
`tok_decline` to simulate a declined card. Set `PAYMENT_GATEWAY=stripe` plus
`STRIPE_SECRET_KEY` to use the real Stripe integration.

## End-to-end flow

1. Register as a **vendor** → create a vendor profile (`/vendor`) → an admin
   approves it → list a product (auto-creates a stock row).
2. Register as a **customer** → browse/search → add to cart (validated against
   inventory) → checkout.
3. Checkout atomically **reserves stock → charges → persists the order**, then
   asynchronously sends a confirmation email and records a recommendation
   signal. Stock is permanently decremented; the cart is closed.
4. The nightly Celery `train_model` job trains the PyTorch recommender from
   captured interactions and refreshes per-user recommendation caches.

### Example requests

```bash
# Register, then log in to get a JWT access token
curl -X POST localhost:8000/api/v1/auth/register/ \
  -H 'Content-Type: application/json' \
  -d '{"email":"buyer@example.com","password":"Sup3r-Secret-99"}'

TOKEN=$(curl -s -X POST localhost:8000/api/v1/auth/token/ \
  -H 'Content-Type: application/json' \
  -d '{"email":"buyer@example.com","password":"Sup3r-Secret-99"}' | jq -r .access)

# Browse the catalog (public) and add an item to the cart (authenticated)
curl localhost:8000/api/v1/catalog/products/
curl -X POST localhost:8000/api/v1/cart/items/ \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"product_id": 1, "quantity": 2}'

# Checkout
curl -X POST localhost:8000/api/v1/orders/checkout/ \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"payment_token": "tok_visa"}'
```

## Security hardening (Phase 3)

- **Auth:** Argon2 password hashing, JWT access/refresh with rotation +
  blacklist-on-logout, role-based + object-level permissions.
- **Rate limiting:** DRF throttling — `anon` 60/min, `user` 1000/min, and a
  strict `10/min` scope on login/registration to deter brute force (tunable via
  `THROTTLE_*` env vars).
- **Transport/headers:** HTTPS redirect + HSTS, secure cookies, `nosniff`,
  `X-Frame-Options: DENY`, referrer policy, COOP (production settings); gzip
  compression; nginx security headers on the SPA.
- **Input/DB safety:** every endpoint validates via DRF serializers; the ORM and
  parameterized SQLAlchemy queries prevent SQL injection.
- **Secrets:** only via environment / AWS Secrets Manager — none in code;
  `.env` is git-ignored and excluded from Docker images via `.dockerignore`.
- **Dependencies:** audited with `pip-audit` + `npm audit` (CI gates). Every
  advisory with an available fix is patched — Django 5.0→5.2.15 LTS, DRF,
  SimpleJWT, sentry-sdk, torch, and the frontend toolchain (vite/esbuild). One
  residual stands: torch `CVE-2025-3000` in `torch.jit.script` — **unused by
  this codebase** (the recommender uses `nn.Module` only), local-only, and
  unpatched upstream; ML training runs in an isolated, non-web-facing worker.

## Troubleshooting

| Symptom | Cause / Fix |
|---------|-------------|
| `docker compose up` fails: port already in use | Postgres/Redis/ES ports (5432/6379/9200) are taken. Stop the local service or change the published port in `docker-compose.yml`. |
| `django.db.utils.OperationalError: could not connect` | DB not ready yet. The compose `web` service waits on a healthcheck; if running bare, ensure `DATABASE_URL` points at a running Postgres. |
| `relation "..." does not exist` | Migrations not applied — run `docker compose exec web python manage.py migrate`. |
| Elasticsearch container exits / search returns `503 SEARCH_UNAVAILABLE` | ES needs ≥512 MB and (on Linux) `vm.max_map_count=262144`: `sudo sysctl -w vm.max_map_count=262144`. The API degrades gracefully (503) when ES is down. |
| Recommendations always show "popularity_fallback" | Expected until the nightly `train_model` Celery job has run and a checkpoint exists; cold-start users also fall back by design. |
| 401 on a protected endpoint | Access token expired (15 min TTL). The frontend auto-refreshes; for raw curl, mint a new token via `/auth/token/refresh/`. |
| `429 Too Many Requests` | Rate limit hit. Raise `THROTTLE_*` env vars for local load testing. |
| Tests can't find Postgres | They don't need it — `pytest` uses `config.settings.test` (in-memory SQLite). |
| `pip install` is slow (torch) | `torch` is large; for backend-only API work you can install everything except torch — recommendation ML imports are lazy and degrade gracefully. |

## Layout

```
backend/     Django project (config/) + domain apps (apps/) + tests/
frontend/    React + TypeScript SPA (pages, components, hooks, store, api)
infrastructure/  Terraform modules/environments + Docker assets
.github/workflows/  CI + staging/production CD pipelines
scripts/     Deploy/build helper scripts
docs/        Architecture & engineering docs
```
