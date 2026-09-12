# API Gateway & Service Mesh Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended GitHub Actions stages:

1. Lint
   - Run `go fmt`, `go vet`, `golangci-lint`, `npm run lint`, and TypeScript checks.
2. Test
   - Run Go unit tests with coverage.
   - Run frontend unit tests.
   - Run repository integration tests against disposable PostgreSQL when a database service is available.
3. Build
   - Build backend and frontend Docker images.
   - Generate protobuf clients in a deterministic job.
4. Scan
   - Run dependency vulnerability scans.
   - Run container image scans before publishing.
5. Publish
   - Push versioned images to Amazon ECR.
6. Deploy
   - Update Helm image tags in the GitOps repository or application path.
   - Let ArgoCD reconcile dev, staging, and production clusters.

## 3.2 Testing Strategy

### Unit Testing

- Backend: Go standard `testing` package plus `testify` for assertions where useful.
- Frontend: Vitest and React Testing Library.
- Target coverage: 80% for service and policy logic; handlers can be lower if covered by integration tests.
- Prioritize deterministic tests around route matching, validation, rate-limit decisions, and transform rules.

### Integration Testing

- Use Docker Compose for PostgreSQL and mock upstream services.
- Validate migrations from an empty database.
- Verify admin CRUD operations persist expected route policies.
- Verify gRPC clients handle deadlines, retries, and unavailable upstreams.
- Add Envoy contract tests before relying on sidecar behavior in production.

### End-to-End Testing

- Use Playwright for the React admin console.
- Keep E2E tests focused on critical workflows: login, create route, update policy, view metrics.
- Run full E2E suites against staging, not every pull request.

## 3.3 Deployment Strategy

- Deploy to AWS EKS with Helm charts managed by ArgoCD.
- Use separate namespaces for dev, staging, and production.
- Store images in Amazon ECR.
- Use AWS Load Balancer Controller for ALB ingress.
- Use Horizontal Pod Autoscaler and Pod Disruption Budgets for availability.
- Run PostgreSQL as Amazon RDS in production; local and development environments can use containerized PostgreSQL.
- Deploy Envoy as a sidecar or through the chosen service mesh control plane.
- Keep ML anomaly detection as a separate service so model updates do not require gateway redeploys.

## 3.4 Environment Management

- Keep non-secret defaults in config files.
- Use `.env.example` as the local developer contract.
- Use Kubernetes ConfigMaps for non-sensitive environment values.
- Use External Secrets Operator with AWS Secrets Manager for secrets.
- Use explicit environment names: `local`, `dev`, `staging`, and `prod`.
- Validate required configuration on process startup.

Example `.env.example` values are included at the repository root.

## 3.5 Version Control Workflow

Use trunk-based development with short-lived branches.

Rationale:

- Gateway and platform infrastructure changes need quick feedback and frequent integration.
- Feature branches should stay small enough for focused review.
- Release confidence should come from CI, image promotion, Helm values, and ArgoCD sync status.
- Use tags for production releases and immutable image versions.

Recommended conventions:

- `main` is always deployable.
- Branch names: `feature/<short-name>`, `fix/<short-name>`, `chore/<short-name>`.
- Pull requests require CI success and at least one review.
- Production deploys require an explicit promotion step or ArgoCD sync approval.

## 3.6 Common Pitfalls

- Blocking every gateway request on PostgreSQL route lookups will become a latency bottleneck.
- High-cardinality Prometheus labels such as raw user IDs or full URLs can overload metrics storage; the implementation uses route templates from Gin where available.
- Envoy retries can amplify outages if retry budgets and circuit breakers are not configured.
- JWT validation must handle key rotation without frequent request-path network calls.
- Rate limiting must be tenant-aware and distributed across pods.
- Request transformation can become unsafe if upstream allowlists and body size limits are missing.
- ML anomaly detection can generate noisy alerts without tenant, route, and deploy-event context.
- Local Docker Compose behavior can hide Kubernetes-specific issues around DNS, service accounts, probes, and resource limits.
- List endpoints are paginated with bounded `limit` and `offset` values to avoid unbounded API responses.
