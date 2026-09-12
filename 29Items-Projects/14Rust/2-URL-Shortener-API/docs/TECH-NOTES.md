# Technical Notes

## CI/CD pipeline design

Use GitHub Actions for pull requests and protected `main` deployments:

1. **Lint:** `cargo fmt --check`, `cargo clippy -- -D warnings`, dependency audit where available.
2. **Test:** unit tests plus integration tests using a fresh SQLite file per job.
3. **Build:** release binary and Docker image; tag immutable images with commit SHA.
4. **Deploy:** use the pinned official Railway CLI only after all checks and the protected production-environment approval. Embedded migrations run once during API startup before the listener accepts traffic.

Cache Cargo registry and build artifacts using a lockfile key. Production deployment must consume the already-built image, never rebuild a branch in the deploy step.

## Testing strategy

- Unit test domain validation, code generation, service decision paths, and error mappings with Rust’s built-in test framework. Target at least 80% meaningful coverage of service/domain code; do not chase coverage through framework glue.
- Use Actix test utilities for route-level integration tests. Each test should create a temporary SQLite database, run migrations, make HTTP calls, and remove no shared state.
- Test the redirect contract end-to-end: creation, redirect location/status, unknown code, invalid URL, collision retry, and visit-count behavior.
- Add `cargo nextest` for fast parallel test execution and `cargo llvm-cov` for coverage reports when the project becomes active.
- Run container smoke tests in CI against the built image. Use k6 or Grafana k6 Cloud for controlled redirect load tests before capacity changes.

## Deployment strategy

Build a small multi-stage Linux container: compile Rust in a builder image, then copy only the binary into a slim runtime image. Railway is the simplest initial target: configure the health endpoint, set secrets in its dashboard, mount persistent storage only for non-critical SQLite deployments, and deploy an immutable image. Fly.io is suitable when region placement and a persistent volume are required.

SQLite is not a good multi-instance production database. Start with one replica and a durable volume only for an early-stage service; migrate to managed PostgreSQL before enabling autoscaling or multi-region deployment. Back up the database and test restore procedures.

## Environment management

Keep configuration in environment variables. Local developers copy `.env.example` to `.env`; CI injects test values; Railway/Fly secrets hold staging and production values. Validate all required configuration at process startup. Keep safe defaults only for local development and use explicit deployment environment labels.

The included `.env.example` defines the expected variable names and non-secret sample values.

## Version-control workflow

Use GitHub Flow: short-lived feature branches, pull requests into protected `main`, required CI, and squash merges. This suits a small service with continuous delivery and avoids maintaining long-lived environment branches. Use annotated semantic-version tags for releases and conventional commits if automated release notes are desired.

## Common pitfalls

- Diesel is synchronous; do not execute database calls directly on Actix worker threads. Use a blocking boundary and a correctly sized pool.
- SQLite permits only one writer at a time. Visit increments can become a contention hotspot; use atomic SQL updates, then migrate to PostgreSQL or event aggregation when needed.
- Generating a code is not enough: enforce a unique database constraint and retry insertion on collision.
- Never redirect arbitrary schemes. Restrict inputs to normalized `http`/`https` URLs and set maximum input lengths.
- Redirect status matters: use 302/307 for mutable destinations; use 301/308 only when permanent browser caching is intended.
- Container filesystems are ephemeral on many hosts. SQLite requires a persistent volume and backup plan.
- Tests sharing a SQLite file become flaky under parallel execution; use one database per test.
