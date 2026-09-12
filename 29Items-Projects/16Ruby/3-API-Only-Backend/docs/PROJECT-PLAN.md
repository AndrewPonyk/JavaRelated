# Project Plan: API-Only Backend

## 1.1 Project File Structure

```text
/
├── app/                      # Rails application code (Backend)
│   ├── controllers/api/v1/   # API endpoints (versioned)
│   ├── models/               # Database models and associations
│   ├── services/             # Business logic and anomaly detection
│   └── jobs/                 # Background jobs (Sidekiq/Resque)
├── config/                   # Rails configuration
├── db/                       # Database migrations and schema
│   └── migrate/
├── docs/                     # Documentation (Architecture, Tech Notes, etc.)
├── frontend/                 # Mobile app or admin dashboard frontend (TypeScript)
│   └── src/
│       ├── components/       # Reusable UI components
│       └── api/              # API integration services
├── spec/                     # RSpec tests
├── .github/                  # CI/CD workflows
│   └── workflows/
├── Dockerfile                # Docker container definition
├── docker-compose.yml        # Local development environment
├── .env.example              # Example environment variables
└── .rubocop.yml              # Ruby linter configuration
```

## 1.2 Implementation Status

### Phase 1: Foundation (High Priority)
- [x] Initialize Rails API application (`rails new . --api -d postgresql`)
- [x] Configure PostgreSQL database connection
- [x] Set up Docker and `docker-compose` for local development (API, worker, DB, Redis, frontend)
- [x] Implement custom JWT authentication with token revocation
- [x] Setup RSpec, FactoryBot, and SimpleCov for testing
- [x] Add API documentation in `docs/API.md`
- [x] Create CI pipeline (GitHub Actions for RuboCop, RSpec, frontend build, Docker build)

### Phase 2: Core Features (Medium Priority)
- [x] Design and implement User model and DB schema
- [x] Build `/api/v1/auth` endpoints (register, login, logout, me)
- [x] Implement mobile data synchronization endpoints
- [x] Setup Redis and Sidekiq for background jobs processing
- [x] Build frontend scaffolding and API service integrations (TypeScript)

### Phase 3: Polish & Optimization (Lower Priority)
- [x] Implement API metrics collection (tracking request rates, error rates, latency)
- [x] Develop Anomaly Detection service to analyze metrics (identifying spikes/abnormalities)
- [x] Document Render deployment environment requirements
- [x] Add rate limiting (`rack-attack`)
- [x] Refine API documentation with detailed request/response schemas
