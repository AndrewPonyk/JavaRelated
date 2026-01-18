# Enterprise Banking Platform

A reactive microservices-based banking system built with Java 21, Spring Boot 3, and WebFlux. Implements Event Sourcing and CQRS patterns with ML-based fraud detection.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3, WebFlux |
| Database | PostgreSQL with R2DBC |
| Messaging | Apache Kafka |
| Cache | Redis |
| Auth | Keycloak (OAuth2/JWT) |
| ML | CatBoost |
| Frontend | React 18, TypeScript, Vite |
| Infrastructure | Docker, Kubernetes (EKS), Terraform |
| CI/CD | Jenkins, SonarQube |
| Monitoring | Prometheus, Grafana |

## Services

- **account-service** - Account management with event sourcing
- **transaction-service** - Transaction processing with CQRS
- **loan-service** - Loan origination and management
- **fraud-detection-service** - ML-based fraud detection
- **api-gateway** - API routing and security
- **config-server** - Centralized configuration
- **discovery-service** - Service discovery (Eureka)

## Quick Start

### Prerequisites

- Java 21 (GraalVM recommended)
- Node.js 20+
- Docker & Docker Compose
- Maven 3.9+

### Local Development

1. **Configure environment variables:**
   ```bash
   # Copy the example environment file
   cp .env.example .env

   # Edit .env and set required values:
   # - POSTGRES_PASSWORD (required)
   # - KEYCLOAK_ADMIN_PASSWORD (required)
   # - GRAFANA_ADMIN_PASSWORD (required)
   ```

2. **Start infrastructure services:**
   ```bash
   docker-compose up -d postgres redis kafka zookeeper
   ```

3. **Run database migrations:**
   ```bash
   # Flyway migrations run automatically on service startup
   ```

4. **Start backend services:**
   ```bash
   # Start each service (or use docker-compose for all)
   cd services/account-service && mvn spring-boot:run
   ```

5. **Start frontend:**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

6. **Access the application:**
   - Frontend: http://localhost:3000
   - API Gateway: http://localhost:8080
   - Keycloak Admin: http://localhost:8180 (admin / your password)
   - Eureka Dashboard: http://localhost:8761
   - Swagger UI: http://localhost:8081/swagger-ui.html

### Using Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services (data persists in volumes)
docker-compose down

# Stop and remove all data
docker-compose down -v
```

### Keycloak Setup (First Time)

After starting services, configure Keycloak for authentication:

1. Open http://localhost:8180 and login with `admin` / your `KEYCLOAK_ADMIN_PASSWORD`
2. Create a new realm called `banking`
3. Create a client:
   - Client ID: `banking-client`
   - Client authentication: ON
   - Valid redirect URIs: `http://localhost:3000/*`
4. Create roles: `USER`, `ADMIN`
5. Create test users and assign roles

Your Keycloak data persists across `docker-compose down/up` cycles.

## Project Structure

```
enterprise-banking-platform/
├── docs/                    # Documentation
├── services/                # Backend microservices
│   ├── account-service/
│   ├── transaction-service/
│   ├── loan-service/
│   ├── fraud-detection-service/
│   ├── api-gateway/
│   ├── config-server/
│   └── discovery-service/
├── frontend/                # React frontend
├── infrastructure/          # K8s manifests, Terraform
├── migrations/              # Database migrations
├── ci/                      # CI/CD configurations
└── monitoring/              # Prometheus, Grafana configs
```

## Documentation

- [Project Plan](docs/PROJECT-PLAN.md) - Implementation roadmap
- [Architecture](docs/ARCHITECTURE.md) - System design
- [Technical Notes](docs/TECH-NOTES.md) - Development guidelines

## API Documentation

Swagger UI available at each service's `/swagger-ui.html` endpoint.

## Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn verify

# Frontend tests
cd frontend && npm test
```

## Deployment

See [TECH-NOTES.md](docs/TECH-NOTES.md) for deployment strategies.

## License

Proprietary - All rights reserved.
