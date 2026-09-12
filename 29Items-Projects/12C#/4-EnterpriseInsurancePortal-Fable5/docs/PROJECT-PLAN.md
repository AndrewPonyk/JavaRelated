# Enterprise Insurance Portal — Project Plan

**Stack:** .NET 8, ASP.NET Core, Blazor Server, EF Core, SignalR, gRPC, xUnit, IdentityServer, Hangfire, SQL Server, Kafka, React (public portal)
**Platform:** Azure AKS · **CI/CD:** Azure DevOps

---

## 1.1 Project File Structure

```
EnterpriseInsurancePortal/
├── .azuredevops/                        # Azure DevOps pipeline definitions
│   ├── azure-pipelines.yml              # Main multi-stage pipeline (CI + CD)
│   └── templates/
│       ├── dotnet-build.yml             # Reusable: restore → build → test → publish
│       ├── react-build.yml              # Reusable: lint → test → build (Vite)
│       └── deploy-aks.yml               # Reusable: helm/kubectl deploy per environment
│
├── deploy/
│   ├── docker/
│   │   ├── *.Dockerfile                 # One per deployable (+ Migrations.Dockerfile = EF bundle)
│   │   ├── nginx.conf                   # SPA serving config for the React image
│   │   └── docker-compose.yml           # Full local stack: SQL Server, Kafka (KRaft) + all services
│   └── k8s/                             # kustomize base: deployments, HPA, PDB, ingress,
│       └── ...                          #   ConfigMap, Key Vault CSI secrets, migration Job
│
├── docs/
│   ├── PROJECT-PLAN.md                  # This file
│   ├── ARCHITECTURE.md                  # Patterns, diagrams, data flow, security
│   └── TECH-NOTES.md                    # CI/CD, testing, deployment, pitfalls
│
├── migrations/
│   └── sql/                             # DBA-reviewed idempotent SQL scripts
│       └── 001_initial_schema.sql       #   (generated from EF migrations via `dotnet ef migrations script`)
│
├── src/
│   ├── Portal.Web/                      # Blazor Server — internal broker app
│   │   ├── Components/Pages/            # Razor components (pages)
│   │   ├── Hubs/                        # SignalR hubs (real-time policy updates)
│   │   ├── Services/                    # Typed HTTP/gRPC clients to backend services
│   │   ├── Program.cs
│   │   └── appsettings.json
│   │
│   ├── public-portal/                   # React + TypeScript — customer self-service
│   │   ├── src/
│   │   │   ├── api/                     # API client (fetch wrapper, auth handling)
│   │   │   ├── components/              # UI components
│   │   │   ├── App.tsx
│   │   │   └── main.tsx
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   └── vite.config.ts
│   │
│   ├── Services/
│   │   ├── Policy.Api/                  # REST API: quotes, policies, claims
│   │   │   ├── Controllers/
│   │   │   ├── Services/                # Application/service layer
│   │   │   ├── Validators/              # FluentValidation request validators
│   │   │   └── Program.cs
│   │   ├── Policy.Domain/               # Entities, domain rules (no dependencies)
│   │   │   └── Entities/
│   │   ├── Policy.Infrastructure/       # EF Core DbContext, EF migrations, repositories
│   │   │   └── Migrations/
│   │   └── Rating.Grpc/                 # gRPC microservice: premium rating engine
│   │       ├── Protos/
│   │       └── Services/
│   │
│   ├── Identity/
│   │   └── Portal.Identity/             # IdentityServer (Duende) — OIDC for both frontends
│   │
│   ├── Jobs/
│   │   └── Portal.Jobs/                 # Hangfire host: nightly premium calc, monthly reports
│   │       └── Jobs/
│   │
│   └── Shared/
│       ├── Portal.Shared.Contracts/     # Event contracts, DTOs shared across services
│       │   └── Events/
│       └── Portal.Shared.Kafka/         # Kafka producer/consumer abstractions
│
├── tests/
│   ├── Policy.Api.Tests/                # xUnit: domain, validators, service layer (SQLite)
│   ├── Policy.Api.IntegrationTests/     # WebApplicationFactory: endpoints, authz, outbox, jobs
│   └── Rating.Grpc.Tests/               # xUnit: rating engine rate-table behavior
│
├── .editorconfig                        # C# code style (enforced in CI via `dotnet format`)
├── .env.example                         # Local dev environment template
├── .gitignore
├── Directory.Build.props                # Shared MSBuild props (TFM, nullable, analyzers)
└── README.md
```

