# Static Analyzer Technical Notes

## CI/CD Pipeline

GitHub Actions runs four jobs:

1. Backend: install dependencies, Ruff lint, pytest with coverage threshold.
2. Frontend: install dependencies, ESLint, Vitest, Vite production build.
3. Analyzer: install LLVM/Clang packages, CMake configure/build, CTest smoke test.
4. Docker: validate Compose configuration and build backend/frontend images.

## Testing

- Backend unit and integration tests use `pytest`, FastAPI `TestClient`, and an isolated SQLite database.
- Coverage is enforced through `pyproject.toml` with an 80 percent minimum.
- Source analyzer unit tests cover fact extraction and rule evaluation.
- Frontend tests use Vitest, jsdom, React Testing Library, and user-event.
- Docker build validation is part of CI.

## Deployment

The default deployment unit is containers:

- `docker/Dockerfile.backend` installs Python dependencies and serves FastAPI through Uvicorn.
- `docker/Dockerfile.frontend` builds React assets and serves them through Nginx.
- `docker/nginx.conf` proxies `/api` requests from the frontend container to the backend service.
- `docker-compose.yml` at the repository root runs the full stack with a persistent SQLite volume.

## Environment Management

Runtime configuration is read through Pydantic settings:

- `DATABASE_URL`
- `RULES_CONFIG`
- `MIGRATIONS_PATH`
- `ANALYZER_BINARY`
- `MAX_SOURCE_BYTES`
- `CORS_ORIGINS`
- `MAX_PAGE_SIZE`
- `GZIP_MIN_SIZE`
- `ENFORCE_HTTPS`
- `ALLOWED_HOSTS`

Local defaults are in `.env.example`. Production environments should provide these values through platform configuration or secret management.

## Version Control

The recommended workflow is trunk-based development with short-lived branches:

- `main` remains releasable.
- `feature/<short-name>` carries small enhancements.
- `fix/<short-name>` carries focused bug fixes.

This fits the project because analyzer rules, solver behavior, API contracts, and frontend displays need frequent integration.

## Stack Pitfalls Addressed

- Compile database drift: the source scanner works without a compile database, while the external analyzer boundary accepts one when available.
- AST schema instability: facts include `schemaVersion`.
- Z3 overuse: only the simple feasibility rule invokes Z3.
- Path explosion: feasibility checks are local, bounded, and deterministic.
- Source-code sensitivity: the backend persists normalized facts and finding evidence, not raw source blobs.
- Custom rules: YAML rule packs are validated before execution.
