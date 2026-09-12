# API Gateway & Service Mesh

Distributed API gateway with a Go/Gin control plane, PostgreSQL persistence, route matching, request transformation, JWT-protected data-plane routing, Prometheus metrics, Jaeger-ready tracing configuration, Envoy sidecar config, and a React/TypeScript admin console.

## Run Locally

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Services:

- Frontend admin console: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Jaeger UI: `http://localhost:16686`
- PostgreSQL: `localhost:5432`

The admin API uses `X-Admin-API-Key`. For local development, use the `ADMIN_API_KEY` value from `.env`. The frontend asks for this key and stores it in local browser storage.

## Backend API

All admin endpoints are under `/api/v1`:

- `GET /tenants`, `POST /tenants`, `GET|PUT|DELETE /tenants/{id}`
- `GET /routes`, `POST /routes`, `GET|PUT|DELETE /routes/{id}`
- `GET /routes/resolve?tenantId=&host=&path=&method=`
- `GET /anomalies`, `POST /anomalies`, `GET|PUT|DELETE /anomalies/{id}`
- `POST /traffic/score`
- `GET /healthz`, `GET /readyz`, `GET /metrics`

List endpoints accept `limit` and `offset`. `limit` defaults to `100` and is capped at `250`.

Example:

```powershell
curl.exe -H "X-Admin-API-Key: local-dev-key" http://localhost:8080/api/v1/tenants
```

OpenAPI details are in [docs/openapi.yaml](C:/mygit/JavaRelated/29Items-Projects/13Go/1API-Gateway-Service-Mesh/docs/openapi.yaml), with runnable examples in [docs/API-EXAMPLES.md](C:/mygit/JavaRelated/29Items-Projects/13Go/1API-Gateway-Service-Mesh/docs/API-EXAMPLES.md).

## Data-Plane Routing

Requests not handled by `/api/v1`, `/healthz`, `/readyz`, or `/metrics` are treated as gateway traffic. The gateway matches by host, path prefix, method, and optional `X-Tenant-ID`. For HTTP upstream routes it proxies to `upstreamService`, applies configured header transforms, enforces per-route limits, and checks JWT scopes when required.

Set `AUTH_MODE=strict` and `JWT_SHARED_SECRET` to require HS256 bearer tokens for data-plane traffic. Local development defaults to permissive mode.

## Tests

Backend:

```powershell
cd backend
go test -cover ./...
```

Frontend:

```powershell
cd frontend
npm install
npm test
npm run build
```

## Deployment

- Build images through `.github/workflows/ci.yml`.
- Publish images to ECR from the CI publish step.
- Update `deploy/helm/api-gateway/values.yaml` image tags.
- Let ArgoCD reconcile `deploy/argocd/application.yml`.

Production should use Amazon RDS PostgreSQL, AWS Secrets Manager or External Secrets Operator, ALB ingress, and mTLS through the selected service mesh.

## Troubleshooting

- Docker cannot connect to `dockerDesktopLinuxEngine`: start Docker Desktop and wait until the engine is running.
- Backend exits on startup: check `DATABASE_URL`, `ADMIN_API_KEY`, and PostgreSQL readiness.
- Admin API returns `401`: send `X-Admin-API-Key` with the configured value.
- Data-plane route returns `404`: verify tenant, host including port, path prefix, and HTTP method.
- Production startup rejects local secrets: set non-development `ADMIN_API_KEY` and `JWT_SHARED_SECRET`.
