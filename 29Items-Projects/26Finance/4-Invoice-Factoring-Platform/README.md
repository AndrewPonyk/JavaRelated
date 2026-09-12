# Invoice Factoring Platform

> SMB invoice financing marketplace with an automated, ML-driven credit assessment engine.

Businesses (SMBs) submit unpaid customer invoices and receive an immediate cash
advance. The platform underwrites each invoice automatically using a machine-learning
**default-risk model**, prices the advance, disburses funds via **Stripe**, and pulls
verified bank-account / cash-flow data via **Plaid**. Repayment is reconciled when the
invoice debtor pays.

---

## Tech Stack

| Layer            | Technology                                                        |
| ---------------- | ----------------------------------------------------------------- |
| Backend API      | C# / .NET 8 (ASP.NET Core Web API), Clean Architecture            |
| Persistence      | SQL Server 2022, EF Core 8                                         |
| ML / Scoring     | ML.NET (LightGBM) credit-risk default model                       |
| Payments         | Stripe (Connect, PaymentIntents, Payouts, Webhooks)               |
| Banking data     | Plaid (Auth, Transactions, Assets)                                |
| Messaging        | Azure Service Bus (async underwriting & payment events)           |
| Cache            | Azure Cache for Redis                                              |
| Frontend         | React 18 + TypeScript + Vite + TanStack Query                     |
| Infrastructure   | Azure AKS, Azure Container Registry, Bicep                         |
| CI/CD            | Azure DevOps Pipelines                                             |
| Observability    | OpenTelemetry → Azure Application Insights                         |

## Repository Layout

```
.
├── docs/                 # PROJECT-PLAN, ARCHITECTURE, TECH-NOTES
├── src/
│   ├── backend/          # .NET 8 solution (Clean Architecture)
│   └── frontend/         # React + TypeScript SPA
├── database/             # SQL migration scripts
├── infrastructure/       # Bicep (Azure), Kubernetes manifests, docker-compose
└── .azuredevops/         # Azure DevOps pipeline definitions
```

See [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) for the full structure, the
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for diagrams and design rationale, and
[`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) for CI/CD, testing, and operational guidance.

## Quick Start (local dev)

```bash
# 1. Infrastructure dependencies (SQL Server, Redis, Azurite)
docker compose -f infrastructure/docker/docker-compose.yml up -d

# 2. Backend
cd src/backend
cp ../../.env.example .env          # then fill in secrets
dotnet restore
# Migrations auto-apply in Development; run this explicitly for staging/prod:
dotnet ef database update -p src/InvoiceFactoring.Infrastructure -s src/InvoiceFactoring.Api
dotnet run --project src/InvoiceFactoring.Api      # https://localhost:7080 (Swagger at /swagger)

# 3. Frontend
cd ../frontend
npm install
npm run dev                          # http://localhost:5173
```

## Status

✅ **Core flow works end-to-end and is verified.** The solution builds clean (0 warnings),
**49 tests pass** (46 unit + 3 integration against a real SQL Server container), both Docker
images build/run/serve, and there are **no known-vulnerable dependencies**.

Implemented and tested: company + invoice domain, submit → automated underwriting (scoring,
risk grade, pricing) → advance offer → Stripe disbursement → webhook reconciliation, behind
JWT auth with CORS, rate limiting, response compression, and security headers.

**Underwriting** uses a transparent, explainable heuristic scorer by default (real PDs +
adverse-action reason codes, no binary needed); it automatically switches to the trained
ML.NET model when a model artifact is present at `ML:ModelPath` (see
[`src/backend/src/InvoiceFactoring.ML`](src/backend/src/InvoiceFactoring.ML)).

Still stubbed (clear `// TODO`s): live Plaid feature extraction, a trained ML model artifact,
onboarding/KYC, PDF upload, and the admin/underwriter console.

## Demo — Available Functionality

An honest mapping of what's actually demoable, based on what's implemented in the code.

### ✅ Available (implemented + tested)

- **Create invoice** — submit an invoice for factoring (`POST /api/invoices`; frontend has the form with validation)
- **Show invoices (list)** — paged list of a company's invoices with status + offer (`GET /api/invoices`; frontend `InvoiceList`)
- **Show invoice (single)** — one invoice with its underwriting offer / decline reason (`GET /api/invoices/{id}`)
- **Underwrite invoice** — automated risk scoring → risk grade + advance rate/fee + reason codes (runs async via the worker, or on-demand). Not a button — it's the engine that turns *Submitted* → *Approved/Declined*
- **Accept advance offer** — accept an approved offer → initiates the Stripe payout (`POST /api/invoices/{id}/accept-offer`; frontend "Accept advance" button)
- **Reconcile payout** — Stripe webhook marks the advance disbursed / invoice outstanding (`POST /api/webhooks/stripe`; system action, not user-clicked)
- **Health / status** — `GET /health/live`, `GET /health/ready`

### ❌ Not available

- **Register** — no company/user registration endpoint. Companies exist in the domain (`Company.Register`) but are only seeded directly in tests; there's no signup API or UI
- **Login** — no login flow. Auth is *wired* for Azure AD B2C (JWT), but no login/registration is implemented
- Also missing: connect bank (Plaid Link), KYC/onboarding, admin/underwriter console, notifications

### ⚠️ Auth caveat for the demo

The invoice endpoints sit behind `[Authorize]`, and the frontend's auth token is still a
`// TODO` (returns `null`), so calls hit **401** as-is. To click through a demo you need to
either configure a real Azure AD B2C tenant, or run with a test/dev auth scheme. The cleanest
working path today is the integration test flow (`InvoiceFlowTests`), which drives
create → underwrite → show end-to-end with a test identity and a real database.

## Troubleshooting

| Symptom | Cause / Fix |
| ------- | ----------- |
| API exits immediately / can't reach SQL on `dotnet run` | Start dependencies first: `docker compose -f infrastructure/docker/docker-compose.yml up -d`. In Development the API auto-applies migrations only when a `ConnectionStrings:SqlServer` is configured. |
| `npm ci` fails with "can only install with an existing package-lock.json" | Commit `src/frontend/package-lock.json` (run `npm install` once to generate it). |
| Underwriting worker logs "Service Bus not configured; idle" | Expected without a Service Bus connection string — submission still persists; run the underwriting use case directly or configure `ServiceBus:ConnectionString`. |
| `dotnet ef` not found | `dotnet tool install --global dotnet-ef --version 8.0.8`. |
| Integration tests fail to start | They need Docker running (Testcontainers pulls `mcr.microsoft.com/mssql/server:2022-latest`). |
| Stripe/Plaid calls fail locally | Set sandbox keys in `.env` (see `.env.example`); without them those endpoints are exercised only by mocks/tests. |
