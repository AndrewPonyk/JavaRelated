# Distributed Task Queue - Project Plan

## 1. Project File Structure

This project is a Go-based distributed task queue with an HTTP API, a worker runtime, Redis-backed task state, NATS messaging, PostgreSQL persistence, and Prometheus metrics.

```text
.
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- cmd/
|   |-- api/
|   |   `-- main.go
|   |-- scheduler/
|   |   `-- main.go
|   `-- worker/
|       `-- main.go
|-- config/
|   `-- config.example.yaml
|-- deploy/
|   |-- ecs/
|   |   |-- service-task-definition.json
|   |   `-- worker-task-definition.json
|   `-- terraform/
|       |-- main.tf
|       |-- outputs.tf
|       `-- variables.tf
|-- docs/
|   |-- ARCHITECTURE.md
|   |-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
|-- internal/
|   |-- api/
|   |   |-- handlers.go
|   |   |-- middleware.go
|   |   `-- router.go
|   |-- config/
|   |   `-- config.go
|   |-- db/
|   |   `-- postgres.go
|   |-- logging/
|   |   `-- logger.go
|   |-- messaging/
|   |   `-- nats.go
|   |-- metrics/
|   |   `-- prometheus.go
|   |-- queue/
|   |   `-- redis_queue.go
|   |-- repository/
|   |   `-- task_repository.go
|   |-- service/
|   |   `-- task_service.go
|   |-- shutdown/
|   |   `-- shutdown.go
|   `-- worker/
|       |-- pool.go
|       `-- processor.go
|-- migrations/
|   `-- 000001_create_tasks.sql
|-- observability/
|   `-- prometheus/
|       `-- prometheus.yml
|-- pkg/
|   `-- task/
|       `-- task.go
|-- tests/
|   `-- integration/
|       `-- task_api_test.go
|-- web/
|   |-- components/
|   |   `-- TaskList.js
|   `-- static/
|       |-- index.html
|       `-- styles.css
|-- .dockerignore
|-- .editorconfig
|-- .env.example
|-- .gitignore
|-- .golangci.yml
|-- Dockerfile
|-- Makefile
|-- README.md
|-- docker-compose.yml
|-- go.mod
`-- go.sum
```

### Source Code Organization

- `cmd/api`: HTTP API process for task submission, lookup, cancellation, health checks, and metrics.
- `cmd/worker`: Worker process that consumes tasks from Redis/NATS and executes task handlers with goroutines and channels.
- `cmd/scheduler`: Optional process for delayed/retry task scheduling.
- `internal/api`: HTTP routing, handlers, validation, and API middleware.
- `internal/service`: Application use cases. This layer coordinates validation, persistence, queueing, and publishing.
- `internal/repository`: PostgreSQL access for durable task metadata and audit state.
- `internal/queue`: Redis queue implementation for pending, reserved, retry, and dead-letter task sets.
- `internal/messaging`: NATS publisher/subscriber integration for fanout and worker coordination.
- `internal/worker`: Goroutine worker pool, backpressure, task acknowledgement, retry orchestration, and graceful shutdown.
- `pkg/task`: Shared task contracts safe for use by external packages.
- `migrations`: SQL schema migrations for PostgreSQL.
- `web`: Lightweight static frontend for viewing and submitting tasks during development.

### CI/CD Structure

- `.github/workflows/ci.yml`: Lint, test, build, image build, and AWS ECS deployment workflow.
- `Dockerfile`: Multi-stage Go image for API, worker, and scheduler binaries.
- `docker-compose.yml`: Local PostgreSQL, Redis, NATS, Prometheus, API, and worker stack.
- `deploy/ecs`: ECS task definitions for API and worker services.
- `deploy/terraform`: Infrastructure configuration for ECS, RDS PostgreSQL, ElastiCache Redis, networking, ECR, IAM-adjacent service wiring, and observability foundations.

### Tool Configuration

- `.env.example`: Local and deployment environment variable template.
- `.golangci.yml`: Go linter configuration.
- `.editorconfig`: Cross-editor formatting defaults.
- `config/config.example.yaml`: Human-readable application config reference.
- `observability/prometheus/prometheus.yml`: Prometheus scrape configuration.

## 2. Implementation Checklist

### Phase 1: Foundation - High Priority

- [x] Define task lifecycle states and durable task schema.
- [x] Implement configuration loading from environment variables.
- [x] Add structured logging with request IDs and worker IDs.
- [x] Implement PostgreSQL connection pooling and migration workflow.
- [x] Implement Redis queue primitives: enqueue, reserve, ack, retry, dead-letter.
- [x] Implement NATS publisher/subscriber wrapper with reconnect handling.
- [x] Add API routes for task creation, retrieval, listing, update, deletion, cancellation, attempts, dead letters, and queue stats.
- [x] Add worker pool with bounded concurrency and graceful shutdown.
- [x] Add Prometheus metrics for queue depth, task latency, success count, failure count, and worker utilization.
- [x] Add local Docker Compose stack.

### Phase 2: Core Features - Medium Priority

- [x] Add retry policies with exponential backoff and max-attempt enforcement.
- [x] Add delayed task scheduling.
- [x] Add idempotency key support for task submission.
- [x] Add dead-letter queue inspection and requeue API.
- [x] Add per-task timeout and cancellation propagation.
- [x] Add task result storage with retention policy hooks through durable task result JSON.
- [x] Add integration-style HTTP tests and unit tests for service and worker behavior.
- [x] Add API-key authentication for API calls.
- [x] Add structured audit events for important task state transitions.
- [x] Add ECS deployment pipeline with environment-driven production rollout.

### Phase 3: Polish & Optimization - Lower Priority

- [x] Add worker autoscaling inputs based on queue depth and processing latency metrics.
- [x] Add Redis Lua scripts for atomic queue transitions.
- [x] Keep event subjects and request IDs ready for trace correlation across API-to-worker paths.
- [x] Add admin dashboard views for failed, running, delayed, and completed tasks.
- [x] Add test and coverage gates in CI for throughput-sensitive code paths.
- [x] Add schema migration execution at service startup and migration files in source control.
- [x] Add operational guidance in README and architecture notes.
- [x] Add cost-aware Terraform defaults for AWS-managed services.
- [x] Harden security headers and API-key secret injection.
- [x] Document backup and restore expectations in README.
