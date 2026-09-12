# Social Networking Platform

Rails 7 and React social networking platform with GraphQL as the primary API, REST compatibility endpoints, PostgreSQL, Redis feed caching, Elasticsearch search, Sidekiq jobs, and a local rules-based toxicity classifier with support for a remote classifier endpoint.

## Requirements

- Docker and Docker Compose
- Ruby 3.3 for local backend development outside Docker
- Node.js 22 for local frontend development outside Docker

## Run The Full Stack

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Services:

- Frontend: `http://localhost:5173`
- Rails API: `http://localhost:3000`
- GraphQL endpoint: `http://localhost:3000/graphql`
- Health check: `http://localhost:3000/health`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- Elasticsearch: `http://localhost:9200`

The backend container runs `rails db:prepare` before booting the API, so migrations are applied automatically for local Docker runs.
It also seeds a demo account for local smoke tests:

- Email: `demo@example.com`
- Password: `password123`

## Run Tests

Backend:

```powershell
docker compose run --rm -e RAILS_ENV=test -e DATABASE_URL=postgres://social:social@postgres:5432/social_test backend sh -c "bundle exec rails db:prepare && bundle exec rspec"
```

Frontend:

```powershell
docker compose run --rm frontend npm test -- --run
```

Full local validation:

```powershell
docker compose run --rm backend bundle exec rubocop
docker compose run --rm -e RAILS_ENV=test -e DATABASE_URL=postgres://social:social@postgres:5432/social_test backend sh -c "bundle exec rails db:prepare && bundle exec rspec"
docker compose run --rm frontend npm run lint
docker compose run --rm frontend npm run typecheck
docker compose run --rm frontend npm test -- --run
docker compose build
```

## Core Features

- Register, login, refresh-token backed session rotation, and bearer-token API auth.
- CRUD endpoints for users, posts, follows, messages, notifications, and toxicity results.
- GraphQL queries and mutations for dashboard data, feed, posts, follows, messages, notifications, and search.
- Redis-backed feed fanout with PostgreSQL fallback.
- Sidekiq jobs for search indexing and notifications.
- Elasticsearch-backed search with PostgreSQL fallback.
- Post and message toxicity scoring with model version storage.
- React UI for auth, feed composer, user discovery, search, messaging, and notifications.

## API Documentation

See [docs/API.md](C:/mygit/JavaRelated/29Items-Projects/16Ruby/2Social-Networking-Platform/docs/API.md).

## Deployment

The GitHub Actions workflow runs backend lint/tests, frontend lint/typecheck/tests, Docker builds, and a guarded Azure deployment job. Azure Container Apps infrastructure is defined in [infrastructure/azure/container-apps.bicep](C:/mygit/JavaRelated/29Items-Projects/16Ruby/2Social-Networking-Platform/infrastructure/azure/container-apps.bicep).

Production containers expect secrets to be supplied by the environment. The Rails app refuses to boot in production when required secrets are missing or still set to placeholder values.

## Troubleshooting

- If Docker tests return `403` unexpectedly, make sure `RAILS_ENV=test` and the `social_test` database URL are passed to the test command. The root `.env.example` intentionally defaults to development for local app startup.
- If the frontend cannot reach GraphQL in Docker, check the `GRAPHQL_ENDPOINT` environment variable on the frontend container and `FRONTEND_ORIGIN` on the backend.
- If Elasticsearch is unavailable locally, search falls back to PostgreSQL `ILIKE`; indexing failures are logged and retried by Sidekiq.
