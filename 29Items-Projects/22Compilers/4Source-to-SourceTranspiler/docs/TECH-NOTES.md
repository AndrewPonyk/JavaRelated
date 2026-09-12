# Source-to-Source Transpiler Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended GitHub Actions stages:

1. Lint: `cargo fmt --check`, `cargo clippy --workspace --all-targets -- -D warnings`, and TypeScript type checking.
2. Test: Rust unit tests, Rust integration tests, frontend tests, and SQL migration smoke checks.
3. Build: Rust workspace build, backend API binary, CLI binary, WASM package, and frontend production bundle.
4. Package: Docker images for the API and frontend.
5. Deploy: promote images through development, staging, and production with environment-specific secrets after the target platform is selected.

The pipeline should fail fast on formatting and linting, then run tests and builds in parallel where possible.

## 3.2 Testing Strategy

### Unit Testing

- Use Rust built-in tests for AST utilities, source-map generation, incremental cache key creation, macro expansion rules, and transformation passes.
- Use `insta` snapshots or golden-file testing for emitted JavaScript and diagnostics.
- Target high coverage for parser wrappers, transform passes, source maps, and cache invalidation logic.

### Integration Testing

- Test the full compiler pipeline from source text to JavaScript and source map.
- Add fixtures for syntax errors, semantic diagnostics, macro expansion, and optimization passes.
- Test backend routes with in-memory or disposable database fixtures.
- Verify migrations can run from an empty database and against the previous schema.

### End-to-End Testing

- Use Docker Compose smoke tests for the current MVP: API health, compile job creation, artifact listing, and frontend static serving.
- Add Playwright when browser automation becomes part of the release gate.
- Keep E2E tests focused on creating a compile job, viewing diagnostics, rerunning a cached compile, and deleting the job.

## 3.3 Deployment Strategy

Use containers for repeatable deployment:

- API: Rust binary in a slim runtime image.
- Frontend: static Vite build served by Nginx or the target platform static hosting layer.
- Database: managed PostgreSQL in production; Docker Compose PostgreSQL locally.
- WASM: built during frontend packaging and published as a versioned asset.

For production, prefer a managed container platform such as AWS ECS/Fargate, Azure Container Apps, Google Cloud Run, Fly.io, or Kubernetes if the organization already operates it. Keep the first deployment simple: API, frontend, PostgreSQL, observability, and managed secrets.

## 3.4 Environment Management

Use environment variables for runtime configuration and checked-in TOML files for non-secret defaults. Local development should start from `.env.example`.

Required template:

```dotenv
APP_ENV=development
RUST_LOG=info,backend_api=debug
API_HOST=0.0.0.0
API_PORT=8080
API_PUBLISHED_PORT=8080
FRONTEND_PUBLISHED_PORT=5173
POSTGRES_USER=transpiler
POSTGRES_PASSWORD=change-me-local-only
POSTGRES_DB=transpiler
DATABASE_URL=postgres://transpiler:change-me-local-only@postgres:5432/transpiler
CORS_ALLOWED_ORIGINS=http://localhost:5173
MAX_SOURCE_BYTES=1048576
INCREMENTAL_CACHE_DIR=.cache/transpiler
VITE_API_BASE_URL=http://localhost:8080
```

Configuration rules:

- `development`: verbose logs, local database, permissive local CORS.
- `staging`: production-like database, structured logs, realistic limits, test secrets.
- `production`: strict CORS, managed secrets, structured logs, rate limiting, and no debug payloads.

## 3.5 Version Control Workflow

Use trunk-based development with short-lived feature branches. This fits compiler work well because parser, AST, transform, and emitter changes often need to move together and should be integrated continuously.

Recommended workflow:

- Branch from `main` for focused changes.
- Open small pull requests with tests or fixture updates.
- Require CI passing before merge.
- Use feature flags for incomplete user-facing capabilities.
- Tag releases for CLI/WASM/API compatibility points.

## 3.6 Common Pitfalls

- LALRPOP grammar ambiguity can grow quickly; keep precedence rules explicit and add parser regression fixtures.
- Source spans are easy to lose during AST rewrites; preserve origin metadata through every transformation pass.
- Macro systems need recursion limits, hygiene rules, deterministic expansion order, and clear diagnostics.
- Incremental compilation caches become incorrect if compiler version, options, macro inputs, or dependencies are not included in the cache key.
- WASM bindings should avoid large unnecessary copies between JavaScript and Rust.
- Source-map generation should be tested with real browser/devtool expectations, not just JSON shape checks.
- API logs must not include full source code by default.
- Golden tests are useful, but brittle formatting churn can hide meaningful compiler behavior changes.
