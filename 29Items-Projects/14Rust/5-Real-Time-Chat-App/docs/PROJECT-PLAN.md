# Real-Time Chat App — Project Plan

## 1. Project file structure

The repository is a small Rust workspace. The backend owns transport, application, and persistence concerns; `shared` owns the wire contract; and the frontend is a framework-free browser client served by the backend. This keeps the first deployment operationally simple while retaining boundaries that can be extracted later.

```text
.
├── .github/
│   └── workflows/
│       ├── ci.yml                    # Lint, test, audit, and container build
│       └── deploy.yml                # Fly.io staging/production deployment
├── backend/
│   ├── src/
│   │   ├── api/
│   │   │   ├── health.rs             # Liveness/readiness probes
│   │   │   ├── mod.rs                # HTTP router composition
│   │   │   ├── rooms.rs              # Room CRUD HTTP handlers and DTO validation
│   │   │   └── websocket.rs          # WebSocket upgrade and connection loop
│   │   ├── domain/
│   │   │   ├── mod.rs
│   │   │   └── room.rs               # Room domain model
│   │   ├── repositories/
│   │   │   ├── mod.rs                # Persistence boundary
│   │   │   └── postgres.rs           # SQLx/PostgreSQL implementation
│   │   ├── services/
│   │   │   ├── chat.rs               # Per-room broadcast hub and presence
│   │   │   ├── mod.rs
│   │   │   └── rooms.rs              # Room application service
│   │   ├── config.rs                 # Typed environment configuration
│   │   ├── error.rs                  # Unified application/API errors
│   │   ├── lib.rs                    # Application composition
│   │   ├── main.rs                   # Process startup and graceful shutdown
│   │   ├── state.rs                  # Dependency container
│   │   └── telemetry.rs              # Structured tracing initialization
│   ├── tests/
│   │   └── health_api.rs             # HTTP integration test
│   └── Cargo.toml
├── config/
│   ├── default.toml                  # Documented non-secret defaults
│   ├── development.toml              # Local overrides
│   └── production.toml               # Production policy
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PROJECT-PLAN.md
│   └── TECH-NOTES.md
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   └── chat-room.js          # Chat UI, room fetching, and UI states
│   │   ├── services/
│   │   │   └── chat-api.js           # HTTP and WebSocket client boundary
│   │   └── main.js                   # Browser entry point
│   ├── index.html
│   └── styles.css
├── migrations/
│   └── 0001_create_chat_schema.sql   # Users, rooms, memberships, messages
├── scripts/
│   └── check.ps1                     # Reproducible local validation
├── shared/
│   ├── src/
│   │   ├── lib.rs
│   │   └── protocol.rs               # Serde WebSocket request/event schema
│   └── Cargo.toml
├── .dockerignore
├── .env.example
├── .gitignore
├── Cargo.lock                        # Reproducible dependency resolution
├── Cargo.toml                        # Workspace manifest and shared dependencies
├── Dockerfile                        # Multi-stage production image
├── README.md                         # Setup, API, and operating instructions
├── clippy.toml                       # Clippy policy
├── deny.toml                         # Dependency/license/security policy
├── docker-compose.yml                # Local app and PostgreSQL
├── fly.staging.toml                  # Staging Fly.io settings
├── fly.toml                          # Production Fly.io settings
└── rustfmt.toml                      # Deterministic Rust formatting
```

The completed implementation extends that foundation with `api/auth.rs`, `api/users.rs`, expanded room/message/member handlers, `domain/{audit,message,user}.rs`, `services/{auth,rate_limit}.rs`, `docs/API.md`, migrations `0002` through `0004`, and real-database `api_flow`, `postgres_repository`, and `websocket_flow` integration suites. `rust-toolchain.toml` pins the MSRV. The original tree remains the architectural grouping rather than an exhaustive generated file listing.

Generated build output (`target/`), local environment files, coverage output, and editor state are ignored. Secrets never belong in `config/*.toml`; those files document policy and defaults, while credentials are injected through environment variables or Fly secrets.

