# Serverless SaaS Platform

An enterprise-grade, multi-tenant B2B SaaS platform architected on AWS using a Serverless Event-Driven Architecture (EDA), DynamoDB Single-Table Design, EventBridge messaging, Cognito authentication with custom geolocation rules, and ML-powered usage forecasting via Amazon SageMaker.

---

## Architecture & System Overview

- **Edge & Security:** Amazon CloudFront + AWS WAF + Geolocation filtering (`CloudFront-Viewer-Country` header inspection).
- **Ingress & Auth:** Amazon API Gateway + Amazon Cognito User Pools + Lambda Custom Authorizer ([`authorizer.py`](file:///backend/src/api/authorizer.py)).
- **Dual Compute Runtime:**
  - **AWS Cloud Production:** Scoped Python 3.12 AWS Lambda functions deployed via AWS CDK.
  - **Local & Containerized Development:** High-performance FastAPI ASGI server ([`server.py`](file:///backend/src/server.py)) with GZip compression, security headers (`nosniff`, `DENY`), and interactive Swagger UI at `http://localhost:8000/docs`.
- **High-Throughput Ingestion:** Amazon SQS event buffering with Dead Letter Queues (DLQ) and DynamoDB atomic increments (`ADD total_count :inc`).
- **Data Persistence:** DynamoDB Single-Table Design with pay-per-request billing and Global Secondary Indexes (`GSI1`, `GSI2`).
- **Event Bus:** Amazon EventBridge with automated 90-day archive and threshold alerts (`UsageThresholdExceeded`).
- **Machine Learning:** Amazon SageMaker Serverless Inference (DeepAR / time-series forecasting) with heuristic moving-average fallback.
- **Frontend:** React 18 SPA with TypeScript, Vite, SVG time-series charts, and interactive Geolocation Simulator.

---

## Getting Started

### Option 1: Full-Stack Local Orchestration with Docker Compose (Recommended)

To run DynamoDB Local, the Backend API with automatic table creation & seeding, and the React Frontend with a single command:

```powershell
docker compose up --build
```

Access the services:
- **Interactive Web Dashboard:** [http://localhost:3000](http://localhost:3000)
- **Interactive Swagger / OpenAPI Docs:** [http://localhost:8000/docs](http://localhost:8000/docs)
- **API Health Endpoint:** [http://localhost:8000/health](http://localhost:8000/health)
- **DynamoDB Local:** `http://localhost:8002`

---

### Option 2: Local Development Setup

#### 1. Backend Microservices & API
```powershell
cd backend
python -m venv .venv
# Windows:
.venv\Scripts\activate
# Linux/macOS: source .venv/bin/activate

pip install -r requirements.txt -r requirements-dev.txt uvicorn fastapi

# Run all 27 unit & integration tests with coverage:
python -m pytest --cov=src tests/

# Run the local FastAPI server with auto-reload:
python -m src.server
```

#### 2. Frontend React Application
```powershell
cd frontend
npm install

# Typecheck and run tests:
npm run typecheck
npm test -- --run
npm run lint

# Build production assets:
npm run build

# Start Vite dev server:
npm run dev
```

---

## Interactive Features & Testing Guide

### 1. Geolocation Access Control
- In the top header of the frontend dashboard, switch the **Simulate Geo** dropdown between `US`, `DE`, `GB` and blocked countries such as `KP` (North Korea) or `IR` (Iran).
- Observe that requests originating from non-whitelisted regions are immediately rejected with **HTTP 403 Forbidden (`GEOLOCATION_RESTRICTED`)**, and a visual alert banner is rendered in the UI.

### 2. Multi-Tenant Switching
- Use the **Tenant** dropdown to switch between:
  - `Alpha Corp International` (Enterprise Tier, 10M units quota, allowed: `US`, `CA`, `GB`, `DE`)
  - `Beta Growth Innovations` (Pro Tier, 1M units quota, allowed: `US`, `DE`, `FR`)
  - `Gamma Financial Services` (Starter Tier, 100k units quota, allowed: `US` only)
- Real-time counters, progress bars, billing estimates, and forecast trajectories will instantly update.

### 3. Usage Ingestion & Threshold Alerts
- Click the **"Simulate API Call (+25k)"** button on the Usage Card.
- The platform emits a metered event batch, writes to the audit log, atomically increments the monthly counter, and dynamically updates the quota bar.

### 4. Machine Learning Capacity Forecasting
- The **Capacity Prediction Chart** plots 7-day historical ingestion alongside SageMaker's 7-day growth projection and 30-day forecasted total.
- If the 30-day forecast exceeds the tenant's monthly quota, an automated **Capacity Alert** advises upgrading tiers before overage penalties occur.

---

## API Endpoints Reference

| Method | Endpoint | Description | Sample cURL |
| :--- | :--- | :--- | :--- |
| `GET` | `/health` | Health check & system metadata | `curl http://localhost:8000/health` |
| `GET` | `/v1/tenants` | List active tenants (paginated) | `curl http://localhost:8000/v1/tenants?limit=10` |
| `POST` | `/v1/tenants` | Onboard new B2B tenant | `curl -X POST http://localhost:8000/v1/tenants -H "Content-Type: application/json" -d '{"name":"Acme Corp","tier":"PRO","contact_email":"ops@acme.com","allowed_countries":["US","DE"],"monthly_quota":500000}'` |
| `GET` | `/v1/tenants/{id}` | Get tenant profile & geo-policy | `curl http://localhost:8000/v1/tenants/tenant-alpha-enterprise -H "CloudFront-Viewer-Country: US"` |
| `PUT` | `/v1/tenants/{id}` | Update quota or allowed countries | `curl -X PUT http://localhost:8000/v1/tenants/tenant-alpha-enterprise -H "Content-Type: application/json" -d '{"monthly_quota":12000000}'` |
| `POST` | `/v1/usage/events` | Ingest single or batch metered events | `curl -X POST http://localhost:8000/v1/usage/events -H "X-Tenant-Id: tenant-alpha-enterprise" -H "Content-Type: application/json" -d '{"metric":"api_calls","count":100,"idempotency_key":"cli-001"}'` |
| `GET` | `/v1/usage` | Real-time usage metrics & quota | `curl http://localhost:8000/v1/usage?metric=api_calls -H "X-Tenant-Id: tenant-alpha-enterprise"` |
| `GET` | `/v1/usage/history`| Historical time-series points | `curl "http://localhost:8000/v1/usage/history?metric=api_calls&days=7" -H "X-Tenant-Id: tenant-alpha-enterprise"` |
| `GET` | `/v1/billing` | Invoice preview & overage breakdown| `curl http://localhost:8000/v1/billing -H "X-Tenant-Id: tenant-alpha-enterprise"` |
| `GET` | `/v1/predictions/capacity`| SageMaker 7 & 30-day forecast | `curl http://localhost:8000/v1/predictions/capacity -H "X-Tenant-Id: tenant-alpha-enterprise"` |

---

## Test Suite Execution

All 27 backend unit, integration, and error-scenario tests are verified:

```powershell
cd backend
python -m pytest --cov=src tests/
```

Test coverage report:
- `test_tenant_lifecycle_integration` (Create, read, update, list tenants and team members)
- `test_geolocation_enforcement_integration` (Verifies 200 OK for allowed countries and 403 Forbidden for restricted countries)
- `test_usage_ingestion_and_metrics_integration` (Batch and single event ingestion, metrics, history)
- `test_billing_and_prediction_endpoints` (Invoice previews, past invoices, SageMaker forecasts)
- `test_validation_error_on_invalid_tenant_creation` (Verifies 400 Bad Request on invalid payloads)
- `test_validation_error_on_invalid_country_code` (Verifies rejection of invalid ISO-3166 codes)
- `test_not_found_on_unknown_tenant` (Verifies 404 response on unknown tenant IDs)
- `test_user_creation_invalid_role` (Verifies validation on unauthorized team roles)
- `test_authorizer` (Cognito token validation and custom authorizer IAM policies)
- `test_single_table` (Atomic updates, GSI queries, raw event audit trail)
- **Total: 27 passing tests with ≥ 85% line coverage.**

Frontend test suite:
```powershell
cd frontend
npm test -- --run
```
- **Total: 5 passing tests.**

---

## Troubleshooting Guide

### 1. Port 8000 or 3000 Already in Use
- If port 8000 is occupied by another service on your machine, edit `docker-compose.yml` to change the host port: `"8001:8000"`.
- If DynamoDB Local port 8002 is occupied, change `"8002:8000"` to `"8003:8000"`.

### 2. DynamoDB Local Connectivity Issues
- When running locally outside of Docker, ensure `DYNAMODB_ENDPOINT_URL` is omitted (defaults to AWS) or set to `http://localhost:8002` if running the Docker container.
- If running in Docker Compose, the backend container automatically talks to `http://dynamodb-local:8000`.

### 3. Geolocation Access Denied (403) Unexpectedly
- By default, `ENFORCE_GEOLOCATION=true`. If making manual cURL requests without the `CloudFront-Viewer-Country` header, the authorizer defaults to `US`.
- Ensure your tenant's `allowed_countries` array contains the country code you are passing.

### 4. SageMaker Endpoint Latency & Circuit Breaking
- In production, SageMaker Serverless Inference endpoints scale to zero when idle. Cold starts may take 3–5 seconds.
- The backend features an automatic heuristic fallback in [`inference_client.py`](file:///backend/src/sagemaker/inference_client.py) that projects historical moving averages if the SageMaker endpoint times out or is in a warmup state.

---

## AWS Cloud Deployment with AWS CDK

To synthesize and deploy to AWS:

```powershell
cd infrastructure
npm install
npm run build

# Synthesize CloudFormation templates:
npx cdk synth

# Deploy all stacks to AWS:
npx cdk deploy --all
```
