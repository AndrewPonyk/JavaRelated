# Java Microservices CI/CD — Order Service

Complete CI/CD reference implementation for a Spring Boot microservice:
Maven build → JUnit 5 tests → JaCoCo coverage gate (≥ 80%) → SonarQube quality gate →
Docker image (ECR) → Trivy scan → ML deployment-risk score → Helm → ArgoCD (GitOps) →
AWS EKS with rolling staging deploys and gated production promotion (blue-green optional).

| | |
|---|---|
| Service | **order-service** — e-commerce Order API (REST, PostgreSQL, OpenAPI) |
| Runtime | Java 21, Spring Boot 3.5, PostgreSQL 16 |
| Delivery | GitHub Actions · ECR · Helm · ArgoCD · AWS EKS · Argo Rollouts (blue/green) |
| Observability | Actuator → Prometheus → Grafana dashboard + alert rules |

## What the project can do

All items below are implemented and verified (built, tested, run end-to-end). The only gap to a real
production deploy is filling in the AWS placeholders listed at the bottom of this file.

**Order Service (the product)**

1. **Create orders** via `POST /api/v1/orders` — the server computes the total (`BigDecimal`, price
   snapshot per line) and returns `201` with a `Location` header
2. **Retry-safe creation** — send an `Idempotency-Key` header; a retry with the same key returns the
   original order with `200` instead of creating a duplicate, race-safe under concurrent requests
3. **Fetch an order with its items** in one SQL query (`@EntityGraph`, no N+1)
4. **List orders** — paginated, sorted, filterable by `customerId`, page size hard-capped at 100
5. **Drive the order lifecycle** — `PATCH /{id}/status` enforces the state machine
   NEW→CONFIRMED→PAID→SHIPPED→DELIVERED; illegal jumps are rejected with `409`
6. **Cancel orders** (`DELETE`) — allowed only before payment; kept as a CANCELLED audit record
7. **Reject bad input cleanly** — every error (validation, malformed JSON, unknown enum, bad sort
   property, unknown id) is an RFC-7807 `application/problem+json` body with a field→message map;
   no stack traces or SQL ever leak to the caller
8. **Serve its own API docs** — OpenAPI 3.1 + Swagger UI, generated from code
9. **Serve a built-in ops console** at `/` — create/list orders from the browser, with proper
   loading/error states
10. **Enforce JWT auth when configured** — set the issuer URI and the API becomes an OAuth2 resource
    server (`orders:read` / `orders:write` scopes); without it, it runs open for local dev
11. **Persist to PostgreSQL with versioned migrations** — Flyway V1–V3 apply automatically on
    startup; schema drift kills the pod instead of corrupting data

**Operations built in**

12. **Kubernetes-ready health** — liveness/readiness probes (readiness gates on the DB) on an
    isolated management port `8081` that is never internet-routed
13. **Prometheus metrics out of the box** — request rate/latency histograms, JVM, connection pool;
    pre-built Grafana dashboard + 4 alert rules (error rate, p95, crash loops, pool saturation)
14. **Zero-downtime deploys** — graceful shutdown drains in-flight requests; rolling updates with
    `maxUnavailable: 0`

**Delivery platform**

15. **One-command local stack** — `docker compose up -d --build` runs PostgreSQL + the service;
    optional profiles add Prometheus/Grafana/SonarQube
16. **One-command quality gate** — `./mvnw verify`: Checkstyle + 62 tests + JaCoCo ≥ 80% line
    coverage (currently ~95%); `-Pintegration-tests` adds Testcontainers ITs against real PostgreSQL
17. **Full CI pipeline** (GitHub Actions) — lint → tests → Sonar quality gate → Docker build → push
    to ECR via OIDC (no stored AWS keys) → Trivy CVE scan → automatic staging deploy via GitOps commit
18. **ML deployment risk scoring** — every PR gets a risk score (0–1) from diff metrics, posted as a
    sticky PR comment; scores ≥ 0.70 block production promotion unless explicitly overridden
19. **Gated production releases** — manual workflow with risk gate + required human approval +
    release tagging; the ArgoCD prod app is deliberately manual-sync (two-key launch)
20. **Blue-green deployments on demand** — flip one Helm flag (`blueGreen.enabled`) and the chart
    renders an Argo Rollout with a preview service for verify-then-promote (or instant abort)
