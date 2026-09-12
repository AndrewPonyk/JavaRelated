# Inventory Management System

A complete warehouse inventory application built with Java 17, Spring Boot, JPA, MySQL, REST/GraphQL, Kafka, Quartz, Vue 3, Tailwind, and a Redis-backed FastAPI forecasting service.

## Run the full stack

Prerequisites: Docker Desktop/Engine with Compose v2. Copy the development configuration before starting; Compose deliberately refuses to invent database passwords when the file is absent:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
.\scripts\smoke-test.ps1
```

| Service | Local URL |
|---|---|
| Operator UI | `http://localhost:5173` |
| REST / Swagger | `http://localhost:8080/api/v1` / `http://localhost:8080/swagger-ui.html` |
| GraphQL / GraphiQL | `http://localhost:8080/graphql` / `http://localhost:8080/graphiql` |
| Backend health / metrics | `http://localhost:8080/actuator/health` / `http://localhost:8080/actuator/prometheus` |
| Forecast liveness / readiness | `http://localhost:8000/health/live` / `http://localhost:8000/health/ready` |

MySQL Flyway migrations create the schema and seed warehouse `00000000-0000-0000-0000-000000000001`. Compose binds development ports to loopback and explicitly builds the UI with local authorization. The backend otherwise fails closed: exposed environments use `SPRING_PROFILES_ACTIVE=secure`, a valid issuer and audience, TLS service URLs, and externally provisioned secrets.

## Application capabilities

1. Create and manage warehouses.
2. Activate or deactivate warehouses safely.
3. Create inventory items with SKU, name, warehouse, quantity, and reorder point.
4. Edit item metadata and barcode information.
5. Deactivate empty inventory items.
6. Search inventory by SKU, name, or barcode.
7. Filter inventory by warehouse and active status.
8. Sort and paginate inventory results.
9. Look up items using manually entered or hardware-scanned barcodes.
10. Scan supported barcodes using a device camera.
11. Support primary and alias barcodes for one item.
12. Validate EAN-8, EAN-13, UPC-A, Code 39, Code 128, and QR codes.
13. Record positive or negative stock adjustments with reasons and references.
14. Receive incoming stock.
15. Ship stock while preventing shipment beyond available quantity.
16. Perform bulk stock adjustments through the REST API.
17. Transfer stock between warehouses.
18. Create stock reservations with external reference numbers.
19. Release existing reservations.
20. Fulfill reservations and deduct the reserved quantity from stock.
21. Distinguish on-hand, reserved, and available quantities.
22. Prevent stock from becoming negative or dropping below reserved quantity.
23. Display the complete immutable stock-movement history.
24. Detect items at or below their reorder point.
25. Generate and display low-stock alerts.
26. Acknowledge low-stock alerts.
27. Generate demand forecasts from historical stock movements.
28. Return forecast ranges, model version, feature version, and model error measurements.
29. Refresh forecasts manually or through scheduled jobs.
30. Record audit history with actor, reason, correlation ID, timestamp, and before/after values.
31. Reconcile current inventory quantities against the stock ledger.
32. Queue receipt, shipment, and adjustment commands when the browser is offline.
33. Replay offline commands safely using idempotency keys.
34. Prevent duplicate processing when requests are retried.
35. Provide role-based read, write, and administrator access using OIDC/JWT scopes.
36. Provide a local development authentication mode.
37. Expose REST endpoints with OpenAPI/Swagger documentation.
38. Provide GraphQL inventory, warehouse, movement, and forecast queries.
39. Publish stock-change events through Kafka using a transactional outbox.
40. Process low-stock events idempotently through a consumer inbox.
41. Expose backend, frontend, and forecast-service health checks.
42. Expose Prometheus application metrics.
43. Run clustered scheduled jobs using Quartz.
44. Support Docker Compose for local deployment.
45. Support Kubernetes deployment with TLS enforcement, health probes, scaling, and network policies.

## Core REST API

All write commands that can be retried require an `Idempotency-Key` header. Responses use RFC problem details for failures.

