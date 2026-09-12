# API Gateway & Service Mesh Project Plan

## Implemented Structure

```text
.
|-- .github/workflows/ci.yml
|-- backend/
|   |-- api/proto/routing.proto
|   |-- cmd/gateway/main.go
|   |-- internal/config
|   |-- internal/database
|   |-- internal/grpc
|   |-- internal/http
|   |-- internal/middleware
|   |-- internal/models
|   |-- internal/repository
|   |-- internal/services
|   `-- tests
|-- configs/
|-- deploy/
|   |-- argocd
|   |-- envoy
|   |-- helm/api-gateway
|   `-- k8s
|-- docs/
|-- frontend/
|-- migrations/
|-- scripts/
|-- docker-compose.yml
`-- README.md
```

## Implementation Status

### Phase 1: Foundation

- [x] Service boundaries represented by gateway, persistence, telemetry/anomaly scoring, and Envoy integration config.
- [x] Configuration loading validates required runtime settings.
- [x] PostgreSQL-backed repositories implemented for tenants, routes, and anomaly events.
- [x] Database schema migration runs at gateway startup and is also available under `/migrations`.
- [x] Protobuf contract defined for route lookup and telemetry submission.
- [x] Admin API-key auth and data-plane JWT validation implemented.
- [x] Fixed-window rate limiting implemented for global and route-specific controls.
- [x] Structured request logging and Prometheus metrics implemented.
- [x] React admin flows implemented for tenants, route CRUD, anomaly scoring, and anomaly history.
- [x] Backend and frontend Docker images implemented.
- [x] GitHub Actions quality gates run backend tests, frontend tests, builds, and Docker builds.

### Phase 2: Core Features

- [x] Header transformation rules are applied on proxied requests.
- [x] Route matching supports tenant, host, path prefix, and method.
- [x] Envoy static sidecar/bootstrap configuration is provided.
- [x] Anomaly feature scoring persists incident events when thresholds are exceeded.
- [x] Circuit-breaker and retry policy ownership is documented in Envoy/mesh configuration.
- [x] Unit and handler tests cover route CRUD, matching, and anomaly persistence.

### Phase 3: Operations

- [x] Helm and ArgoCD manifests are present for EKS deployment.
- [x] Readiness, liveness, and metrics endpoints support platform automation.
- [x] README and OpenAPI documentation describe setup and API usage.
- [x] The architecture documents match the implemented control-plane and data-plane responsibilities.
