# REST API Backend

A **marketplace / SaaS** REST API built with **PHP 8.3 · Laravel 11 · MySQL 8 · Redis 7**, fronted by a **Vue 3** SPA. Features token auth (Sanctum), **versioned & rate-limited** endpoints, **event-driven notifications**, **Swagger/OpenAPI** docs, and **user-behavior clustering** for personalization & recommendations. Ships to **DigitalOcean** via **GitHub Actions**.

---

## 📚 Documentation

| Doc | Contents |
| --- | --- |
| [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) | File structure & phased implementation TODO list |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Architectural pattern, Mermaid diagrams, data flow, scalability, security, error handling |
| [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) | CI/CD, testing, deployment, environment management, git workflow, pitfalls |

## 🧱 Stack at a glance

```
Vue 3 SPA  ──HTTPS/JSON──▶  Nginx ──▶ Laravel (php-fpm)
                                          │
                  ┌───────────────────────┼───────────────────────┐
                  ▼                        ▼                       ▼
             MySQL 8 (data)        Redis (cache/queue)     Queue workers
                                                            (notifications,
                                                             clustering)
```

## 🚀 Quick start — Option A (Docker, full stack)

```bash
# 1. Env files
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
cp .env.example .env

# 2. Boot the stack (php-fpm, nginx, mysql, redis, worker, scheduler, vite)
docker compose up -d --build

# 3. App key, migrate + seed, generate API docs
docker compose exec app php artisan key:generate
docker compose exec app php artisan migrate --seed
docker compose exec app php artisan l5-swagger:generate
```

| Surface | URL |
| --- | --- |
| API (v1) | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/api/documentation |
| Health probe | http://localhost:8080/up |
| Vue SPA (Vite) | http://localhost:5173 |

Seeded admin login: `admin@example.com` / `password`.

> If host port **8080** is already in use, set `NGINX_HTTP_PORT=8081` (or any free port) in the root `.env` before `docker compose up` — the compose file reads it (`"${NGINX_HTTP_PORT:-8080}:80"`) and defaults to 8080.

## 🚀 Quick start — Option B (no Docker, SQLite)

For a fast spin-up with no MySQL/Redis, the app runs on SQLite + in-process drivers:

```bash
cd backend
composer install
cp .env.example .env
# In .env set: DB_CONNECTION=sqlite, CACHE_STORE=array, QUEUE_CONNECTION=sync, SESSION_DRIVER=array
touch database/database.sqlite
php artisan key:generate
php artisan migrate --seed
php artisan l5-swagger:generate
php artisan serve            # → http://127.0.0.1:8000

# Frontend (separate terminal)
cd frontend && npm install && npm run dev   # → http://localhost:5173
```

## 🔌 API endpoints (v1)

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/auth/register` | – | Create account, returns token |
| POST | `/auth/login` | – | Exchange credentials for a token |
| GET | `/auth/me` | ✓ | Current user |
| POST | `/auth/logout` | ✓ | Revoke current token |
| GET / POST | `/products` | ✓ | List (paginated, filterable) / create |
| GET / PUT / DELETE | `/products/{id}` | ✓ | Show / update / delete (owner or admin) |
| GET / POST | `/orders` | ✓ | Order history / place an order (checkout) |
| GET | `/orders/{id}` | ✓ | Show an order (owner or admin) |
| POST | `/events` | ✓ | Record a behavioral signal (view / wishlist / add_to_cart) |
| GET | `/recommendations` | ✓ | Personalized recommendations (with cold-start fallback) |

## 🔌 Example requests

```bash
BASE=http://localhost:8080/api/v1

# Register → returns a bearer token
TOKEN=$(curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Ada","email":"ada@example.com","password":"password123","password_confirmation":"password123"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")

# List products
curl $BASE/products -H "Authorization: Bearer $TOKEN"

# Place an order (checkout) — emits ProductPurchased → telemetry + notification
curl -X POST $BASE/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"items":[{"product_id":1,"quantity":2}]}'

# Personalized recommendations
curl "$BASE/recommendations?limit=5" -H "Authorization: Bearer $TOKEN"
```

## ✅ Quality gates

```bash
# Backend
cd backend
composer lint     # Pint (PSR-12)
composer stan     # PHPStan / Larastan level 6
composer test     # PHPUnit (unit + feature)

# Frontend
cd frontend
npm run lint
npm run build
```

CI runs all of the above on every push/PR (`.github/workflows/ci.yml`); tagged releases deploy to DigitalOcean (`.github/workflows/deploy.yml`).

**Current status:** 49 tests / 166 assertions passing · **96% line coverage** · Pint clean · PHPStan level 6 clean · frontend lints & builds.

> **PHP note:** the project targets **PHP 8.3** (as the CI image and `composer.json` pin). It also runs on PHP 8.5; the only difference is cosmetic deprecation notices from Laravel 11's bundled config, which are filtered in `phpunit.xml` and don't occur on 8.3.

## 🛠️ Troubleshooting

| Symptom | Cause / Fix |
| --- | --- |
| `docker compose up` → `bind: address already in use` on `:8080` | Another process holds 8080. Set `NGINX_HTTP_PORT=8081` (or any free port) in the **root** `.env`, then re-run. |
| Build fails: `"/scripts/entrypoint.sh": not found` | The image builds from the **repo root** (`context: .`, `dockerfile: backend/Dockerfile`) so the root-level `docker/` and `scripts/` dirs are in context. Don't set the context to `./backend`. |
| `composer install` → `requires php >= 8.4.1` on PHP 8.3 | `config.platform.php` is pinned to `8.3.0` in `composer.json` so deps resolve 8.3-compatible. If you edited deps, run `composer update` to refresh the lock. |
| Swagger UI empty or 500 | Run `php artisan l5-swagger:generate` (or keep `L5_SWAGGER_GENERATE_ALWAYS=true` in dev). |
| API returns `429 Too Many Requests` | Rate limit hit (`auth` 5/min/IP, `api` 60/min/user). Honour the `Retry-After` response header. |
| Fresh Docker DB is empty | By design the container entrypoint does **not** migrate (avoids multi-replica races). Run `docker compose exec app php artisan migrate --seed` once. |
| Tests: `could not find driver` | Enable `pdo_sqlite`; the suite uses in-memory SQLite (`phpunit.xml`). |
| Login/register `422` on a valid-looking email | Email is validated with `email:rfc,strict`; malformed or look-alike addresses are rejected by design. |

## 🔒 Security notes

- Secrets live only in `.env` (gitignored); `.env.example` documents every variable. Passwords use Laravel's `hashed` cast (bcrypt). Auth is Sanctum bearer tokens; every write goes through Form Request validation + Policies; responses carry a uniform JSON error envelope with a `request_id`.
- **Dependency advisories** (`composer audit`): 3 are reported against `laravel/framework` 11.54.0 (the latest 11.x). One — *temporary signed-URL path confusion* — is **not applicable** (the app issues no signed URLs). The other two — *CRLF injection in the default `email` rule* — are mitigated at the input boundary via `email:rfc,strict`. The upstream fix ships in **Laravel 12.6x**; since 11.x is past its security window, upgrading the framework is the recommended next step for long-term support.

## 🗂️ Layout

```
backend/   Laravel 11 API (controllers · services · repositories · events)
frontend/  Vue 3 SPA (Vite, Pinia, axios)
docker/    Nginx + PHP production config
docs/      Architecture & engineering docs
.github/   CI/CD workflows
```

> Fully implemented marketplace API + SPA — no stub code. Domain logic (checkout with stock locking, event-driven telemetry & notifications, behavior-clustering recommendations) is real and test-covered.
