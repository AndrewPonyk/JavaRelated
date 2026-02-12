# Technical Notes: Development & Operations

## 3.1 CI/CD Pipeline Design

Our deployment process uses **GitHub Actions** and follows these key stages:

1. **Lint Phase (`lint`)**:
   - PHP Coding Standards: `pint --test`
   - Static Analysis: `phpstan analyse`
   - JS Linting: `npm run lint`

2. **Test Phase (`test`)**:
   - Unit Tests: `php artisan test --parallel --testsuite=Unit`
   - Feature Tests: `php artisan test --parallel --testsuite=Feature`
   - End-to-End Tests: Optional (e.g., Cypress/Dusk)

3. **Build Phase (`build`)**:
   - Docker Buildx builds `Dockerfile` for production.
   - Pushes image to ECR with tags `latest` and `SHA-$commit`.

4. **Deploy Phase (`deploy`)**:
   - Updates `terraform` infrastructure if needed.
   - Triggers ECS Rolling Update via CLI or Terraform output.

## 3.2 Testing Strategy
- **Unit Testing**: 
    - Goal: 80% coverage for core services/logic.
    - Framework: PHPUnit 10+.
    - Mocking: Mockery for repositories and external services.
- **Integration Testing**:
    - Goal: Test controller flows, DB interactions, and queued jobs.
    - Database usage: In-memory SQLite or separate MySQL test DB (via `RefreshDatabase`).
- **End-to-End (E2E)** (Optional):
    - Tools: Cypress or Laravel Dusk for critical UI flows (Login, Publish Article).

## 3.3 Deployment Strategy
- **Platform**: AWS Elastic Container Service (ECS) Fargate.
- **Orchestration**: Infrastructure-as-Code (Terraform) manages ECS clusters, Task Definitions, ALB, RDS, ElastiCache (Redis).
- **Container Strategy**: 
    - `php-fpm` container serves the app logic.
    - `nginx` container serves static assets and proxies PHP requests.
    - Multi-stage Docker builds to keep images small (Alpine based).
- **Secrets**: Injected via ECS Task Definition from AWS Secrets Manager/SSM Parameter Store.

## 3.4 Environment Management

Configs are managed via `.env` files locally and Environment Variables in cloud.

**Example `.env.example`** template provided in code stubs.

1. **Development (`local`)**:
    - `APP_ENV=local`
    - `APP_DEBUG=true`
    - Uses local Docker Compose services.

2. **Staging (`staging`)**:
   - Mirror of Prod.
   - `APP_ENV=staging`
   - Uses separate RDS/Redis instances.

3. **Production (`production`)**:
   - `APP_ENV=production`
   - `APP_DEBUG=false`
   - Optimized `opcache`.

## 3.5 Version Control Workflow

**Strategy: GitHub Flow (Simplified)**

1. **Main Branch (`main`)**: The production-ready code. Protected branch. Requires PR reviews.
2. **Feature Branches (`feature/xyz`)**: Created off `main`. 
   - Write code -> Push -> Create Pull Request (PR).
   - CI runs on PR.
   - Merged to `main` upon approval -> Triggers Deploy to Staging (auto) and Production (manual approval or tag based).
3. **Hotfix Branches (`hotfix/abc`)**: 
   - Created off `main` for critical bugs.
   - Verify -> Merge to `main`.

## 3.6 Common Pitfalls
1. **N+1 Query Problem**: Failing to eager load relationships (`with()`) in API resources.
   - *Fix*: Use `Model::preventLazyLoading(!app()->isProduction())`.
2. **Redis Serialization**: Storing complex PHP objects directly.
   - *Fix*: Cache only simple scalar data or JSON strings.
3. **Environment Caching**: Changing `.env` without clearing config cache (`php artisan config:cache`).
   - *Fix*: Always restart containers or clear cache on env changes.
4. **Elasticsearch Index Sync**: DB updates not propagating to search index.
   - *Fix*: Use Model Observers or Queue Jobs to update index on `saved`/`deleted` events.
