# Technical Notes

## 3.1 CI/CD Pipeline Design
- **Linting & Formatting:** `cargo fmt` & `cargo clippy` for Rust. `eslint` & `prettier` for TS.
- **Testing:** `cargo test` for unit tests.
- **Building:** Multi-stage Docker builds to produce minimal final images (e.g., using `alpine` or `scratch`).
- **Deploying:** GitHub Actions pushes images to Docker registry and triggers rolling deployments on Fly.io.

## 3.2 Testing Strategy
- **Unit Testing:** Write `#[test]` modules in Rust for all core parsers and business logic. Aim for 80%+ coverage on core crates.
- **Integration Testing:** Spin up temporary Postgres and Redis containers (via Testcontainers) to test Actix routes and Diesel repositories end-to-end.
- **E2E Testing:** Playwright or Cypress for the frontend control plane.

## 3.3 Deployment Strategy
- **Containerization:** All services dockerized.
- **Cloud Platform:** Fly.io for deploying the Rust edge nodes globally close to users (Anycast network).
- **Database:** Managed PostgreSQL (e.g., AWS RDS or Fly Postgres) to reduce ops overhead.

## 3.4 Environment Management
Configuration is strictly managed via Environment Variables (12-Factor App methodology).
- See `.env.example` for the template.
- Use `dotenvy` in Rust for local development.

## 3.5 Version Control Workflow
- **Trunk-based Development:** Developers merge small, frequent updates to `main`.
- Feature flags are used to hide incomplete features in production.

## 3.6 Common Pitfalls
- **Rust Compile Times:** Can be long. Use `sccache` in CI and locally to cache build artifacts.
- **Async Deadlocks:** Be careful when mixing blocking operations (like heavy ML inference or synchronous DB calls) in Tokio async tasks. Use `tokio::task::spawn_blocking` for CPU-heavy tasks.
- **Diesel and Async:** Diesel is currently synchronous. Use `deadpool-diesel` or `tokio::task::spawn_blocking` to avoid blocking the Actix workers.
