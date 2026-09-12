# Distributed Task Queue - Technical Notes

## 1. CI/CD Pipeline Design

The GitHub Actions pipeline should follow this order:

1. Lint: run `gofmt`, `go vet`, and `golangci-lint`.
2. Test: run unit tests first, then integration tests when service dependencies are available.
3. Build: compile API, worker, and scheduler binaries.
4. Containerize: build and tag Docker images.
5. Security scan: scan dependencies and container image.
6. Deploy dev: automatically deploy from the main branch.
7. Deploy staging: deploy after successful dev validation.
8. Deploy production: require manual approval and use immutable image tags.

The included `.github/workflows/ci.yml` runs format checks, vet, tests with an 80% coverage gate for the covered application packages, Docker image build, and an AWS ECS rollout when the required repository variables and AWS role secret are configured.

## 2. Testing Strategy

### Unit Testing

Use Go's built-in `testing` package with table-driven tests. Target high coverage for the service layer, worker retry logic, task state transitions, configuration loading, and validation code. A practical target is 80% coverage for domain and service packages, with lower coverage acceptable for thin adapters.

### Integration Testing

Use Docker Compose locally for full dependency testing. The committed integration-style tests run the HTTP API over the real router with in-memory adapters so CI can validate main flows without external services. Dependency-backed tests should verify the same flows against PostgreSQL, Redis, and NATS before large infrastructure changes:

- Task creation persists metadata and enqueues work.
- Workers reserve, complete, retry, and dead-letter tasks correctly.
- Cancellation changes state consistently.
- Duplicate idempotency keys do not create duplicate tasks.
- Redis and PostgreSQL remain consistent across worker restarts.

### End-to-End Testing

For API-level tests, use HTTP black-box tests against a running stack. For the lightweight development UI, use Playwright only if the UI becomes business critical. The main correctness risk is backend state consistency, so prioritize API and worker integration tests first.

## 3. Deployment Strategy

The production target is AWS ECS.

- Build one Docker image containing `api`, `worker`, and `scheduler` binaries.
- Run separate ECS services with different commands for API and worker roles.
- Put the API ECS service behind an Application Load Balancer.
- Run workers without public ingress.
- Use RDS PostgreSQL for durable storage.
- Use ElastiCache Redis for queue operations.
- Use managed NATS where available, or operate a private NATS cluster in ECS/EC2 with clear ownership.
- Scrape Prometheus metrics with AWS Managed Prometheus, self-hosted Prometheus, or an observability collector.

## 4. Environment Management

Configuration should come from environment variables in containers, with local defaults documented in `.env.example`. Avoid environment-specific code branches. Prefer one image promoted through dev, staging, and production with different runtime configuration.

Important environment values:

- `APP_ENV`
- `HTTP_ADDR`
- `DATABASE_URL`
- `REDIS_ADDR`
- `NATS_URL`
- `WORKER_CONCURRENCY`
- `SHUTDOWN_TIMEOUT`
- `METRICS_ENABLED`
- `API_KEY`
- `AUTO_MIGRATE`
- `ENFORCE_HTTPS`

## 5. Version Control Workflow

Use trunk-based development or GitHub Flow.

For this project, GitHub Flow is the pragmatic default:

- Keep `main` deployable.
- Use short-lived feature branches.
- Require pull request checks before merge.
- Squash merge for a clean history.
- Tag releases with immutable image versions.

Full Gitflow adds branch overhead that is usually unnecessary for a service with automated CI/CD and environment promotion.

## 6. Common Pitfalls

- Treating Redis as the durable source of truth. PostgreSQL should own durable task metadata.
- Reserving tasks non-atomically, which can cause duplicate processing or lost work.
- Starting unbounded goroutines under load.
- Ignoring graceful shutdown, causing ECS deployments to interrupt in-flight tasks.
- Publishing NATS events before durable state commits.
- Retrying terminal errors and filling the queue with work that can never succeed.
- Storing large payloads directly in PostgreSQL or Redis instead of object storage.
- Missing idempotency, causing duplicate task submissions during client retries.
- Scaling workers on CPU only instead of queue depth and task age.
- Logging task payloads that may contain sensitive data.