**Conventions**
- One deployable = one Dockerfile = one k8s manifest = one pipeline deploy job.
- `Policy.Domain` has zero project references; `Infrastructure` references Domain; `Api` references both. New services follow the same Domain/Infrastructure/Api split.
- Shared code only via `src/Shared/*` packages — never cross-service project references.

---

## 1.2 Implementation TODO List

### Phase 1: Foundation (high priority)
- [x] Create solution file and wire all projects (`dotnet new sln && dotnet sln add ...`)
- [x] Stand up local dev environment via `docker-compose` (SQL Server, Kafka)
- [x] Implement Policy domain model (Quote, Policy, Claim, Customer) + EF Core DbContext
- [x] Generate and apply initial EF migration; commit SQL script to `/migrations/sql`
- [x] Configure IdentityServer clients for Blazor (code flow), React (code + PKCE), service-to-service (client credentials)
- [ ] Wire interactive OIDC login end-to-end (login UI, EF stores, Key Vault signing keys) — local dev currently uses the API's header-driven Dev auth scheme
- [x] Policy.Api: CRUD for quotes/policies with FluentValidation + service layer
- [x] Azure DevOps CI pipeline: build, test, `dotnet format` check, ESLint, publish artifacts
- [x] Dockerfiles for all deployables; push to Azure Container Registry
- [x] AKS manifests: deployments, HPA, ingress, Key Vault CSI secrets, migration Job (`deploy/k8s/`)
- [ ] Provision the actual AKS clusters + variable group/service connection in Azure DevOps

### Phase 2: Core features (medium priority)
- [x] Broker workflows in Blazor: issue quote → bind policy → manage claims
- [x] Rating.Grpc premium calculation engine; called by Policy.Api during quoting
- [x] SignalR PolicyHub: push policy status changes to connected brokers (Azure SignalR Service backplane)
- [x] Kafka: publish `PolicyBound`, `ClaimFiled` events from Policy.Api; outbox pattern for atomicity
- [x] React public portal: customer sign-in, view policies, file claims & track claim status
- [x] Hangfire nightly premium recalculation job (idempotent, batched)
- [x] Hangfire monthly regulatory report job (generate → store in Azure Blob → notify)
- [x] CD pipeline: dev → staging (auto) → prod (manual approval gate)
- [x] Integration test suite (WebApplicationFactory + in-memory SQLite; fake rating client; recorded Kafka publisher)

### Phase 3: Polish & optimization (lower priority)
- [ ] Observability: OpenTelemetry traces/metrics → Azure Monitor; structured logging via Serilog
- [x] Performance groundwork: list-endpoint pagination (+`X-Total-Count`), response compression, reporting indexes (migration 002)
- [ ] Performance: response caching, EF compiled queries, read replicas for reporting
- [ ] Resilience: Polly retry/circuit-breaker policies on all HTTP/gRPC clients
- [ ] Horizontal pod autoscaling tuned per service; load testing with k6
- [ ] Blue/green or canary deployments via deployment strategies in AKS
- [x] Rate limiting (per-caller fixed window, configurable) + dependency vulnerability scan clean
- [ ] Security hardening: penetration test remediation (audit logging already in place)
- [ ] Accessibility (WCAG 2.1 AA) pass on React portal
- [ ] DR runbook: SQL Server failover groups, Kafka topic replication, RPO/RTO validation
