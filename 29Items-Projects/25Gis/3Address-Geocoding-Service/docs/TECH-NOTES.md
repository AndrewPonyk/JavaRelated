# Address Geocoding Service Technical Notes

## 1. CI/CD Pipeline Design

The GitHub Actions workflow should follow this order:

1. Lint backend with Ruff.
2. Test backend with pytest.
3. Type-check frontend with TypeScript.
4. Lint frontend with ESLint.
5. Build frontend with Vite.
6. Build Docker images for backend and frontend.
7. Deploy to development automatically from `main`.
8. Deploy to staging and production through protected environments.

Recommended promotion model:

- Pull request: lint, test, build only.
- `main`: deploy to development.
- Git tag: deploy to staging.
- GitHub protected environment approval: deploy to production.

## 2. Testing Strategy

### Backend

- Use `pytest` and `httpx.AsyncClient` for FastAPI route tests.
- Target at least 80 percent coverage for service and API layers.
- Mock Nominatim for unit tests; use contract-style integration tests for provider response parsing.
- Use disposable containers for PostGIS and Elasticsearch integration tests.
- Keep migration tests in CI so schema drift is caught early.

### Frontend

- Use Vitest and React Testing Library for component tests.
- Use Playwright for end-to-end tests covering lookup, autocomplete, validation errors, and empty states.
- Keep frontend API calls behind `src/lib/api.ts` to make tests straightforward.

### End-to-End

- Run E2E tests against Docker Compose in CI for release candidates.
- Seed a tiny address fixture set into PostGIS and Elasticsearch.
- Verify autocomplete latency and basic accessibility behavior.

## 3. Deployment Strategy

The first production target is AWS EC2 with Docker containers.

- Build immutable Docker images for backend and frontend.
- Run PostGIS and Elasticsearch as managed services when possible for production. Local Compose is for development.
- Terminate TLS at an AWS load balancer or Nginx on EC2.
- Run FastAPI with Uvicorn workers behind Nginx.
- Serve the React production bundle through Nginx; do not run the Vite dev server in production.
- Use systemd only as an EC2 host-level process supervisor when Docker Compose is not used.
- Store backups for PostGIS and Elasticsearch snapshots in S3.

## 4. Environment Management

Configuration should be environment-variable driven. The `.env.example` file documents required values and safe defaults for local development.

Rules:

- Commit only `.env.example`.
- Keep `.env`, `.env.production`, and secrets out of git.
- Validate configuration at application startup.
- Keep environment-specific infrastructure values in AWS Systems Manager Parameter Store or AWS Secrets Manager.

Important variables:

- `APP_ENV`
- `DATABASE_URL`
- `ELASTICSEARCH_URL`
- `NOMINATIM_BASE_URL`
- `API_CORS_ORIGINS`
- `ALLOWED_HOSTS`
- `API_KEY_HEADER_NAME`
- `LOG_LEVEL`

## 5. Version Control Workflow

Use GitHub Flow.

Rationale:

- The project can ship continuously with small pull requests.
- Environment promotion can be controlled by GitHub Actions environments.
- Long-lived feature branches add overhead and tend to hide integration issues.

Recommended practice:

- Branch from `main`.
- Open a pull request early.
- Require CI passing before merge.
- Use squash merges for clean history.
- Tag releases with semantic versions when deploying externally visible API changes.

## 6. Common Pitfalls

- Nominatim usage policies: public Nominatim instances have strict rate limits. Production should use a dedicated Nominatim deployment, a compliant provider, or aggressive caching.
- Address ambiguity: geocoding is probabilistic. Return confidence scores and match explanations instead of pretending every result is exact.
- Elasticsearch analyzer drift: index settings are part of application behavior. Version mappings and use aliases for migrations.
- PostGIS performance: spatial queries need GiST indexes and bounded search radii.
- Coordinate order mistakes: keep APIs explicit about latitude and longitude ordering.
- Unicode normalization: addresses need careful normalization for casing, accents, punctuation, and locale-specific rules.
- Observability gaps: provider latency and failures must be measured separately from API latency.
- Secrets in frontend builds: React environment variables are public once bundled. Never put backend secrets in frontend configuration.
