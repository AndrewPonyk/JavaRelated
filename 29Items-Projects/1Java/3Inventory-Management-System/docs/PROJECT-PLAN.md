# Inventory Management System — Project Plan

## Purpose and scope

This repository is a production-oriented warehouse inventory application. It combines a Java 17/Spring Boot API, a Vue 3/Tailwind web client, a Python forecasting service, MySQL, Kafka, Redis, Quartz, GraphQL, OpenAPI, Docker Compose, and Kubernetes manifests for DigitalOcean Kubernetes (DOKS).

The first implementation is a **modular monolith plus a forecasting microservice**. Inventory transactions remain strongly consistent inside one backend and one MySQL schema. Kafka carries integration events; the Python service owns model loading and prediction; Redis holds versioned model artifacts and short-lived prediction data.

## 1.1 Project file structure

The tree below highlights the representative structure; the repository also contains focused DTOs, tests, runbooks, migrations, and validation tooling. Package-by-feature is used in the backend so a feature can later be extracted without reorganizing the entire codebase.

```text
.
├── .editorconfig                         # Cross-language editor defaults
├── .env.example                         # Local configuration contract; no secrets
├── .gitignore
├── .gitlab-ci.yml                        # GitLab build, test, scan, publish, deploy pipeline
├── README.md                             # Developer quick start and operating notes
├── compose.yaml                          # Local MySQL, Kafka, Redis, API, forecast, and UI
├── gpt-5.txt                             # Requested empty model marker
├── docs/
│   ├── PROJECT-PLAN.md                   # Scope, structure, and prioritized work
│   ├── ARCHITECTURE.md                   # Architecture decisions and diagrams
│   └── TECH-NOTES.md                     # Delivery, testing, and operations guidance
├── backend/
│   ├── .dockerignore
│   ├── checkstyle.xml                    # Java static-analysis baseline
│   ├── Dockerfile                        # Layer-friendly JVM runtime image
│   ├── pom.xml                           # Spring Boot and quality/test dependencies
│   └── src/
│       ├── main/
│       │   ├── java/com/example/inventory/
│       │   │   ├── InventoryApplication.java
│       │   │   ├── common/error/         # RFC 9457-compatible API errors
│       │   │   │   ├── ConflictException.java
│       │   │   │   ├── GlobalExceptionHandler.java
│       │   │   │   └── NotFoundException.java
│       │   │   ├── config/
│       │   │   │   ├── OpenApiConfig.java
│       │   │   │   ├── QuartzConfig.java
│       │   │   │   └── SecurityConfig.java
│       │   │   ├── inventory/
│       │   │   │   ├── domain/           # JPA aggregate and warehouse relation
│       │   │   │   │   ├── InventoryItem.java
│       │   │   │   │   └── Warehouse.java
│       │   │   │   ├── dto/              # Validated transport contracts
│       │   │   │   │   ├── InventoryCreateRequest.java
│       │   │   │   │   ├── InventoryResponse.java
│       │   │   │   │   └── InventoryUpdateRequest.java
│       │   │   │   ├── repository/
│       │   │   │   │   ├── InventoryItemRepository.java
│       │   │   │   │   └── WarehouseRepository.java
│       │   │   │   ├── service/InventoryService.java
│       │   │   │   └── web/
│       │   │   │       ├── InventoryController.java
│       │   │   │       └── InventoryGraphqlController.java
│       │   │   ├── outbox/                 # Transactional event relay
│       │   │   ├── stock/                  # Ledger, reservations, transfers
│       │   │   ├── warehouse/              # Warehouse lifecycle
│       │   │   ├── forecast/               # Forecast orchestration/snapshots
│       │   │   ├── alert/                  # Idempotent Kafka consumer
│       │   │   └── scheduling/             # Clustered forecast/reconciliation jobs
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── db/migration/V1__create_inventory_schema.sql
│       │       └── graphql/inventory.graphqls
│       └── test/java/com/example/inventory/inventory/
│           ├── service/InventoryServiceTest.java
│           └── web/InventoryControllerIT.java
├── frontend/
│   ├── .dockerignore
│   ├── .eslintrc.cjs
│   ├── .prettierrc.json
│   ├── Dockerfile
│   ├── index.html
│   ├── nginx.conf
│   ├── package-lock.json                  # Reproducible npm dependency graph
│   ├── package.json
│   ├── postcss.config.js
│   ├── tailwind.config.js
│   ├── tsconfig.json
│   ├── tsconfig.app.json
│   ├── tsconfig.node.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.vue
│       ├── env.d.ts
│       ├── main.ts
│       ├── style.css
│       ├── api/inventory.ts
│       ├── components/
│       │   ├── BarcodeInput.vue
│       │   └── InventoryTable.vue
│       ├── types/inventory.ts
│       └── views/InventoryView.vue
├── forecast-service/
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── pytest.ini
│   ├── requirements.txt
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py                         # FastAPI health and prediction endpoints
│   │   ├── model_store.py                  # Redis-backed checksummed model registry
│   │   └── schemas.py
│   └── tests/test_main.py
├── shared/
│   ├── README.md                           # Contract ownership and compatibility rules
│   └── schemas/events/stock-changed.schema.json
├── infrastructure/k8s/
│   ├── base/
│   │   ├── backend-deployment.yaml
│   │   ├── backend-service.yaml
│   │   ├── configmap.yaml
│   │   ├── forecast-deployment.yaml
│   │   ├── forecast-service.yaml
│   │   ├── frontend-deployment.yaml
│   │   ├── frontend-service.yaml
│   │   ├── hpa.yaml
│   │   ├── ingress.yaml
│   │   ├── kustomization.yaml
│   │   ├── namespace.yaml
│   │   ├── network-policy.yaml
│   │   ├── secret.example.yaml
│   │   └── service-account.yaml
│   └── overlays/
│       ├── dev/{kustomization.yaml,replicas.yaml}
│       ├── staging/{kustomization.yaml,replicas.yaml}
│       └── prod/{kustomization.yaml,replicas.yaml}
└── scripts/
    ├── dev.ps1                             # Local stack lifecycle helper
    └── smoke-test.ps1                      # Post-deployment health checks
```

