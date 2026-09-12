# Project Plan — Java Microservices CI/CD

**Project:** E-commerce Order Service with a complete CI/CD pipeline
**Stack:** Java 21 · Spring Boot 3.5 · Maven · JUnit 5 · Mockito · JaCoCo · SonarQube · PostgreSQL · Docker · Helm · Kubernetes (AWS EKS) · GitHub Actions · ArgoCD · Prometheus/Grafana
**Status:** Scaffold complete (all files below exist in this repository, most with working code, some as documented stubs marked `TODO`).

---

## 1.1 Project File Structure (Code + CI + Tools)

The repository is a **service monorepo**: each microservice lives under `services/<name>/` with its own
build, Dockerfile and test suite; deployment artifacts (Helm/ArgoCD), observability assets and shared
tooling live at the top level so they can be reused when the second service (e.g. `payment-service`)
arrives. CI/CD is GitOps-style: **GitHub Actions builds and publishes**, **ArgoCD deploys what Git says**.

```text
1-Java-Microservices-CICD/
├── claude-fable-5.txt                  # LLM marker file (empty, requested by task)
├── README.md                           # Entry point: quickstart, repo map, pipeline overview
├── .gitignore
├── .editorconfig                       # Consistent formatting across editors
├── .env.example                        # Template of every environment variable (never commit .env)
├── Makefile                            # Developer shortcuts: build / test / lint / run / helm-lint
├── docker-compose.yml                  # Local dev: PostgreSQL (+ optional SonarQube, Prometheus, Grafana)
├── sonar-project.properties            # For CLI scanner only; Maven analysis reads pom.xml
│
├── docs/
│   ├── PROJECT-PLAN.md                 # This file: structure + implementation roadmap
│   ├── ARCHITECTURE.md                 # Patterns, diagrams, data flow, security, scaling
│   ├── TECH-NOTES.md                   # Pipeline design, testing, deployment, env mgmt, pitfalls
│   └── runbooks/
│       └── rollback.md                 # Promotion procedure, rollback decision tree, blue-green ops
│
├── .github/
│   ├── CODEOWNERS                      # Review routing (platform vs. service ownership)
│   ├── pull_request_template.md        # Checklist: tests, coverage, migration compatibility
│   ├── dependabot.yml                  # Maven, Actions, Docker, pip update automation
│   └── workflows/
│       ├── ci.yml                      # PR + main: lint → test/coverage → Sonar → risk score → image → staging bump
│       └── cd-production.yml           # Manual promotion: risk gate → approval → prod values bump → tag
│
├── config/
│   └── checkstyle/
│       └── checkstyle.xml              # Lint rules enforced in the `validate` Maven phase
│
├── services/
│   └── order-service/                  # ← the microservice (one folder per service)
│       ├── pom.xml                     # Boot 3.5 parent, JaCoCo ≥80% gate, Checkstyle, Sonar, failsafe IT profile
│       ├── mvnw / mvnw.cmd / .mvn/     # Maven wrapper (pinned 3.9.9) — used by CI
│       ├── Dockerfile                  # Multi-stage: maven build → layered Spring Boot jar on JRE 21, non-root
│       ├── .dockerignore
│       └── src/
│           ├── main/
│           │   ├── java/com/example/orderservice/
│           │   │   ├── OrderServiceApplication.java
│           │   │   ├── api/                        # Web layer (thin)
│           │   │   │   ├── OrderController.java    # REST endpoints /api/v1/orders
│           │   │   │   ├── GlobalExceptionHandler.java  # RFC-7807 ProblemDetail responses
│           │   │   │   └── dto/                    # Request/response records + validation
│           │   │   ├── service/                    # Application layer
│           │   │   │   ├── OrderService.java       # Transactions, orchestration, idempotent create
│           │   │   │   ├── CreationResult.java     # created-vs-replayed outcome for the web layer
│           │   │   │   └── OrderMapper.java        # DTO ⇄ entity mapping (no framework magic)
│           │   │   ├── domain/                     # Rich domain model
│           │   │   │   ├── Order.java              # Aggregate root: total calc, status transitions
│           │   │   │   ├── OrderItem.java
│           │   │   │   └── OrderStatus.java        # State machine (NEW→…→DELIVERED / CANCELLED)
│           │   │   ├── repository/
│           │   │   │   └── OrderRepository.java    # Spring Data JPA + @EntityGraph (N+1 guard)
│           │   │   ├── exception/                  # Domain exceptions → mapped to 404/409
│           │   │   └── config/                     # OpenAPI, Clock, SecurityConfig (JWT / open fallback)
│           │   └── resources/
│           │       ├── application.yml             # Graceful shutdown, probes, metrics, profiles
│           │       ├── db/migration/               # Flyway (versioned, append-only)
│           │       │   ├── V1__create_orders_tables.sql
│           │       │   ├── V2__add_indexes.sql
│           │       │   └── V3__add_idempotency_key.sql
│           │       └── static/index.html           # Minimal ops/demo console (fetch + states)
│           └── test/
│               └── java/com/example/orderservice/
│                   ├── api/OrderControllerTest.java      # @WebMvcTest slice + MockMvc
│                   ├── api/SecurityFilterChainTest.java  # JWT chain: 401/403/scopes/public paths
│                   ├── service/OrderServiceTest.java     # Pure Mockito unit tests (incl. idempotency races)
│                   ├── service/OrderMapperTest.java
│                   ├── domain/OrderTest.java             # Domain rules
│                   ├── domain/OrderStatusTest.java       # Transition table
│                   └── it/OrderApiIT.java                # Testcontainers PostgreSQL (failsafe, -Pintegration-tests)
│
├── deploy/
│   ├── helm/
│   │   └── order-service/              # One chart per service, env deltas in values-*.yaml
│   │       ├── Chart.yaml
│   │       ├── .helmignore
│   │       ├── values.yaml             # Safe defaults (rolling update, probes, HPA, PDB)
│   │       ├── values-staging.yaml     # CI bumps image.tag here on every main build
│   │       ├── values-production.yaml  # Bumped only by the promotion workflow
│   │       └── templates/
│   │           ├── _helpers.tpl
│   │           ├── networkpolicy.yaml  # Opt-in default-deny (API/management/DNS/RDS/HTTPS)
│   │           ├── deployment.yaml     # Rolling strategy (default path)
│   │           ├── rollout.yaml        # Argo Rollouts Blue/Green (enabled per release when risk is high)
│   │           ├── service.yaml
│   │           ├── service-preview.yaml# Preview service for blue/green
│   │           ├── ingress.yaml        # AWS ALB ingress
│   │           ├── hpa.yaml
│   │           ├── pdb.yaml
│   │           ├── serviceaccount.yaml # IRSA annotation for AWS access
│   │           ├── configmap.yaml      # Non-secret env
│   │           ├── secret.yaml         # Dev-only; real envs use ExternalSecrets
│   │           ├── externalsecret.yaml # AWS Secrets Manager → K8s Secret
│   │           ├── servicemonitor.yaml # Prometheus Operator scrape config
│   │           ├── NOTES.txt
│   │           └── tests/test-connection.yaml
│   └── argocd/
│       ├── project.yaml                # AppProject: repo/namespace allow-list
│       ├── order-service-staging.yaml  # Auto-sync, prune, self-heal
│       └── order-service-production.yaml  # Manual sync (promotion is a human decision)
│
├── observability/
│   ├── prometheus/
│   │   ├── prometheus.yml              # Local compose scrape config
│   │   └── alerts/order-service-alerts.yaml   # PrometheusRule: error rate, p95, restarts, pool
│   └── grafana/
│       ├── provisioning/               # Auto-load datasource + dashboards in compose
│       │   ├── datasources/datasource.yml
│       │   └── dashboards/provider.yml
│       └── dashboards/order-service.json      # RED + JVM + Hikari dashboard
│
├── tools/
│   └── risk-score/                     # ML-based deployment risk scoring
│       ├── risk_score.py               # Feature extraction from git diff + coverage; logistic model
│       ├── train_model.py              # Trains on historical deploy outcomes (stub + TODOs)
│       ├── requirements.txt            # Optional deps; script degrades to stdlib-only
│       ├── data/deployments.sample.csv # Example training data shape
│       └── README.md
│
└── scripts/
    └── smoke-test.sh                   # Post-deploy health + create/read round-trip
```

