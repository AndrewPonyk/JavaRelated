# Source-to-Source Transpiler

Rust workspace for a TypeScript-like source-to-source transpiler. The app parses with LALRPOP, expands built-in macros, rewrites ASTs through deterministic passes, emits JavaScript, generates source-map metadata, persists compile jobs in PostgreSQL, and exposes CLI, API, WASM, and React frontend entry points.

## Features

- TypeScript-like MVP syntax: `let`, `const`, functions, returns, type annotations, calls, strings, booleans, arithmetic, and macro calls.
- Built-in macros: `identity!(expr)`, `debug!(expr)`, and `todo!()`.
- Optimizations: constant folding and dead-code trimming after `return`.
- Incremental cache keying by source, compiler version, and compile options.
- Database-backed compile job CRUD with generated artifact reads.
- React UI for compile history, source editing, options, diagnostics, output, and artifacts.

## Requirements

- Docker Desktop for the full stack.
- Rust toolchain if running workspace commands directly.
- Node.js 20+ if running the frontend directly.

## Run Full Stack

```powershell
cp .env.example .env
docker compose up --build
```

Services:

- Frontend: `http://localhost:5173`
- API: `http://localhost:8080`
- Health: `http://localhost:8080/health`
- PostgreSQL: `localhost:5432`

The checked-in `.env.example` is suitable for local Docker Compose. Change `POSTGRES_PASSWORD` before sharing a development environment.

## Local Development

Run frontend checks:

```powershell
cd frontend
npm ci
npm test
npm run build
```

Run Rust checks when the Rust toolchain is installed:

```powershell
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
cargo build --workspace
```

Run all local checks:

```powershell
.\scripts\check.ps1
```

## API

See [docs/API.md](docs/API.md).

Main endpoints:

- `GET /health`
- `GET /api/compile-jobs?limit=50&offset=0`
- `POST /api/compile-jobs`
- `GET /api/compile-jobs/{id}`
- `PUT /api/compile-jobs/{id}`
- `DELETE /api/compile-jobs/{id}`
- `GET /api/compile-jobs/{id}/artifacts`
- `GET /api/compile-jobs/{id}/artifacts/{artifactId}`
- `DELETE /api/compile-jobs/{id}/artifacts/{artifactId}`

## Main Modules

- `crates/transpiler-core`: AST, diagnostics, and pipeline contracts.
- `crates/parser`: LALRPOP grammar and parser facade.
- `crates/macros`: deterministic built-in macro expansion.
- `crates/transforms`: source-to-source rewrite passes.
- `crates/emitter`: JavaScript output and source-map attachment.
- `crates/sourcemap`: source-map data structures.
- `crates/incremental`: cache keys and memory cache implementation.
- `crates/backend-api`: Axum API for compile job CRUD and artifacts.
- `crates/cli`: local file transpilation.
- `crates/wasm`: browser-facing WASM bindings.
- `frontend`: Vite React client.

## Configuration

Start from `.env.example`. Important values:

- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`: PostgreSQL container settings.
- `DATABASE_URL`: PostgreSQL connection string for the API.
- `CORS_ALLOWED_ORIGINS`: comma-separated browser origins accepted by the API.
- `API_PUBLISHED_PORT`, `FRONTEND_PUBLISHED_PORT`: host ports used by Docker Compose.
- `MAX_SOURCE_BYTES`: source request size limit.
- `VITE_API_BASE_URL`: API URL used by the Vite frontend.
- `RUST_LOG`: Rust tracing filter.

For running the API directly on the host instead of inside Compose, use `localhost` in `DATABASE_URL`, for example `postgres://transpiler:<password>@localhost:5432/transpiler`.

## Database

Migrations live in `migrations/`. The API runs the checked-in migration at startup, and PostgreSQL also executes it when a fresh Docker volume is initialized.

## Troubleshooting

- Docker reports missing environment variables: create `.env` from `.env.example` or run `docker compose --env-file .env.example ...`.
- API cannot connect to PostgreSQL in Compose: use the service hostname `postgres` in `DATABASE_URL`, not `localhost`.
- Host API cannot connect to PostgreSQL: use `localhost` in `DATABASE_URL` and ensure the Compose `postgres` service is running.
- Port conflicts: change the published ports in `docker-compose.yml` or stop the process using `5173`, `8080`, or `5432`.
- If another process owns `8080`, set `API_PUBLISHED_PORT=18080` and `VITE_API_BASE_URL=http://localhost:18080`, then rebuild the frontend image.
- Docker engine access errors on Windows: start Docker Desktop and run the terminal with permission to access the Docker Desktop Linux engine.
