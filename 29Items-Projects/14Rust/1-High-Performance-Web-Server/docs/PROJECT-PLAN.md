# Project Plan: High-Performance Web Server

## 1.1 Project File Structure (Code + CI + Tools)

```text
/
├── backend/                  # Rust/Actix-Web backend
│   ├── Cargo.toml            # Rust dependencies
│   ├── Dockerfile            # Backend container
│   ├── migrations/           # Diesel database migrations
│   ├── src/
│   │   ├── api/              # Actix-web handlers/controllers
│   │   ├── db/               # Diesel models and schema
│   │   ├── ml/               # ML-based cache prediction integrations
│   │   ├── services/         # Core business logic
│   │   └── main.rs           # Entry point
├── frontend/                 # TypeScript frontend (React/Next.js for dashboard/CDN management)
│   ├── package.json          # TS/JS dependencies
│   ├── Dockerfile            # Frontend container
│   ├── src/
│   │   ├── components/       # Reusable UI components
│   │   ├── pages/            # Next.js/React pages
│   │   └── utils/            # API clients, helpers
├── docs/                     # Project documentation
│   ├── PROJECT-PLAN.md       # Implementation phases and structure
│   ├── ARCHITECTURE.md       # System design
│   └── TECH-NOTES.md         # Developer guides and strategies
├── .github/                  # CI/CD pipelines
│   └── workflows/
│       └── ci.yml            # GitHub Actions config
├── .env.example              # Example configuration
└── docker-compose.yml        # Local development environment
```

## 1.2 Implementation TODO List

- [ ] **Phase 1: Foundation (high priority)**
  - [ ] Initialize Rust/Actix-web backend and TypeScript frontend projects.
  - [ ] Configure Docker and `docker-compose.yml` for local Postgres and Redis.
  - [ ] Setup Diesel ORM and run initial migrations for CDN metadata.
  - [ ] Configure GitHub Actions for linting, formatting (cargo fmt, clippy, eslint).

- [ ] **Phase 2: Core features (medium priority)**
  - [ ] Implement HTTP edge proxy routing using Actix-web.
  - [ ] Integrate Redis for distributed caching.
  - [ ] Setup WebSockets for real-time traffic monitoring and analytics.
  - [ ] Build basic frontend dashboard to visualize edge node metrics.

- [ ] **Phase 3: Polish & optimization (lower priority)**
  - [ ] Integrate ML-based cache prediction engine for pre-warming.
  - [ ] Optimize Rust application for memory safety and zero-copy data transfer.
  - [ ] Configure Fly.io deployment pipeline in GitHub Actions.
  - [ ] Load testing and latency tuning.
