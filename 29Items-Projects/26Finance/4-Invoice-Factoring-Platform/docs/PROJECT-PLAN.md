# Invoice Factoring Platform — Project Plan

**Status:** Architecture baseline · **Owner:** Platform Team · **Last updated:** 2026-06-30

The Invoice Factoring Platform lets SMBs convert unpaid invoices into immediate working
capital. The core differentiator is an **automated underwriting engine**: every submitted
invoice is scored by an ML default-risk model that drives the advance rate and discount
fee, removing the manual credit-analyst bottleneck that constrains traditional factoring.

---

## 1.1 Project File Structure

The repository is a polyglot mono-repo. The backend follows **Clean Architecture**
(Domain → Application → Infrastructure → API), the frontend is a Vite SPA, and all
infrastructure-as-code lives under `infrastructure/`.

```
4-Invoice-Factoring-Platform/
│
├── docs/                                   # 📚 Architecture & process docs
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── src/
│   ├── backend/                            # 🧩 .NET 8 solution
│   │   ├── InvoiceFactoring.sln
│   │   ├── Directory.Build.props           # Shared MSBuild props (nullable, analyzers)
│   │   ├── Directory.Packages.props        # Central package version management
│   │   ├── .editorconfig
│   │   │
│   │   ├── src/
│   │   │   ├── InvoiceFactoring.Domain/            # Enterprise rules — no dependencies
│   │   │   │   ├── Common/                         #   Entity, AggregateRoot, DomainEvent
│   │   │   │   ├── Entities/                       #   Invoice, Company, Advance, ...
│   │   │   │   ├── ValueObjects/                   #   Money, TaxId, AdvanceTerms
│   │   │   │   ├── Enums/                          #   InvoiceStatus, RiskGrade
│   │   │   │   └── Events/                         #   InvoiceSubmittedEvent, ...
│   │   │   │
│   │   │   ├── InvoiceFactoring.Application/       # Use cases (CQRS via MediatR)
│   │   │   │   ├── Common/                         #   Behaviors, exceptions, mapping
│   │   │   │   ├── Abstractions/                   #   IRepository, IPaymentGateway, ...
│   │   │   │   └── Features/
│   │   │   │       ├── Invoices/                   #   Commands + Queries + Validators
│   │   │   │       ├── Underwriting/
│   │   │   │       ├── Advances/
│   │   │   │       └── Payments/
│   │   │   │
│   │   │   ├── InvoiceFactoring.Infrastructure/    # Adapters to the outside world
│   │   │   │   ├── Persistence/                    #   AppDbContext, configs, repos
│   │   │   │   │   ├── Configurations/
│   │   │   │   │   └── Migrations/
│   │   │   │   ├── Payments/Stripe/                #   Stripe gateway adapter
│   │   │   │   ├── Banking/Plaid/                  #   Plaid client adapter
│   │   │   │   ├── Messaging/ServiceBus/           #   Azure Service Bus pub/sub
│   │   │   │   ├── Caching/                        #   Redis
│   │   │   │   └── DependencyInjection.cs
│   │   │   │
│   │   │   ├── InvoiceFactoring.ML/                # Credit-risk model (ML.NET)
│   │   │   │   ├── Models/                         #   Input/Output schemas
│   │   │   │   ├── Training/                       #   Trainer pipeline + CLI
│   │   │   │   ├── CreditRiskPredictor.cs          #   Inference service (PredictionEnginePool)
│   │   │   │   └── README.md
│   │   │   │
│   │   │   └── InvoiceFactoring.Api/               # ASP.NET Core host
│   │   │       ├── Controllers/
│   │   │       ├── Webhooks/                       #   Stripe / Plaid webhook receivers
│   │   │       ├── Middleware/                     #   Exception handling, correlation id
│   │   │       ├── Program.cs
│   │   │       ├── appsettings.json
│   │   │       ├── appsettings.Development.json
│   │   │       └── Dockerfile
│   │   │
│   │   └── tests/
│   │       ├── InvoiceFactoring.Domain.UnitTests/
│   │       ├── InvoiceFactoring.Application.UnitTests/
│   │       ├── InvoiceFactoring.IntegrationTests/  # Testcontainers (SQL Server)
│   │       └── InvoiceFactoring.Architecture.Tests/ # Layer-dependency guards
│   │
│   └── frontend/                           # ⚛️ React + TypeScript SPA
│       ├── package.json
│       ├── tsconfig.json
│       ├── vite.config.ts
│       ├── index.html
│       ├── .eslintrc.cjs
│       ├── .prettierrc
│       ├── Dockerfile
│       ├── nginx.conf
│       └── src/
│           ├── main.tsx
│           ├── App.tsx
│           ├── api/                         #   Typed API client (axios + zod)
│           ├── components/                  #   Presentational + container components
│           ├── features/                    #   Feature folders (invoices, onboarding)
│           ├── hooks/                        #   TanStack Query hooks
│           ├── lib/                          #   auth, money formatting, config
│           ├── pages/                        #   Route-level components
│           └── types/                        #   Shared TS types (mirror API DTOs)
│
├── database/
│   ├── migrations/                          # Versioned SQL (also generated by EF Core)
│   │   └── V001__initial_schema.sql
│   └── seed/                                # Reference / demo data
│
├── infrastructure/
│   ├── bicep/                               # Azure IaC (AKS, SQL, Redis, KV, ACR, SB)
│   │   ├── main.bicep
│   │   └── modules/
│   ├── k8s/                                 # Kubernetes manifests (Kustomize base/overlays)
│   │   ├── base/
│   │   └── overlays/{dev,staging,prod}/
│   └── docker/
│       └── docker-compose.yml               # Local dependencies (SQL, Redis, Azurite)
│
├── .azuredevops/
│   └── pipelines/
│       ├── ci.yml                           # PR validation: lint → test → build
│       ├── cd.yml                           # Deploy: dev → staging → prod (gated)
│       └── templates/                       # Reusable pipeline steps
│
├── azure-pipelines.yml                      # Entry point that includes ci/cd templates
├── .env.example
├── .editorconfig
├── .gitignore
└── README.md
```

