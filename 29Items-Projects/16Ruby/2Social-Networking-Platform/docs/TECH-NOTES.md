# Technical Notes

## 3.1 CI/CD Pipeline Design

The GitHub Actions pipeline should run in this order:

1. Lint Ruby with RuboCop and TypeScript with ESLint.
2. Run backend unit, model, request, and GraphQL specs.
3. Run frontend type checks and component tests.
4. Build backend and frontend Docker images.
5. Push images to Azure Container Registry after merges to `main`.
6. Deploy to development automatically, staging after approval, and production through a protected environment.

## 3.2 Testing Strategy

Use RSpec for backend tests with factories and focused service specs. Target high coverage on authentication, authorization, moderation, feed generation, and GraphQL mutations. Coverage percentage is less important than protecting critical behavior, but 80% line coverage is a reasonable minimum gate once the project stabilizes.

Use request specs for REST compatibility endpoints and GraphQL integration specs for API contracts. Use dataloader and N+1 tests around feed and search resolvers.

Use Vitest and React Testing Library for frontend components. Use Playwright for end-to-end tests covering sign in, create post, follow user, search, send message, and notification flows.

## 3.3 Deployment Strategy

Package backend and frontend as separate containers. Deploy Rails API and Sidekiq workers as separate Azure Container Apps so they can scale independently. Use Azure Database for PostgreSQL, Azure Cache for Redis, and managed Elasticsearch/OpenSearch where possible.

The backend image should run database migrations as a controlled release step, not automatically on every app boot. The worker image can reuse the backend image with a different command.

## 3.4 Environment Management

Use environment variables for all runtime configuration. Development can load `.env`, while staging and production should use Azure Container Apps secrets and managed identities.

The root `.env.example` documents required settings:

```dotenv
RAILS_ENV=development
RAILS_LOG_LEVEL=info
DATABASE_URL=postgres://social:social@localhost:5432/social_development
REDIS_URL=redis://localhost:6379/0
ELASTICSEARCH_URL=http://localhost:9200
FRONTEND_ORIGIN=http://localhost:5173
GRAPHQL_ENDPOINT=http://localhost:3000/graphql
JWT_SECRET_KEY_BASE=replace-with-local-secret
SECRET_KEY_BASE=replace-with-local-secret-key-base
FORCE_SSL=false
TOXICITY_CLASSIFIER_URL=http://localhost:8080/classify
AZURE_CONTAINER_REGISTRY=
AZURE_RESOURCE_GROUP=
AZURE_CONTAINER_APP_ENV=
```

## 3.5 Version Control Workflow

Use GitHub Flow: short-lived feature branches, pull requests into `main`, required CI, and protected production deployment approvals. This fits a web platform with frequent incremental delivery better than long-running Gitflow release branches.

Use branch names such as `feature/feed-fanout`, `fix/graphql-auth`, and `chore/sidekiq-observability`.

## 3.6 Common Pitfalls

- GraphQL N+1 queries can quietly damage performance. Use dataloaders and query analysis early.
- Feed fanout can create write amplification. Keep Redis feed entries compact and rebuildable from PostgreSQL.
- Elasticsearch is eventually consistent. Avoid using it as the source of truth for authorization-sensitive data.
- Toxicity classifiers produce false positives and false negatives. Store scores, decisions, model versions, and allow moderation review.
- Sidekiq jobs must be idempotent because retries are normal.
- Redis cache invalidation should be explicit, versioned, and recoverable.
- Azure Container Apps scaling rules should differ for API and workers; HTTP traffic and queue depth are different signals.
- Vite frontend variables are normally compile-time values. This project writes `env.js` at frontend container startup so Azure can inject `GRAPHQL_ENDPOINT` without rebuilding static assets.
