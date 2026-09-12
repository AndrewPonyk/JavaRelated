# Technical Notes

## 3.1 CI/CD Pipeline Design

Our CI/CD pipeline uses GitHub Actions and is triggered on every PR to `main`:
1. **Linting:** RuboCop (Ruby) and ESLint (TypeScript/Frontend).
2. **Testing:** RSpec (Backend) and Vitest/build checks (Frontend). Ensure at least 70% backend coverage.
3. **Building:** Build Docker image to verify environment integrity.
4. **Deploying:** Auto-deploy to Staging upon merge to `main`. Manual approval to deploy to Production on Render.

## 3.2 Testing Strategy

* **Unit Testing:** RSpec for Models and Services. Target complex business logic (anomaly detection algorithm).
* **Integration Testing:** Request specs in RSpec to test API endpoints, ensuring correct status codes, JSON structures, and JWT authentication flows.
* **End-to-End (E2E):** Minimal E2E tests for critical user journeys (login, full data sync) using tools like Cypress if testing the frontend alongside the API.

## 3.3 Deployment Strategy

* **Platform:** Render (PaaS).
* **Containerization:** A `Dockerfile` is provided. Render will build and deploy the Docker container, ensuring parity between development and production environments.
* **Services on Render:** Web Service (Rails API), Background Worker (Sidekiq), Managed PostgreSQL, Managed Redis.

## 3.4 Environment Management

* **Development:** Uses `docker-compose` to spin up local Postgres and Redis. Relies on `.env` file (copied from `.env.example`).
* **Staging:** Mirror of production with scrubbed data. Uses Render's environment variable configuration.
* **Production:** Strict access controls. High availability database configuration.

## 3.5 Version Control Workflow

**GitHub Flow:**
1. Branch from `main` (e.g., `feature/user-auth` or `bugfix/sync-error`).
2. Commit changes and push to origin.
3. Open a Pull Request (PR).
4. CI runs checks (Linter, Tests).
5. Code Review by at least one peer.
6. Merge via "Squash and Merge" to keep the `main` history clean.

## 3.6 Common Pitfalls

* **N+1 Queries in API:** Very common in Rails APIs when serializing related data. Always use `.includes` or strict serializer definitions to eager load associations.
* **JWT Invalidation:** Since JWTs are stateless, immediate invalidation (e.g., user logout on all devices) requires a denylist stored in Redis, adding complexity.
* **Heavy Background Jobs:** Anomaly detection on metrics might become resource-intensive. Ensure batch processing or use specialized time-series databases if metrics volume grows significantly.