### Why this structure

- **Clean Architecture** keeps domain rules (factoring math, risk grading) independent of
  EF Core, Stripe, and Plaid. Vendors can be swapped behind `Application/Abstractions`
  interfaces without touching business logic — critical in fintech where payment/banking
  partners change.
- **CQRS feature folders** group each use case's command, handler, validator, and DTO,
  so a new operation (e.g. "request advance") is added in one place.
- **`InvoiceFactoring.ML` as a separate project** isolates the heavyweight ML.NET /
  LightGBM dependency and its training CLI from the request-serving host.
- **Kustomize overlays** give per-environment Kubernetes config without copy-paste.

---

## 1.2 Implementation TODO List

### ✅ Phase 1 — Foundation (high priority)

- [ ] Provision Azure landing zone via Bicep: AKS, Azure SQL, Cache for Redis, Key Vault,
      ACR, Service Bus, Application Insights.
- [ ] Scaffold .NET 8 solution with the four Clean Architecture projects + test projects.
- [ ] Configure central package management (`Directory.Packages.props`) and analyzers.
- [ ] Define core domain model: `Company`, `Invoice`, `Advance`, `CreditAssessment`,
      `Money`, `InvoiceStatus`, `RiskGrade`.
- [ ] EF Core `AppDbContext` + initial migration → SQL Server.
- [ ] Authentication/authorization via Azure AD B2C (JWT bearer) with role policies
      (`Borrower`, `Admin`, `Underwriter`).
- [ ] Global exception-handling middleware → RFC 7807 ProblemDetails.
- [ ] CI pipeline: restore → lint → unit test → build container → push to ACR.
- [ ] Frontend Vite + TS scaffold, auth flow, typed API client, base layout.
- [ ] Health checks (`/health/live`, `/health/ready`) + OpenTelemetry wiring.

### 🔨 Phase 2 — Core Features (medium priority)

- [ ] **Onboarding:** company registration + KYC/KYB; Plaid Link to connect bank account.
- [ ] **Invoice submission:** upload invoice (PDF + structured fields), validation,
      duplicate detection, debtor capture.
- [ ] **Underwriting engine:** publish `InvoiceSubmitted` → Service Bus → consumer enriches
      with Plaid cash-flow features → `CreditRiskPredictor` returns probability-of-default →
      `RiskGrade` + advance rate + fee.
- [ ] **ML pipeline:** train LightGBM model on historical payment outcomes; register model
      artifact in blob storage; serve via `PredictionEnginePool`; capture feature importance
      for adverse-action reasons.
- [ ] **Advance lifecycle:** approve → disburse funds via Stripe (Connect transfer/payout) →
      track outstanding → reconcile repayment when debtor pays.
- [ ] **Stripe & Plaid webhooks:** idempotent receivers updating payment + funding status.
- [ ] **Borrower dashboard:** invoice list, status timeline, advance offers, accept flow.
- [ ] **Admin/underwriter console:** review queue, manual override, audit trail.
- [ ] Integration tests with Testcontainers; contract tests against Stripe/Plaid sandboxes.

### 🎨 Phase 3 — Polish & Optimization (lower priority)

- [ ] Risk-based dynamic pricing experiments (A/B advance-rate strategies).
- [ ] Model monitoring: data drift, PD calibration, automated retraining trigger.
- [ ] Investor/marketplace side: pool invoices, expose yield to funders (future).
- [ ] Redis caching for debtor risk lookups and dashboard aggregates.
- [ ] Notifications (email/SMS) on status changes via Service Bus + SendGrid.
- [ ] Rate limiting, WAF rules, penetration-test remediation.
- [ ] Performance: response compression, read replicas, query tuning, load test to SLO.
- [ ] Accessibility (WCAG 2.1 AA) + i18n; PWA offline invoice draft.
- [ ] Cost optimization: AKS autoscaling profiles, spot node pool for ML training jobs.

---

## Milestones

| Milestone                         | Exit criteria                                                        |
| --------------------------------- | ------------------------------------------------------------------- |
| **M1 — Walking skeleton**         | Auth + submit invoice + persist + see it in dashboard, deployed to dev |
| **M2 — Automated underwriting**   | Submitted invoice scored end-to-end, advance offer generated         |
| **M3 — Money movement**           | Advance disbursed via Stripe; repayment reconciled                   |
| **M4 — Production hardening**     | SLOs met, security review passed, model monitoring live              |
