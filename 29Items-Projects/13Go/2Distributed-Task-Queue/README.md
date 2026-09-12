# Distributed Task Queue

Distributed Task Queue is a Go service composed of an HTTP API, a worker pool, and a scheduler. PostgreSQL stores durable task state, Redis stores operational queue state, NATS publishes lifecycle events, and Prometheus exposes metrics.

## Requirements

- Go 1.22+
- Docker and Docker Compose

## Local Development

```sh
cp .env.example .env
# Edit API_KEY and POSTGRES_PASSWORD in .env before starting the stack.
docker compose up --build
```

Open the development console at `http://localhost:8080`. Use the `API_KEY` value from your local `.env` file.

## API

All `/api/v1` endpoints require `X-API-Key`.

```sh
curl -s -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -H "X-API-Key: replace-with-local-api-key" \
  -d '{"type":"example.email","payload":{"recipient":"user@example.com"},"max_attempts":3}'
```

Routes:

- `POST /api/v1/tasks`
- `GET /api/v1/tasks?status=&limit=`
- `GET /api/v1/tasks/{id}`
- `PUT /api/v1/tasks/{id}`
- `POST /api/v1/tasks/{id}/cancel`
- `DELETE /api/v1/tasks/{id}`
- `GET /api/v1/tasks/{id}/attempts`
- `GET /api/v1/dead-letters`
- `POST /api/v1/dead-letters/{id}/requeue`
- `GET /api/v1/queue/stats`
- `GET /healthz`
- `GET /metrics`

More detail is in [docs/API.md](docs/API.md).

## Running Tests

```sh
go test ./... -race -coverprofile=coverage.out -covermode=atomic
go tool cover -func=coverage.out
```

## Binaries

```sh
go run ./cmd/api
go run ./cmd/worker
go run ./cmd/scheduler
```

## Operations

The API applies SQL migrations from `migrations/` when `AUTO_MIGRATE=true`. PostgreSQL is the durable source of truth, Redis owns queue movement, and NATS publishes task lifecycle events. Store production secrets in AWS Secrets Manager or SSM Parameter Store and inject them into ECS task definitions.

Back up RDS PostgreSQL with automated snapshots. Redis queue state is operational data; if Redis is restored from backup, reconcile task states from PostgreSQL before resuming workers.

## Troubleshooting

- `401 missing or invalid API key`: set `X-API-Key` to the `API_KEY` value from `.env`.
- API exits with `API_KEY is required`: define `API_KEY`; production values must be at least 24 characters.
- `426 https is required`: set `X-Forwarded-Proto: https` at the load balancer or disable `ENFORCE_HTTPS` for local development.
- API cannot connect to PostgreSQL in Docker: confirm `.env` contains matching `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB`, then recreate the stack with `docker compose down -v && docker compose up --build`.
- Tasks remain in `retrying`: ensure the scheduler process is running so due retry records are promoted back to the pending queue.
- Metrics are empty: hit `/metrics` on the API container and confirm Prometheus is scraping `api:8080`.
