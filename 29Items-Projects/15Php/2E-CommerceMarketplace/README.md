# E-Commerce Marketplace

A multi-vendor e-commerce marketplace built as a **modular monolith** with
**Domain-Driven Design**, event sourcing for order history, Elasticsearch-backed
product search, and ML-driven fraud detection.

## Tech Stack

| Layer            | Technology                                         |
|------------------|----------------------------------------------------|
| Backend          | PHP 8.3, Symfony 7, Doctrine ORM                   |
| Persistence      | PostgreSQL 16 (write model + event store)          |
| Search           | Elasticsearch 8 (product read model)               |
| Messaging        | RabbitMQ (Symfony Messenger transport)             |
| Fraud Detection  | Python ML micro-service (FastAPI)                  |
| Frontend         | React 18, TypeScript 5, Vite                       |
| Deployment       | Azure Container Apps                               |
| CI/CD            | GitHub Actions                                     |

## Repository Layout

```
.
├── backend/        # Symfony 7 modular-monolith (DDD bounded contexts)
├── frontend/       # React + TypeScript SPA
├── ml-service/     # Python fraud-scoring micro-service
├── infra/          # Azure Bicep + Docker infra-as-code
├── .github/        # CI/CD workflows
└── docs/           # PROJECT-PLAN, ARCHITECTURE, TECH-NOTES
```

## Documentation

- [Project Plan](docs/PROJECT-PLAN.md) — structure, roadmap and TODO list.
- [Architecture](docs/ARCHITECTURE.md) — patterns, diagrams, data flow.
- [Technical Notes](docs/TECH-NOTES.md) — CI/CD, testing, deployment, pitfalls.
- [API Reference](docs/API.md) — every HTTP endpoint, auth and payloads.

## Features

- **Identity** — registration (customer/seller), JWT login, role hierarchy, `/me`.
- **Catalog** — public browse + product detail; seller CRUD with ownership checks.
- **Search** — Elasticsearch read model, projected from catalog events (CQRS).
- **Ordering** — event-sourced orders; history reconstructed by replaying events.
- **Payment** — idempotent capture via a PSP gateway port; fraud-hold gating; 402 on decline.
- **FraudDetection** — async ML risk scoring (fail-open), persisted assessments, admin review queue.
- **Vendor** — event-driven seller onboarding, per-seller commission ledger & dashboard.

## Quick Start (local)

```bash
cp .env.example .env
docker compose up -d            # postgres, rabbitmq, elasticsearch
make backend-install            # composer install + migrations
make frontend-install           # npm install
make dev                        # run backend + frontend + workers
```

The API is served at `http://localhost:8000`, the SPA at `http://localhost:5173`.
`make backend-install` also generates the JWT keypair (`config/jwt/*.pem`, git-ignored)
and creates the Elasticsearch index.

## Running tests

```bash
make test                                   # all suites

# or per service:
cd backend    && vendor/bin/phpunit         # PHPUnit: Unit, Integration, Functional
cd frontend   && npm run lint && npm run typecheck && npm test -- --run
cd ml-service && pytest -q
```

- **Backend** — `Unit` tests are pure (no I/O) and run anywhere; `Integration`
  and the authenticated `Functional` happy-paths require PostgreSQL/Elasticsearch
  (the docker-compose services, as in CI).
- **Frontend** — Vitest + Testing Library, with the API client mocked.
- **ml-service** — pytest on the scoring contract and the heuristic fallback.

## License

Proprietary — internal demonstration project.