### Why this layout

| Decision | Rationale |
|---|---|
| `services/<name>/` per microservice | Each service keeps its own `pom.xml`, Dockerfile and tests → independent build/deploy cadence; adding `payment-service` is copy-shape, not restructure. |
| Deployment config outside service code (`deploy/`) | Helm/ArgoCD files change on a different cadence than code and are owned by platform folks (see CODEOWNERS); CI only touches `values-*.yaml` image tags. |
| No frontend module | The stack is backend-only. The "frontend" deliverables are covered by Swagger UI (springdoc) and a dependency-free ops console at `src/main/resources/static/index.html`. A real storefront SPA would be a separate repo/service. |
| Migrations inside the service (`src/main/resources/db/migration`) | Flyway migrations version with the code that needs them and run on service startup (staging) / job (prod option). |
| Shared `config/` for lint rules | One Checkstyle ruleset for every current and future service. |

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority)
- [x] Repository scaffold, `.editorconfig`, `.gitignore`, README
- [x] Order Service skeleton: Boot 3.5 / Java 21, health probes, graceful shutdown
- [x] PostgreSQL integration + Flyway `V1`/`V2` migrations (`ddl-auto: validate`)
- [x] Domain model with status state-machine; REST CRUD + validation + ProblemDetail errors
- [x] OpenAPI docs via springdoc (`/swagger-ui.html`)
- [x] Unit tests (JUnit 5 + Mockito + @WebMvcTest) and **JaCoCo ≥ 80% line gate wired into `mvn verify`**
- [x] Checkstyle bound to `validate` phase
- [x] Multi-stage Dockerfile (non-root, JRE-only runtime)
- [x] CI workflow: lint → test/coverage → Sonar scan → risk score → image build/push (ECR, OIDC) → Trivy scan
- [ ] Create AWS prerequisites: ECR repo, EKS cluster, OIDC role for GitHub (`AWS_ROLE_ARN` repo variable) — *infra, outside this repo (Terraform recommended)*
- [ ] Install cluster add-ons: AWS Load Balancer Controller, ArgoCD, External Secrets Operator, kube-prometheus-stack, Argo Rollouts
- [x] Helm chart with rolling update, probes, HPA, PDB, IRSA, ExternalSecret
- [x] ArgoCD `AppProject` + staging `Application` (auto-sync) — apply `deploy/argocd/*.yaml` to the cluster
- [ ] First end-to-end staging deploy; run `scripts/smoke-test.sh` against staging URL

