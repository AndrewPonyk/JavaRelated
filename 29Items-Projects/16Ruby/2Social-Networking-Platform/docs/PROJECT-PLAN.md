# Social Networking Platform Project Plan

## 1.1 Project File Structure

This project uses a Rails 7 backend as a modular monolith with GraphQL as the primary API, PostgreSQL for durable relational data, Redis for caching/feed fanout, Elasticsearch for search, Sidekiq for asynchronous work, and a React frontend.

```text
.
+-- .github/
|   `-- workflows/
|       `-- ci.yml                         # GitHub Actions pipeline
+-- backend/
|   +-- app/
|   |   +-- controllers/
|   |   |   +-- application_controller.rb
|   |   |   `-- api/
|   |   |       `-- posts_controller.rb    # REST compatibility CRUD
|   |   +-- graphql/
|   |   |   +-- social_network_schema.rb
|   |   |   +-- mutations/
|   |   |   `-- types/
|   |   +-- jobs/                          # Sidekiq-backed ActiveJob workers
|   |   +-- models/                        # Rails domain models
|   |   `-- services/                      # Application service objects
|   +-- config/
|   |   +-- application.rb
|   |   +-- database.yml
|   |   +-- routes.rb
|   |   +-- sidekiq.yml
|   |   `-- initializers/
|   +-- db/
|   |   `-- migrate/                       # PostgreSQL schema migrations
|   +-- spec/                              # RSpec tests
|   +-- Dockerfile
|   `-- Gemfile
+-- frontend/
|   +-- src/
|   |   +-- components/
|   |   |   `-- Feed.tsx                   # GraphQL data fetching component
|   |   +-- graphql/
|   |   |   `-- client.ts
|   |   +-- lib/
|   |   `-- types/
|   +-- Dockerfile
|   +-- nginx.conf                         # Production static asset server
|   +-- docker-entrypoint.sh               # Runtime frontend configuration
|   +-- index.html
|   +-- package.json
|   +-- tsconfig.json
|   `-- vite.config.ts
+-- infrastructure/
|   `-- azure/
|       `-- container-apps.bicep           # Azure Container Apps infrastructure
+-- scripts/
|   `-- smoke-test.ps1
+-- docs/
|   +-- ARCHITECTURE.md
|   +-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
+-- .env.example
+-- .eslintrc.cjs
+-- .gitignore
+-- .prettierrc.json
+-- .rubocop.yml
`-- docker-compose.yml
```

## 1.2 Implementation Checklist

### Phase 1: Foundation (High Priority)

- [x] Generate full Rails 7 app structure or merge the scaffold into a runnable Rails application.
- [x] Configure PostgreSQL, Redis, Elasticsearch, and Sidekiq locally through Docker Compose.
- [x] Add authentication with short-lived access tokens and refresh token rotation.
- [x] Implement GraphQL schema conventions, authentication context, query complexity limits, and error formatting.
- [x] Add base domain models: users, posts, follows, messages, notifications, toxicity results.
- [x] Add CI jobs for Ruby linting, TypeScript linting, backend tests, frontend tests, and Docker builds.
- [x] Add environment variable validation at boot.

### Phase 2: Core Features (Medium Priority)

- [x] Implement GraphQL queries and mutations for posts, follows, messages, notifications, and search.
- [x] Build Redis-backed feed fanout for followed users, with PostgreSQL fallback pagination.
- [x] Index posts into Elasticsearch asynchronously with database fallback search.
- [x] Integrate toxicity classifier service in post and message creation flows.
- [x] Add notification fanout jobs for follows, post creation, and direct messages.
- [x] Add frontend views for feed, composer, profile context, search, messaging, and notifications.
- [x] Add integration tests for GraphQL authorization and feed behavior.

### Phase 3: Polish & Optimization (Lower Priority)

- [x] Add structured logs for request IDs and asynchronous indexing/search failures.
- [x] Add PostgreSQL indexes for feed, follow, message, notification, and moderation lookups.
- [x] Add cache-friendly GraphQL client behavior through Apollo normalized caching.
- [x] Store classifier scores, labels, and model versions for moderation workflows.
- [x] Add API request validation, token authentication, ownership checks, and GraphQL complexity limits.
- [x] Add Azure Container Apps infrastructure with independent API, worker, and frontend scaling surfaces.
- [x] Add automated tests for critical backend and frontend user journeys.