## 2. Delivery boundaries

- **Browser client:** Lists rooms through JSON HTTP endpoints, opens one WebSocket per active chat view, renders presence and messages, and exposes explicit loading, empty, disconnected, and error states.
- **HTTP/WebSocket server:** Axum runs on Tokio, validates all boundary input, exposes room CRUD and probes, upgrades chat sessions, and performs graceful shutdown.
- **Application services:** Room operations and chat fan-out are transport-independent. Handlers do not issue SQL directly.
- **Persistence:** SQLx accesses PostgreSQL through a repository interface. Durable messages and room metadata use migrations checked into source control.
- **Shared contract:** Serde-tagged client commands and server events are centralized to prevent protocol drift.
- **Deployment:** A multi-stage image packages one Rust binary plus static frontend assets. Fly.io health checks gate rollouts.

## 3. Implementation status

### Phase 1 — Foundation (high priority)

- [x] Pin Rust 1.88 in `rust-toolchain.toml` and CI as the initial MSRV.
- [x] Run formatting, strict Clippy, real-database tests, an 80% line-coverage gate, audit/policy checks, release build, and image build in CI.
- [x] Supply isolated local/test PostgreSQL creation plus separate staging/production configuration; platform databases are provisioned per environment by the operator.
- [x] Apply forward migrations as a release command before production traffic.
- [x] Validate typed configuration at startup, including secure production invariants.
- [x] Add request IDs, structured JSON logs, panic containment, liveness, and database readiness probes.
- [x] Supply Fly staging/production templates, TLS enforcement, health checks, release migrations, and secret-driven configuration.
- [x] Use protected GitHub deployment environments and an explicit production workflow dispatch/approval boundary.

### Phase 2 — Core features (medium priority)

- [x] Add account registration/login with Argon2id password hashes and revocable expiring sessions.
- [x] Enforce room membership and owner/moderator authorization for every mutation/history path.
- [x] Persist chat messages, load stable cursor-paginated history, and use content-free soft-deletion tombstones.
- [x] Enforce database-backed client message idempotency without duplicate rebroadcast.
- [x] Implement bounded reconnect with jitter, deduplication, and history resume in the browser.
- [x] Add typing indicators and heartbeat-driven stale-connection cleanup.
- [x] Add per-user/IP rate limits and bounded connection/message/channel/body limits.
- [x] Add isolated real-PostgreSQL repository/API tests and a two-client real WebSocket test.
- [x] Delete rooms through foreign-key cascades and notify active subscriptions.
- [x] Add accessible live status/error announcements, keyboard-native controls, and responsive layouts.

### Phase 3 — Polish and optimization (lower priority)

The application is complete without horizontal fan-out. HTTP Brotli/gzip compression is enabled. Redis/NATS, TTL-based distributed presence, write batching, partitioning, and read replicas remain intentionally disabled without measured need; enabling them changes the documented single-machine consistency boundary. The extraction seams and measurements required for that decision are documented in `ARCHITECTURE.md` and `TECH-NOTES.md`.

Repository-delivered polish includes immutable administrative audit history, sender/moderator message editing and deletion, account settings UI, bounded list/history APIs, Argon2 concurrency limits, session-aware socket revocation, structured tracing fields, forward-compatible integrity migrations and indexes, response compression, strict security headers, consistent JSON rejections, abuse-resistant limits, responsive/accessible browser states, and dependency/security gates. Environment-specific SLOs, external penetration/load assessments, managed-database recovery drills, domains, alerts, and distributed infrastructure remain operator rollout activities because they require the target platform, credentials, traffic envelope, and organizational policy.

## 4. Definition of done

A feature is complete when its contract is documented, boundary inputs are validated, authorization is enforced, structured failure behavior exists, unit/integration tests cover its success and important failure paths, migrations are forward-compatible, dashboards/alerts can observe it, and CI produces a deployable immutable image. Production changes must be verified in staging and have a rollback path.
