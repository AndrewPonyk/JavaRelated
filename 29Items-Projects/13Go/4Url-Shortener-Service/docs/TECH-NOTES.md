# URL Shortener Service Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended GitHub Actions stages:

1. Lint: `gofmt`, `go vet`, `golangci-lint`, frontend ESLint.
2. Test: Go unit tests, frontend unit tests, integration tests for PostgreSQL and Redis.
3. Build: Go binary, React static assets, Docker image.
4. Migration validation: run golang-migrate against an empty database and rollback at least one step.
5. Deploy: promote the same image through dev, staging, and production.

Production deployment should require environment approval and use Fly.io secrets instead of repository committed configuration.

## 3.2 Testing Strategy

### Unit Tests

- Use Go standard `testing` plus Testify for assertions and mocks.
- Target service layer coverage first because it owns business behavior.
- Keep handlers covered with table tests around validation and status mapping.
- Use deterministic short code generator tests to validate collision handling.

Suggested initial coverage target: 70 percent for service and handler packages, then raise after the core flow stabilizes.

### Integration Tests

- Test repository code against real PostgreSQL.
- Test Redis cache behavior against real Redis.
- Validate migrations from a clean database.
- Use Docker Compose locally and service containers in GitHub Actions.

### End-to-End Tests

- Use Vitest for component-level frontend behavior and Playwright for end-to-end browser flows:
  - Create short link.
  - Copy result.
  - Open redirect.
  - View analytics.
  - Render QR code.

## 3.3 Deployment Strategy

The API should be containerized and deployed to Fly.io. PostgreSQL can be Fly Postgres or an external managed PostgreSQL provider. Redis can be Upstash, Fly Redis-compatible service, or another managed Redis provider.

Deployment flow:

1. Build Docker image in CI.
2. Run tests and migration checks.
3. Deploy to Fly.io staging.
4. Run smoke tests.
5. Promote to production with manual approval.
6. Run migrations as a release command or one-off job before serving new code.

## 3.4 Environment Management

Configuration should be loaded from environment variables. Local development can use `.env`, but the runtime should not depend on a specific `.env` library in production.

See the root `.env.example` for the template. Required values:

- `APP_ENV`
- `HTTP_ADDR`
- `PUBLIC_BASE_URL`
- `DATABASE_URL`
- `API_DATABASE_URL`
- `REDIS_ADDR`
- `REDIS_PASSWORD`
- `REDIS_DB`
- `CORS_ALLOWED_ORIGINS`

Recommended separation:

- Development: `.env` and Docker Compose.
- Staging: Fly.io app secrets and isolated database/cache.
- Production: Fly.io app secrets, production database/cache, stricter CORS.

## 3.5 Version Control Workflow

Use trunk-based development with short-lived feature branches:

- `main` is always deployable.
- Pull requests should be small and tested.
- Feature flags protect incomplete behavior.
- Release tags identify deployed versions.

This is simpler than Gitflow for a service that should ship incrementally and avoid long-running branches.

## 3.6 Common Pitfalls

- Cache correctness: Redis entries must expire or be invalidated when a URL is updated or deleted.
- Analytics pressure: recording every click synchronously in PostgreSQL will become a bottleneck.
- Short code collisions: random IDs need retry logic and database uniqueness enforcement.
- Abuse risk: public shorteners attract spam and phishing. Rate limiting and reporting hooks should not be deferred too long.
- Migration drift: all schema changes must go through migrations, including indexes and extensions.
- CORS drift: frontend environment URLs must match backend CORS configuration.
- Fly.io networking: database and Redis addresses differ between local, staging, and production; avoid hardcoded hosts.
- QR codes: generating QR images on every request can become wasteful. Cache by code and style options when traffic grows.
