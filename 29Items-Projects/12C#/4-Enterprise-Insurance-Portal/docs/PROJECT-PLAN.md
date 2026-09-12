# Enterprise Insurance Portal - Project Plan

## 1.1 Project File Structure (Code + CI + Tools)

Below is the proposed repository layout to ensure logical separation of concerns, scalability, and maintainability:

```text
/
├── .github/
│   └── workflows/            # GitHub Actions CI/CD pipelines
├── docs/                     # Architectural and planning documentation
├── migrations/               # Raw SQL migrations (if not relying solely on EF Core)
├── src/
│   ├── backend/              # .NET 8 Backend services
│   │   ├── EnterpriseInsurance.Api/            # Main ASP.NET Core API / gRPC hosts
│   │   ├── EnterpriseInsurance.Blazor/         # Blazor Server internal app (Brokers)
│   │   ├── EnterpriseInsurance.Core/           # Domain entities, interfaces, and business logic
│   │   ├── EnterpriseInsurance.Infrastructure/ # EF Core, DB Contexts, External Services (Kafka, Hangfire)
│   │   ├── EnterpriseInsurance.IdentityServer/ # Identity authentication and authorization
│   │   └── EnterpriseInsurance.Tests/          # xUnit tests (Unit and Integration)
│   ├── frontend/             # React public portal
│   │   └── public-portal/
│   │       ├── public/
│   │       ├── src/
│   │       │   ├── api/      # API client/services (REST/SignalR)
│   │       │   ├── components/ # Reusable UI components
│   │       │   ├── pages/    # Route components
│   │       │   └── store/    # State management
│   │       ├── .eslintrc.json
│   │       ├── package.json
│   │       └── tsconfig.json
├── docker-compose.yml        # Local development infrastructure (SQL Server, Kafka, Redis)
├── Dockerfile                # Multi-stage build for backend
├── .env.example              # Environment variables template
└── README.md                 # Project root documentation
```

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)
- [ ] Initialize Git repository and standard `.gitignore`.
- [ ] Scaffold .NET 8 Backend projects (Api, Core, Infrastructure, Blazor).
- [ ] Scaffold React public portal (`npx create-react-app public-portal --template typescript` or Vite equivalent).
- [ ] Set up local development infrastructure via `docker-compose` (SQL Server, Kafka).
- [ ] Configure Entity Framework Core with SQL Server and run initial migrations.
- [ ] Setup IdentityServer for centralized authentication.
- [ ] Create basic CI/CD pipeline definition for building and testing.

### Phase 2: Core Features (Medium Priority)
- [ ] Implement Policy Management CRUD APIs using .NET Web API.
- [ ] Develop Broker UI using Blazor Server for policy binding and issuing quotes.
- [ ] Develop Customer Public Portal using React for self-service functionality.
- [ ] Integrate SignalR for real-time policy updates from backend to Blazor/React clients.
- [ ] Set up Hangfire to run nightly premium calculations.
- [ ] Configure Kafka producers and consumers for streaming policy events (e.g., Policy Created, Claim Filed).
- [ ] Implement gRPC connections between microservices (if system is broken down further).

### Phase 3: Polish & Optimization (Lower Priority)
- [ ] Increase xUnit code coverage for business logic and critical APIs.
- [ ] Complete End-to-End testing for React using Cypress or Playwright.
- [ ] Set up robust telemetry, logging, and monitoring (e.g., Application Insights, Serilog).
- [ ] Finalize infrastructure as code (Terraform/Bicep) for Azure AKS deployment.
- [ ] Fine-tune RBAC (Role-Based Access Control) rules.
- [ ] Load testing for concurrent SignalR connections and Kafka event throughput.
