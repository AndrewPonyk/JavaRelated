# URL Shortener Service

Production-oriented URL shortener with a Go/Gin API, PostgreSQL persistence, Redis cache and rate limiting, QR code generation, analytics, and a React frontend.

## Features

- Create, inspect, update, list, and delete short URLs.
- Redirect short codes with PostgreSQL fallback and Redis hot URL cache.
- Track clicks with referrer, country, user agent, time buckets, and durable PostgreSQL rows.
- Generate PNG QR codes for every short URL.
- Optional API key protection for list, update, delete, and analytics routes.
- Redis-backed request rate limiting.
- Versioned SQL migrations with golang-migrate.
- Docker Compose local stack and Fly.io deployment configuration.

## Quick Start

```bash
cp .env.example .env
docker compose up --build
```

Services:

- API: `http://localhost:8080`
- Frontend: `http://localhost:5173`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`

Docker Compose starts PostgreSQL and Redis, runs database migrations, starts the API, and starts the Vite frontend.

## API

See [docs/API.md](docs/API.md).

Common requests:

```bash
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com","customCode":"example"}'
```

```bash
curl -i http://localhost:8080/example
```

## Configuration

Configuration is environment-driven. Start from `.env.example`.

- `PUBLIC_BASE_URL`: base URL used in API responses and QR codes.
- `DATABASE_URL`: PostgreSQL connection string.
- `API_DATABASE_URL`: PostgreSQL connection string used by containers inside Docker Compose.
- `REDIS_ADDR`, `REDIS_PASSWORD`, `REDIS_DB`: Redis connection.
- `ADMIN_API_KEYS`: comma-separated keys. When empty, protected endpoints are open for local development. In production, the API refuses to start without at least one key.
- `RATE_LIMIT_PER_MINUTE`: per-IP or per-key API rate limit.
- `CORS_ALLOWED_ORIGINS`: comma-separated frontend origins.

## Local Commands

Backend:

```bash
go mod tidy
go test ./cmd/... ./internal/... ./tests/integration
go test ./cmd/... ./internal/... ./tests/integration -cover
go run ./cmd/api
```

Frontend:

```bash
cd frontend
npm install
npm test
npm run lint
npm run build
```

Migrations:

```bash
DATABASE_URL="postgres://urlshortener:urlshortener@localhost:5432/urlshortener?sslmode=disable" ./scripts/migrate.sh up
```

PowerShell:

```powershell
$env:DATABASE_URL="postgres://urlshortener:urlshortener@localhost:5432/urlshortener?sslmode=disable"
./scripts/migrate.ps1 up
```

## Deployment

Set Fly.io secrets:

```bash
fly secrets set DATABASE_URL="postgres://..."
fly secrets set REDIS_ADDR="..."
fly secrets set REDIS_PASSWORD="..."
fly secrets set PUBLIC_BASE_URL="https://your-app.fly.dev"
fly secrets set CORS_ALLOWED_ORIGINS="https://your-frontend.example.com"
fly secrets set ADMIN_API_KEYS="replace-with-strong-key"
```

Deploy:

```bash
fly deploy
```

GitHub Actions runs backend tests, frontend tests/build, Docker build, and deploys to Fly.io on `main` when `FLY_API_TOKEN` is configured.

## Troubleshooting

- `docker compose up` cannot connect to Docker: start Docker Desktop and ensure the Linux engine is running.
- API exits with `invalid configuration`: check required environment variables. Production requires `ADMIN_API_KEYS` and explicit `CORS_ALLOWED_ORIGINS`.
- Frontend admin actions return `401` or `403`: enter a valid API key in the Admin access field, or leave `ADMIN_API_KEYS` empty for local-only development.
- Migration fails with dirty state: inspect the `schema_migrations` table and rerun the matching `down` or `force` command only after confirming the database state.
- Backend tests should target `./cmd/... ./internal/... ./tests/integration` so frontend `node_modules` is not scanned for unrelated Go packages.