### Module responsibilities

| Area | Owns | Must not own |
|---|---|---|
| Backend | Inventory rules, persistence, REST/GraphQL contracts, scheduling, event publication | ML model implementation or browser state |
| Frontend | Operator workflows, barcode input, display state, API client | Business invariants or direct database access |
| Forecast service | Model retrieval, feature validation, inference | Inventory writes or user authorization policy |
| Shared contracts | Versioned event schemas and compatibility guidance | Runtime logic |
| Infrastructure | Runtime topology, scaling, networking, deploy configuration | Application secrets in Git |

## 1.2 Implementation TODO list

### Phase 1 — Foundation (high priority)

- [x] Define the bounded-context language: warehouse, SKU, barcode, on-hand, reserved, available, adjustment, transfer, and reorder point.
- [x] Implement OIDC/JWT validation and `inventory:read`, `inventory:write`, and `inventory:admin` scopes, with an explicit local-only mode.
- [x] Add the immutable stock-movement ledger and pessimistic item locking.
- [x] Implement a transactional outbox and an idempotent consumer inbox.
- [x] Configure Flyway-owned schema migrations with Hibernate validation.
- [x] Provide Compose services and DOKS manifests for privately addressed MySQL, Kafka, and Redis endpoints.
- [x] Provide protected GitLab environment jobs and external secret templates; operators supply account credentials.
- [x] Add container-backed MySQL/Kafka integration tests and fakeredis model-registry tests.
- [x] Publish live Spring OpenAPI and a versioned JSON event schema with validation in CI.
- [x] Expose health/readiness and Prometheus metrics, structured correlation fields, and operational SLO guidance.

### Phase 2 — Core features (medium priority)

- [x] Complete warehouse, item, adjustment, reservation, receipt, shipment, and transfer APIs.
- [x] Require idempotency keys on stock writes and provide an offline-safe browser retry queue.
- [x] Validate barcode symbologies, support camera/hardware scanners, and map aliases to one SKU.
- [x] Add bounded pagination, filtering, sorting, bulk REST operations, and composed GraphQL reads.
- [x] Add stock thresholds and an inbox-deduplicated Kafka low-stock workflow.
- [x] Train/evaluate Holt trend models and return model/feature versions, horizon, interval, and MAE.
- [x] Add JDBC-clustered Quartz forecast refresh and ledger reconciliation jobs.
- [x] Implement audit history with actor, correlation ID, reason, timestamp, and before/after representations.
- [x] Build responsive, keyboard-accessible, role-aware Vue workflows.
- [x] Document backup restore, Kafka replay, and Redis cold-start exercises in runnable runbooks.

### Phase 3 — Polish and optimization (lower priority)

- [x] Keep authoritative stock uncached; model caching has checksum validation and last-known-good fallback.
- [x] Add lookup/ledger/outbox indexes and bounded database queries; retain load-test guidance for environment sizing.
- [x] Document stock-ledger/outbox retention and archival ownership in the operations notes.
- [x] Provide immutable-image promotion, protected production approval, rollout verification, and rollback commands.
- [x] Add dependency audit, SAST, secret scanning, container scanning, and CycloneDX SBOM CI gates.
- [x] Test Kafka retry persistence, Redis loss/corruption fallback, forecast timeout handling, and graceful pod probes.
- [x] Provide responsive semantic controls, focus styles, status announcements, and a Playwright accessibility-ready smoke path.
- [x] Document the measurable extraction criteria in the architecture decision checkpoints.
- [x] Fail closed outside the explicit local profile; validate token issuer, audience, expiry, and scopes.
- [x] Split runtime and development dependencies, patch audited vulnerabilities, and enforce 80% quality gates.
- [x] Replace quadratic model evaluation and unbounded reconciliation reads with bounded linear/paginated work.
- [x] Add configuration validation, browser E2E CI, HTTPS redirection, component network policies, and secret-free Compose defaults.
- [x] Add pod disruption budgets, topology spreading, read-only root filesystems, and writable ephemeral runtime mounts.

## Definition of done

A feature is done when its domain rules, authorization, migration, observability, API/event contracts, unit and integration tests, rollback behavior, and operator documentation are complete. Production deployment additionally requires a reviewed migration plan, dashboards, alerts, and a verified rollback or forward-fix path.