| Capability | Endpoints |
|---|---|
| Warehouses | `GET/POST /api/v1/warehouses`, `GET/PUT/DELETE /api/v1/warehouses/{id}` |
| Items | `GET/POST /api/v1/inventory`, `GET/PUT/DELETE /api/v1/inventory/{id}` |
| Barcode | `GET /api/v1/inventory/barcode/{barcode}?warehouseId=...` |
| Stock | `POST /api/v1/inventory/{id}/adjustments`, `/receipts`, `/shipments` |
| Bulk and transfer | `POST /api/v1/inventory/bulk-adjustments`, `POST /api/v1/transfers` |
| Reservations | `POST/GET /api/v1/inventory/{id}/reservations`, `POST /api/v1/reservations/{id}/release|fulfill` |
| Ledger | `GET /api/v1/inventory/{id}/movements` |
| Forecasts | `GET/POST /api/v1/inventory/{id}/forecast[/refresh]` |
| Alerts and audit | `GET /api/v1/alerts`, `POST /api/v1/alerts/{id}/acknowledge`, `GET /api/v1/audit` |
| Reconciliation | `POST /api/v1/admin/reconciliation` |

Example item creation:

```powershell
$body = @{
  sku='SKU-001'; barcode='4006381333931'; symbology='EAN_13'; aliases=@()
  name='Demo item'; quantity=10; reorderPoint=3
  warehouseId='00000000-0000-0000-0000-000000000001'
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/inventory `
  -Headers @{ 'Idempotency-Key'=[guid]::NewGuid().ToString() } `
  -ContentType application/json -Body $body
```

## Test and build without Compose

Use Java 17, Node.js 20+, and Python 3.12. The backend integration tests automatically skip when Docker is unavailable; with Docker they exercise real MySQL/Flyway and Kafka.

```powershell
mvn -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test:coverage
npm --prefix frontend run build
python -m pip install -r forecast-service/requirements-dev.txt
python -m ruff check forecast-service/app forecast-service/tests
python -m pytest forecast-service/tests --cov=forecast-service/app --cov-fail-under=80
```

Frontend and Python coverage enforce 80% minimums. Java enforces 80% on the inventory business rules and generates `backend/target/site/jacoco/index.html`; domain invariants, error responses, authorization validation, and the main API flow are covered by unit and container-backed tests.

## Architecture and operations

| Path | Purpose |
|---|---|
| `backend/` | Modular Spring inventory application, outbox/inbox, ledger, Flyway, Quartz |
| `frontend/` | Responsive Vue operator UI, hardware/camera scanning, offline replay |
| `forecast-service/` | Holt-trend training/inference and checksummed Redis model registry |
| `shared/` | Versioned Kafka JSON Schema contracts |
| `infrastructure/k8s/` | DOKS-ready Kustomize base and dev/staging/prod overlays |
| `docs/runbooks/` | Backup restore, Kafka replay, and Redis cold-start procedures |

See [API guide](docs/API.md), [Architecture](docs/ARCHITECTURE.md), [Project plan](docs/PROJECT-PLAN.md), and [Technical notes](docs/TECH-NOTES.md). Production secrets are supplied outside Git through GitLab protected variables or a secret manager; `secret.example.yaml` is shape-only.

## Troubleshooting

| Symptom | Resolution |
|---|---|
| Compose reports a required password is missing | Copy .env.example to .env and keep that local file out of version control |
| MySQL rejects the configured password after `.env` changes | MySQL only applies initialization variables to a new data directory. Restore the credential used to create the volume, rotate the database user from an authenticated session, or back up and recreate the local volume; never delete a production volume to fix configuration drift |
| Port 8080, 5173, 3306, 6379, or 29092 is occupied | Stop the conflicting process or change the corresponding port in .env |
| Docker health checks and `docker exec` all time out | Restart Docker Desktop/Engine, then rerun `docker compose up -d`; this indicates a local container-runtime failure rather than an application health response |
| Maven reports class version 61 is unsupported | Point JAVA_HOME and PATH to JDK 17 before running Maven |
| Backend fails immediately outside Compose | Activate the local profile and set `SPRING_DATASOURCE_PASSWORD`; secure deployments must also provide datasource and OIDC values |
| Secure API returns 401 | Verify token signature, expiry, issuer, and that aud contains the configured OIDC_AUDIENCE |
| Secure API returns 403 | Add the read, write, or admin scope required by the endpoint |
| Forecast readiness returns 503 | Verify Redis connectivity, REDIS_URL TLS credentials, and the forecast network policy |
| Frontend has no write controls | Local builds require VITE_LOCAL_AUTH=true; production requires a non-expired scoped bearer token |
| Testcontainers tests are skipped | Start Docker and rerun Maven verify; CI supplies Docker-in-Docker for these tests |
| A migration fails | Do not edit an applied migration; inspect Flyway history, restore if necessary, and add a forward-fix migration |
