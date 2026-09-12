# API-Only Backend

Rails API backend for mobile/client data sync with JWT authentication, request metrics, anomaly detection, Sidekiq jobs, PostgreSQL, Redis, and a small React dashboard.

## Stack

- Ruby 3.3, Rails 7.1 API mode
- PostgreSQL 15 with UUID primary keys
- Redis and Sidekiq for background jobs
- RSpec, FactoryBot, SimpleCov, RuboCop
- React, TypeScript, Vite

## Run Locally

1. Copy environment defaults and replace placeholder values:

   ```bash
   cp .env.example .env
   ```

   `POSTGRES_PASSWORD` and `JWT_SECRET_KEY` are required by `docker-compose.yml`. Use a random JWT secret with at least 32 characters.

2. Start the full stack:

   ```bash
   docker-compose up --build
   ```

   If you change `POSTGRES_PASSWORD` after the Postgres volume has already been created, the database will still use the old stored password. For a local development reset, run:

   ```powershell
   ./scripts/reset-local-db.ps1
   ```

3. Open the services:

   - API health: http://localhost:3000/up
   - Frontend dashboard: http://localhost:5173

The Rails container runs `db:prepare` on startup. Seed data is available with `demo@example.com` and the `DEMO_PASSWORD` value from `.env` after running:

```bash
docker-compose exec app ./bin/rails db:seed
```

## Tests and Checks

```bash
docker-compose run --rm app bundle exec rspec
docker-compose run --rm app bundle exec rubocop
docker-compose run --rm frontend npm run build
```

RSpec enforces 70% minimum coverage through SimpleCov.

List endpoints accept `page` and `per_page` query parameters. `per_page` is capped at 100 and responses include a `meta` object with `page`, `per_page`, `total_count`, and `total_pages`.

## API Summary

All authenticated endpoints require:

```text
Authorization: Bearer <token>
Content-Type: application/json
```

### Authentication

- `POST /api/v1/auth/register` with `{ "user": { "email": "...", "password": "...", "password_confirmation": "..." } }`

  Windows curl:
  ```
  curl -X POST http://localhost:3000/api/v1/auth/register -H "Content-Type: application/json" -d "{\"user\":{\"email\":\"test@test.com\",\"password\":\"secret123\",\"password_confirmation\":\"secret123\"}}"
  ```
- `POST /api/v1/auth/login` with `{ "user": { "email": "...", "password": "..." } }`
- `DELETE /api/v1/auth/logout`
- `GET /api/v1/auth/me`

### Users

- `GET /api/v1/users`
- `POST /api/v1/users`
- `GET /api/v1/users/:id`
- `PATCH /api/v1/users/:id`
- `DELETE /api/v1/users/:id`

Users can only read, update, or delete their own user record.

### Sync

- `POST /api/v1/sync`
- `GET /api/v1/sync/status`
- `GET /api/v1/sync_items`
- `POST /api/v1/sync_items`
- `GET /api/v1/sync_items/:id`
- `PATCH /api/v1/sync_items/:id`
- `DELETE /api/v1/sync_items/:id`

Sync changes use this shape:

```json
{
  "changes": [
    {
      "collection_name": "notes",
      "record_id": "mobile-1",
      "payload": { "title": "Offline note" },
      "client_updated_at": "2026-05-04T12:00:00Z"
    }
  ],
  "since": "2026-05-04T11:00:00Z"
}
```

### Metrics and Anomalies

- `GET /api/v1/metrics`
- `POST /api/v1/metrics`
- `GET /api/v1/metrics/:id`
- `PATCH /api/v1/metrics/:id`
- `DELETE /api/v1/metrics/:id`
- `GET /api/v1/anomalies`
- `POST /api/v1/anomalies`
- `GET /api/v1/anomalies/:id`
- `PATCH /api/v1/anomalies/:id`
- `PATCH /api/v1/anomalies/:id/resolve`
- `DELETE /api/v1/anomalies/:id`

API requests are automatically recorded as `ApiMetric` rows. `AnomalyDetectionJob` checks recent metrics for elevated error rates and latency.

## Deployment

The Dockerfile builds a production Rails image. Required production environment variables:

- `DATABASE_URL`
- `REDIS_URL`
- `RAILS_MASTER_KEY`
- `JWT_SECRET_KEY`
- `CORS_ORIGINS`
- `RAILS_ASSUME_SSL`
- `RAILS_FORCE_SSL`

The GitHub Actions workflow runs database setup, RuboCop, RSpec, frontend build, and Docker image build on pull requests and pushes to `main`.

## Troubleshooting

- `Set POSTGRES_PASSWORD in .env`: copy `.env.example` to `.env` and replace the placeholder values.
- `password authentication failed for user "postgres"`: your existing Docker Postgres volume was initialized with a different password than `.env`. For disposable local data, run `./scripts/reset-local-db.ps1`. To preserve data, start the DB with the old password, change the Postgres user password with `ALTER USER postgres WITH PASSWORD '<new password>';`, then restart the app.
- `database does not exist`: run `docker-compose run --rm app ./bin/rails db:prepare`.
- Frontend cannot reach the API: confirm `VITE_API_BASE_URL` points to `http://localhost:3000` for local Docker.
- Docker bind mount permission issues on Windows: Compose runs Rails services as root for local development only; the production Docker image still uses the non-root `rails` user.