### Phase 2 — Core features (medium priority)
- [x] Production ArgoCD `Application` (manual sync) + `cd-production.yml` promotion workflow with GitHub *environment approval*
- [x] Deployment risk score (`tools/risk-score`) wired into CI (sticky PR comment) and the prod gate
- [x] Prometheus `ServiceMonitor`, alert rules, Grafana dashboard (RED + JVM + Hikari)
- [ ] Enforce SonarQube quality gate as a required PR status check (branch protection) — *GitHub settings, outside the repo*
- [x] Run Testcontainers ITs in CI (`./mvnw verify -Pintegration-tests` in `ci.yml`; PATCH covered via httpclient5)
- [x] Structured JSON logging (ECS) in the `production` profile — shipping to CloudWatch/Loki is a cluster add-on concern
- [x] Idempotency keys on `POST /orders` — `Idempotency-Key` header, partial unique index (`V3`), race-safe replay, covered by unit + IT + smoke tests
- [x] AuthN/AuthZ: Spring Security OAuth2 Resource Server (JWT) — enforced when `issuer-uri` is configured per environment; open fallback chain for local/tests (`SecurityConfig`, `SecurityFilterChainTest`)
- [x] Maven wrapper (`mvnw`/`mvnw.cmd`, Maven 3.9.9 pinned) — used by CI
- [x] Rollback & promotion runbook (`docs/runbooks/rollback.md`); [ ] game-day rehearsal on a real cluster

### Phase 3 — Polish & optimization (lower priority)

**Production-polish audit (completed 2026-07-12):**
- [x] Dependency refresh: Spring Boot 3.5.9, JaCoCo 0.8.13
- [x] Actuator isolated on a management port (8081): probes/ServiceMonitor/ALB health check retargeted; never ingress-routed
- [x] Opt-in default-deny `NetworkPolicy` chart template
- [x] Readiness gates on the database (`readinessState,db`); liveness deliberately does not
- [x] Error-response hardening: malformed JSON / unknown enum / bad sort property / oversized header → RFC-7807 `400` (were `500`)
- [x] Input caps: ≤100 items per order, sku/productName lengths mirror DB columns, `Idempotency-Key` ≤64 chars, page size ≤100
- [x] `Locale.ROOT` currency normalization (Turkish-locale bug), currency validated as `[A-Za-z]{3}`
- [x] HTTP response compression (JSON ≥1 KiB)
- [x] Smoke test: JWT-aware (`AUTH_TOKEN`), management-port-aware, availability via public `/v3/api-docs`

**Remaining (needs cluster/data/scale):**
- [ ] Blue-green in production via Argo Rollouts for releases with risk ≥ 0.6 (chart flag `blueGreen.enabled` exists; install Rollouts controller, rehearse promote/abort)
- [ ] Train the risk model on real deployment history (`train_model.py`, replace heuristic weights; collect labels from Argo notifications)
- [ ] Performance tests (k6/Gatling) as a nightly workflow with trend reporting
- [ ] Alert routing to Slack/PagerDuty via Alertmanager
- [ ] Transactional outbox + event publishing (Kafka/SNS) for `OrderCreated` — unlocks the next microservice
- [ ] Contract tests (Spring Cloud Contract / Pact) once a second service consumes the API
- [ ] Caching layer (Redis) for hot reads; read-replica datasource routing
- [ ] Cost: Karpenter + Spot for stateless pods; right-size via VPA recommendations
- [ ] Table partitioning for `orders` by month once volume warrants; archive policy
- [ ] DR: RDS PITR verification, cross-region ECR replication, cluster rebuild from Git (GitOps advantage)

---

*Companion documents: [ARCHITECTURE.md](ARCHITECTURE.md) (design & diagrams), [TECH-NOTES.md](TECH-NOTES.md) (operational guidance).*
