# Enterprise Insurance Portal — Technical Notes

## 3.1 CI/CD Pipeline Design (Azure DevOps)

Multi-stage YAML pipeline (`.azuredevops/azure-pipelines.yml`), one pipeline per repo, reusable templates per stack:

```
Stage: CI
  ├── dotnet format --verify-no-changes        (lint C#)
  ├── eslint + tsc --noEmit                    (lint React)
  ├── dotnet build -warnaserror
  ├── dotnet test (unit + integration, SQLite — no Docker needed on agents)
  ├── coverage gate: ≥70% line coverage on core assemblies (reportgenerator)
  └── az acr build × 7 images → ACR (tagged with BuildId + git SHA)

Stage: Deploy-Dev      → auto on main merge; kustomize apply into aks dev namespace
Stage: Deploy-Staging  → auto after dev rollout + smoke tests; EF migration Job runs first
Stage: Deploy-Prod     → manual approval gate (environment check); same promoted images
                         (canary 10% → 100% is a Phase 3 roadmap item)
```

Key rules:
- Images are built **once** in CI and promoted across environments — never rebuilt per stage.
- EF migrations ship as a self-contained bundle image (`Migrations.Dockerfile`) and run as a k8s Job *before* the new pods roll out; migrations must be backward-compatible with the previous app version (expand/contract pattern).
- Pipeline auth to AKS/ACR via workload identity federation — no service-principal secrets.

## 3.2 Testing Strategy

| Layer | Tooling | Target |
|---|---|---|
| Unit | xUnit + NSubstitute + FluentAssertions | Domain logic & services ≥ 80%; don't chase coverage on controllers/DTOs |
| Integration | xUnit + `WebApplicationFactory` + in-memory SQLite (fake gRPC rating client, recorded Kafka publisher) | Every API endpoint happy-path + key failure modes; authorization scoping; outbox relay; Hangfire job logic |
| Contract | Proto files are the gRPC contract — `buf breaking` check in CI; OpenAPI diff for REST | No breaking changes without version bump |
| E2E | Playwright (covers React portal and the critical Blazor broker flow: quote → bind) | ~10 critical journeys only; run nightly + pre-prod, not per-PR |
| Frontend unit | Vitest + React Testing Library | Components with logic; skip pure presentational ones |
| Load | k6 against staging | Quote/bind endpoints, SignalR fan-out, nightly job duration |

Principles: test behavior through public APIs, not internals; integration tests run the real DI/middleware/EF pipeline on SQLite so they need no Docker on dev machines or CI agents (SQL Server-specific behavior is exercised in staging via the migration Job + smoke tests; add a Testcontainers suite if provider-specific bugs ever surface); Blazor components tested with bUnit where logic warrants it.

## 3.3 Deployment Strategy

- **Containers:** multi-stage Dockerfiles (SDK build → `mcr.microsoft.com/dotnet/aspnet:8.0` runtime); React built to static files served by nginx. Non-root users, read-only filesystems.
- **AKS:** one namespace per environment (`insurance-dev/-staging/-prod`); a kustomize base in `deploy/k8s/` (image tags pinned per deploy). HPA per deployment; PodDisruptionBudgets for zero-downtime node upgrades.
- **Blazor/SignalR specifics:** Azure SignalR Service offloads WebSocket connections so Blazor pods can scale and roll without dropping circuits en masse; ingress session affinity enabled.
- **Stateful dependencies are managed services:** Azure SQL Managed Instance (or SQL Server on Azure VMs with AGs), Confluent Cloud / Event Hubs Kafka surface — don't run these in-cluster.
- **Rollout:** RollingUpdate for all deployables, gated by `kubectl rollout status`; canary for Policy.Api in prod (ingress weight + auto-rollback) is the Phase 3 follow-up.

## 3.4 Environment Management

- **.NET:** `appsettings.json` (defaults, safe to commit) + `appsettings.{Environment}.json` + environment variables (win) — env vars are populated in AKS from ConfigMaps and Key Vault-backed secrets. `ASPNETCORE_ENVIRONMENT` drives selection.
- **React:** `VITE_*` variables baked at build time per environment, or a `config.json` fetched at startup for build-once-deploy-many (preferred — matches our image-promotion rule).
- **Local dev:** `docker-compose` brings up SQL Server + Kafka; `.env` (gitignored) from `.env.example`; user-secrets for anything sensitive (`dotnet user-secrets`).
- **Local auth:** when `Identity:Authority` is empty, Policy.Api swaps JWT validation for a header-driven Dev authentication scheme (`X-Dev-Role`/`X-Dev-CustomerId`) — used by local frontends and the integration tests. Any environment that sets the authority gets real JWT bearer validation; the Dev handler is never registered there.
- **Never** commit real connection strings, API keys, or certs. CI enforces with secret scanning.

See [`.env.example`](../.env.example) at repo root for the local template.

## 3.5 Version Control Workflow

**Trunk-based development** with short-lived feature branches:

- Branch from `main`, PR back within 1–2 days, squash-merge. `main` is always releasable.
- Feature flags (e.g., `Microsoft.FeatureManagement`) decouple deploy from release for incomplete features.
- Releases are tags (`v1.4.0`), not long-lived branches; hotfix = branch from tag, fix, tag, cherry-pick to main.

**Why not Gitflow:** with images promoted through dev→staging→prod and feature flags available, release branches add merge overhead without safety. Trunk-based keeps integration pain small and continuous — important with a shared Blazor + microservices codebase where API contracts drift fast on long branches.

PR requirements: green CI, one reviewer (two for `Policy.Domain` and migrations), no decrease in coverage on touched files.

## 3.6 Common Pitfalls (this specific stack)

1. **Blazor Server circuit state and scale-out.** Every user holds a server circuit; a pod restart drops them. Use Azure SignalR Service, session affinity, and keep per-circuit state small. Don't treat Blazor pods like stateless REST pods in HPA settings.
2. **SignalR behind ingress:** WebSockets need explicit ingress annotations (timeouts, upgrade headers). Default 60s idle timeouts silently kill connections — set them ≥ the SignalR keep-alive interval.
3. **EF Core in Blazor Server:** a `DbContext` injected scoped lives as long as the *circuit* (potentially hours) → stale data and concurrency bugs. Use `IDbContextFactory<T>` and short-lived contexts per operation.
4. **Dual-write bug:** writing to SQL then publishing to Kafka without an outbox loses events on crashes. The transactional outbox is not optional here — regulatory reporting depends on event completeness.
5. **Hangfire on multiple pods:** all pods run the scheduler by default → duplicate nightly runs. Use `DisableConcurrentExecution`, idempotent jobs, and a dedicated Jobs deployment (replicas can be >1 only because jobs are idempotent and Hangfire uses distributed locks over SQL).
6. **gRPC on AKS:** HTTP/2 load balancing — L4 services pin all calls to one pod. Use a service mesh / Linkerd, or `GrpcChannel` with DNS round-robin + `LoadBalancingConfig`, or an L7 ingress for gRPC.
7. **IdentityServer key material:** default dev signing keys regenerate on restart → all tokens invalidated. Persist signing keys in Key Vault from day one, even in dev.
8. **Kafka consumer rebalances** during rolling deploys cause processing pauses; use cooperative-sticky assignor and keep `max.poll.interval` honest relative to actual processing time.
9. **EF migrations vs. running pods:** migrations must work with N and N+1 app versions simultaneously (expand/contract). Never drop/rename a column in the same release that stops using it.
10. **Nightly premium calc duration creep:** batch with checkpoints, monitor duration as an SLO; the job must finish before brokers start their day or it will contend with morning traffic.