21. **Three documented rollback paths** — ArgoCD rollback, `git revert`, blue-green abort — with
    copy-paste commands in [docs/runbooks/rollback.md](docs/runbooks/rollback.md)
22. **Post-deploy smoke test** — one script validates availability, the full order flow and
    idempotency against any environment (JWT-aware via `AUTH_TOKEN`)

## Documentation

- [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) — repository layout + phased implementation roadmap
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — architecture, diagrams, data flow, security
- [docs/TECH-NOTES.md](docs/TECH-NOTES.md) — pipeline design, testing strategy, env management, pitfalls
- [tools/risk-score/README.md](tools/risk-score/README.md) — ML deployment risk scoring

## Quickstart (local)

Prereqs: Docker (that's all for option A; option B also wants JDK 21 — Maven comes via the wrapper).

**Option A — full stack in containers:**

```bash
docker compose up -d --build          # PostgreSQL + order-service (:8080 API, :8081 actuator)
MANAGEMENT_URL=http://localhost:8081 bash scripts/smoke-test.sh
# host port 5432 busy? POSTGRES_PORT=15432 docker compose up -d --build
```

**Option B — dev loop (DB in Docker, app from source):**

```bash
docker compose up -d postgres

cd services/order-service
./mvnw verify                         # checkstyle + unit tests + JaCoCo ≥80% gate
./mvnw verify -Pintegration-tests     # + Testcontainers PostgreSQL ITs (same command CI runs)
./mvnw spring-boot:run
```

Then open:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Ops console (demo frontend): <http://localhost:8080/>
- Health: <http://localhost:8081/actuator/health> · Metrics: <http://localhost:8081/actuator/prometheus>
  (actuator runs on the **management port 8081** — in Kubernetes it is cluster-internal, never behind the ALB)

Try the API (the `Idempotency-Key` header makes retries safe — same key ⇒ same order, 200 instead of 201):

```bash
curl -s -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" -d '{
  "customerId": "0f8a9c1e-2b3d-4e5f-8a9b-1c2d3e4f5a6b",
  "items": [{"sku": "SKU-1001", "productName": "Mechanical Keyboard", "quantity": 2, "unitPrice": 89.90}]
}' | jq
```

**Auth:** open out of the box (no issuer configured). Setting
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` (Helm ConfigMap / env) switches the API to a
JWT resource server — `orders:read` scope for GETs, `orders:write` for mutations. See ARCHITECTURE §2.5.

Optional local stacks: `docker compose --profile observability up -d` (Prometheus :9090, Grafana :3000
with the dashboard pre-provisioned) · `docker compose --profile quality up -d` (SonarQube :9000).

## Repository map

```
services/order-service/   Spring Boot service (code, tests, Dockerfile, Flyway migrations)
deploy/helm/              Helm chart (rolling default, blue-green flag, HPA/PDB/IRSA/ExternalSecrets)
deploy/argocd/            ArgoCD AppProject + staging/production Applications
.github/workflows/        ci.yml (build→gate→image→staging bump), cd-production.yml (gated promotion)
observability/            Grafana dashboard, Prometheus alert rules + local scrape config
tools/risk-score/         ML-based deployment risk score used by CI and the prod gate
config/checkstyle/        Shared lint rules (enforced in the Maven validate phase)
scripts/                  smoke-test.sh — post-deploy verification
```

## CI/CD at a glance

```
PR:    checkstyle ─ mvn verify (JaCoCo ≥80%) ─ Sonar gate ─ risk score (advisory)
main:  … ─ docker build → ECR (tag = SHA) ─ Trivy ─ bump values-staging.yaml ─ ArgoCD → staging
prod:  cd-production.yml → risk gate (<0.7) → human approval → bump values-production.yaml → ArgoCD sync
```

Rollback: `git revert` the values bump or `argocd app rollback` — images are immutable SHAs.
Operator commands: [docs/runbooks/rollback.md](docs/runbooks/rollback.md).

---
⚠ Placeholders to fill before first real deploy: ECR registry in `deploy/helm/order-service/values*.yaml`,
`repoURL` in `deploy/argocd/*.yaml`, GitHub variables `AWS_REGION`/`AWS_ROLE_ARN`/`SONAR_HOST_URL`
and secret `SONAR_TOKEN`. See the Phase 1 checklist in [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md).
