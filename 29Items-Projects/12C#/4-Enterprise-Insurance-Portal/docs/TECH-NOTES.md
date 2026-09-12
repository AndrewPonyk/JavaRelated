# Enterprise Insurance Portal - Technical Notes

## 3.1 CI/CD Pipeline Design

The CI/CD pipeline (targeted for Azure DevOps or GitHub Actions) should follow these stages:
1. **Code Checkout & Environment Setup:** Pull code, set up .NET 8 SDK, Node.js.
2. **Linting & Formatting:** 
   - Backend: `dotnet format --verify-no-changes`
   - Frontend: `npm run lint` (ESLint) and `npm run format:check` (Prettier).
3. **Build:** 
   - Backend: `dotnet build -c Release`
   - Frontend: `npm run build`
4. **Testing:** 
   - Backend: `dotnet test -c Release --no-build` (Unit and Integration).
   - Frontend: `npm run test` (Jest/RTL).
5. **Containerization (Docker):** Build Docker images for the API, Blazor App, and React App (via Nginx), then push to Azure Container Registry (ACR).
6. **Deploy to AKS:**
   - **Dev/Staging:** Automatically deployed on merge to `develop`.
   - **Prod:** Requires manual approval step after tagging a release.

## 3.2 Testing Strategy

- **Unit Testing Framework:** Use **xUnit** as the primary framework for .NET, combined with **Moq** for mocking dependencies and **FluentAssertions** for readable assertions. Minimum target coverage: 75% for business logic.
- **Integration Testing:** Use `WebApplicationFactory<TStartup>` to spin up an in-memory test server. Use Testcontainers to spin up a real SQL Server and Kafka instance in Docker for genuine integration testing.
- **Frontend Testing:** React Testing Library for component tests.
- **End-to-End Testing:** Use **Playwright** to simulate real browser interactions covering the entire flow from React -> API -> DB.

## 3.3 Deployment Strategy

- **Platform:** Azure Kubernetes Service (AKS).
- **Containerization:** All services are containerized. A multi-stage `Dockerfile` is used for the .NET backend to keep images lightweight. The React app is built and served via a lightweight Nginx container.
- **Infrastructure as Code (IaC):** Use Terraform or Azure Bicep to provision the AKS cluster, Azure SQL, ACR, and Azure Key Vault.

## 3.4 Environment Management

Configurations must vary by environment using standard `appsettings.json` cascading:
- `appsettings.Development.json` (Local)
- `appsettings.Staging.json` (Staging AKS)
- `appsettings.Production.json` (Prod AKS)
For local React development, we use `.env` files.

*(See the configuration stub for `.env.example`)*

## 3.5 Version Control Workflow

**Strategy: GitHub Flow**
- `main` branch is always deployable and represents production.
- Developers create short-lived feature branches (`feature/add-claims-api`) off `main`.
- Pull Requests require passing CI checks and at least one code review before merging.
- *Rationale:* Gitflow is too heavy for continuous delivery. GitHub Flow allows rapid iteration while keeping the trunk clean.

## 3.6 Common Pitfalls

- **Blazor Server State:** Blazor Server holds circuit state in memory. If deploying to multiple AKS pods, you *must* configure Redis for SignalR backplane and data protection keys, otherwise connections will drop on load balancing.
- **EF Core N+1 Queries:** Be cautious with lazy loading. Use `.Include()` and `.AsNoTracking()` appropriately to avoid database bottlenecks.
- **Kafka Exactly-Once Semantics:** Ensure message idempotency in consumers (Hangfire jobs / Event Handlers) as Kafka may deliver messages at-least-once.
