# Enterprise Insurance Portal

Blazor Server internal app for brokers + React public portal for customers.
SignalR pushes real-time policy updates, gRPC connects the rating microservice,
Hangfire runs nightly premium recalculations and monthly regulatory reports, and
Kafka streams policy events through a transactional outbox.

## Run the full stack (Docker)

```bash
docker compose -f deploy/docker/docker-compose.yml up --build
```

| Endpoint | URL |
|---|---|
| Broker portal (Blazor) | http://localhost:5301 |
| Customer portal (React) | http://localhost:5173 |
| Policy API + Swagger | http://localhost:5101/swagger |
| Hangfire dashboard | http://localhost:5401/hangfire |

Walkthrough: open the broker portal → create a customer → issue a quote (the gRPC
rating engine prices it) → bind it into a policy. The policies screen updates live
via Kafka → SignalR. Then sign in to the customer portal with the customer's ID
(shown in the broker portal URL or API) to view the policy and file a claim.

> Local auth: without an OIDC authority configured, the API uses a header-driven
> Dev auth scheme (brokers by default; the React portal sends customer headers).
> Setting `Identity:Authority` switches the API to real JWT validation.

## Run from source

```bash
# Infrastructure only (SQL Server + Kafka)
docker compose -f deploy/docker/docker-compose.yml up -d sqlserver kafka

# Each in its own terminal:
dotnet run --project src/Services/Rating.Grpc      # gRPC rating engine :5201
dotnet run --project src/Services/Policy.Api       # REST API           :5101
dotnet run --project src/Portal.Web                # broker portal      :5301
dotnet run --project src/Jobs/Portal.Jobs          # Hangfire jobs      :5401
cd src/public-portal && npm install && npm run dev # customer portal    :5173
```

The API applies EF migrations on startup (`Database:MigrateOnStartup`). To manage
migrations manually:

```bash
dotnet tool restore
dotnet ef migrations add <Name> --project src/Services/Policy.Infrastructure
dotnet ef migrations script --idempotent --project src/Services/Policy.Infrastructure -o migrations/sql/001_initial_schema.sql
```

## Tests

```bash
dotnet test                          # 82 tests: domain, validators, services, API integration, jobs, rating engine
cd src/public-portal && npm test     # 7 React component + API client tests

# With the CI coverage profile (≥70% line coverage on core assemblies):
dotnet test --settings coverlet.runsettings --collect:"XPlat Code Coverage"
```

Integration tests boot the real API pipeline against in-memory SQLite — no Docker
required on dev machines or CI agents.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `docker compose up` fails on SQL Server health check | First pull/start can exceed the window — `docker compose up` again, or raise `start_period` in `deploy/docker/docker-compose.yml`. |
| API returns 503 on quote creation | Rating.Grpc isn't running or `Rating:GrpcAddress` is wrong. Start it (`dotnet run --project src/Services/Rating.Grpc`) — it must listen with HTTP/2 (its appsettings already configure h2c). |
| React portal gets CORS errors | The portal origin must be in `Cors:AllowedOrigins` (Policy.Api appsettings or `Cors__AllowedOrigins__0` env var). Default covers `http://localhost:5173`. |
| Customer portal shows no policies | Sign in with the customer's GUID (visible to brokers via `GET /api/v1/customers`); the API scopes results to that ID. |
| Live updates not appearing in the broker portal | Kafka isn't configured/running (`Kafka:BootstrapServers` empty disables the bridge — pages still work, just without live refresh). |
| `dotnet ef` says no project found | Run `dotnet tool restore` first and pass `--project src/Services/Policy.Infrastructure`. |
| Hangfire dashboard returns 401 | Outside Development it's local-requests-only by design. Set `ASPNETCORE_ENVIRONMENT=Development` locally (compose already does). |
| Tests fail with SQLite file locks on Windows | Re-run; ensure no stale `testhost` processes (`taskkill /im testhost.exe /f`). |

## Docs

- [Project Plan](docs/PROJECT-PLAN.md) — structure + implementation roadmap/status
- [Architecture](docs/ARCHITECTURE.md) — patterns, diagrams, data flow, security
- [API Reference](docs/API.md) — every endpoint with curl examples (Swagger at `/swagger` in dev)
- [Tech Notes](docs/TECH-NOTES.md) — CI/CD, testing, deployment, pitfalls

## CI/CD

`.azuredevops/azure-pipelines.yml`: lint → test (with a 70% coverage gate on core
assemblies) → `az acr build` for all seven images → dev (auto) → staging (auto) →
prod (manual approval). Deploys run the EF migration bundle as a k8s Job, then
`kubectl apply -k deploy/k8s` with images pinned to the immutable CI tag.
