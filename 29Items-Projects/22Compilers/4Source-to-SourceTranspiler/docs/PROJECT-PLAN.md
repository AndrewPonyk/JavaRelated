# Source-to-Source Transpiler Project Plan

## 1.1 Project File Structure

This project is organized as a Rust workspace with focused crates for the parser, AST, transformation passes, source maps, incremental compilation, macro expansion, backend API, CLI, and WASM bindings. The web frontend consumes the WASM package and the backend API for persisted compile jobs.

```text
.
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- config/
|   |-- default.toml
|   `-- production.toml
|-- crates/
|   |-- backend-api/
|   |   |-- Cargo.toml
|   |   `-- src/
|   |       |-- lib.rs
|   |       |-- main.rs
|   |       |-- models/
|   |       |   `-- compile_job.rs
|   |       |-- routes/
|   |       |   `-- compile_jobs.rs
|   |       `-- services/
|   |           `-- compile_service.rs
|   |-- cli/
|   |   |-- Cargo.toml
|   |   `-- src/main.rs
|   |-- emitter/
|   |   |-- Cargo.toml
|   |   `-- src/lib.rs
|   |-- incremental/
|   |   |-- Cargo.toml
|   |   `-- src/lib.rs
|   |-- macros/
|   |   |-- Cargo.toml
|   |   `-- src/lib.rs
|   |-- parser/
|   |   |-- Cargo.toml
|   |   |-- build.rs
|   |   `-- src/
|   |       |-- language.lalrpop
|   |       `-- lib.rs
|   |-- sourcemap/
|   |   |-- Cargo.toml
|   |   `-- src/lib.rs
|   |-- transforms/
|   |   |-- Cargo.toml
|   |   `-- src/lib.rs
|   |-- transpiler-core/
|   |   |-- Cargo.toml
|   |   |-- src/
|   |   |   |-- ast.rs
|   |   |   |-- diagnostics.rs
|   |   |   |-- lib.rs
|   |   |   `-- pipeline.rs
|   |   `-- tests/
|   |       |-- golden_output.rs
|   |       `-- fixtures/simple.tsl
|   `-- wasm/
|       |-- Cargo.toml
|       `-- src/lib.rs
|-- docker-compose.yml
|-- docker/
|   |-- Dockerfile.api
|   |-- Dockerfile.frontend
|   `-- docker-compose.yml
|-- docs/
|   |-- API.md
|   |-- ARCHITECTURE.md
|   |-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
|-- frontend/
|   |-- index.html
|   |-- package.json
|   |-- public/.gitkeep
|   |-- src/
|   |   |-- api/client.ts
|   |   |-- components/TranspilePanel.tsx
|   |   |-- main.tsx
|   |   `-- vite-env.d.ts
|   |-- tsconfig.json
|   `-- vite.config.ts
|-- migrations/
|   `-- 001_create_compile_jobs.sql
|-- scripts/
|   |-- check.ps1
|   `-- dev.ps1
|-- .dockerignore
|-- .env.example
|-- .gitignore
|-- .rustfmt.toml
|-- Cargo.toml
|-- clippy.toml
|-- Makefile
`-- README.md
```

### Source Code Layout

- `crates/transpiler-core`: shared AST, diagnostics, compiler pipeline contracts, and stable data types serialized with Serde.
- `crates/parser`: LALRPOP grammar and parser wrapper for the TypeScript-like source language.
- `crates/emitter`: JavaScript emitter and source-map attachment point.
- `crates/transforms`: AST rewrite passes such as constant folding, dead code cleanup, and JavaScript lowering.
- `crates/macros`: compile-time macro expansion hooks and expansion context.
- `crates/incremental`: cache keys, dependency graph types, and rebuild invalidation primitives.
- `crates/sourcemap`: source-map segment model and JSON serialization.
- `crates/wasm`: `wasm-bindgen` interface for browser usage.
- `crates/backend-api`: Axum-based HTTP API for compile job CRUD and transpilation requests.
- `crates/cli`: command-line entry point for local transpilation and CI integration.
- `frontend`: Vite React client that calls the backend and can later load the WASM package directly.
- `migrations`: SQL schema migrations for persisted compile jobs and cache metadata.

### CI/CD Layout

- `.github/workflows/ci.yml`: canonical GitHub Actions pipeline for linting, testing, building, and Docker image validation.
- `docker/Dockerfile.api`: container build for the Rust backend API.
- `docker/Dockerfile.frontend`: container build for the Vite frontend.
- `docker/docker-compose.yml`: local development stack with API, frontend, and PostgreSQL.
- `scripts/check.ps1`: local quality gate mirroring CI.
- `scripts/dev.ps1`: local developer startup helper.

### Tools Configuration

- `.env.example`: environment template for API, database, logging, and frontend API URL.
- `.rustfmt.toml`: Rust formatting rules.
- `clippy.toml`: Clippy lint configuration.
- `.editorconfig`: editor baseline for indentation and newline behavior.
- `frontend/.prettierrc.json`: frontend formatting preferences.
- `frontend/tsconfig.json`: TypeScript compiler configuration.
- `frontend/vite.config.ts`: Vite dev server/build configuration.
- `.dockerignore`: container build context exclusions.
- `.gitignore`: generated artifacts, caches, secrets, and build outputs.

## 1.2 Implementation TODO List

### Phase 1: Foundation (high priority)

- [x] Finalize the AST model for declarations, functions, blocks, expressions, type annotations, and macro invocations.
- [x] Expand the LALRPOP grammar to cover the MVP TypeScript-like syntax.
- [x] Define diagnostic codes, source spans, and structured error output.
- [x] Implement the parser facade and golden tests for valid and invalid syntax.
- [x] Implement the initial JavaScript emitter with source-map segment tracking.
- [x] Add backend API health checks, compile job CRUD, artifact reads, and request validation.
- [x] Wire the frontend transpile panel to backend compile job APIs.
- [x] Add CI checks for formatting, linting, unit tests, workspace builds, and image builds.

### Phase 2: Core features (medium priority)

- [x] Implement transformation pass orchestration with deterministic pass ordering.
- [x] Add macro expansion context, deterministic built-in macro expansion, and recursion limits.
- [x] Add incremental compilation cache keys based on source content, compiler version, and options.
- [x] Persist compile jobs, diagnostics, emitted JavaScript, source maps, and cache metadata.
- [x] Add integration tests for parser, transforms, and emitter.
- [x] Add WASM bindings for browser-only transpilation.
- [x] Add API pagination and compile job status transitions.
- [x] Add Docker Compose stack for full-stack local development.

### Phase 3: Polish & optimization (lower priority)

- [x] Add focused tests for parser behavior, transform ordering, cache keys, source-map shape, and backend compile output.
- [x] Keep AST passes ownership-based to avoid unnecessary shared mutation.
- [x] Add source-map shape validation tests.
- [x] Add frontend source editor, compile history, diagnostics, output, and artifact views.
- [x] Document authentication and organization scoping as production extensions outside the single-user MVP.
- [x] Provide a containerized deployment baseline through Docker and CI image builds.
- [x] Provide CLI and WASM packages as workspace release targets.
- [x] Document parser, macro, and caching decisions in architecture notes.
