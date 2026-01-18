# Healthcare Claims Processing

A cloud-native healthcare claims processing system with automated adjudication, ML-powered fraud detection, and real-time integrations.

## Tech Stack

- **Backend**: Java 21, Quarkus 3, Hibernate Reactive
- **Database**: PostgreSQL 15
- **Message Broker**: Apache Kafka (Azure Event Hubs)
- **Search**: Elasticsearch 8
- **Cache**: Redis 7
- **Fraud Detection**: Python 3.11, FastAPI
- **Frontend**: Angular 17
- **API**: GraphQL + REST
- **Cloud**: Azure Functions, Azure AD B2C
- **IaC**: Terraform
- **CI/CD**: GitHub Actions

## Quick Start

### Prerequisites

- Java 21+
- Node.js 20+
- Docker & Docker Compose
- Maven 3.9+

### Local Development

1. **Start infrastructure services**:
   ```bash
   make dev-up
   ```

2. **Start backend** (in a new terminal):
   ```bash
   make backend
   ```

3. **Start frontend** (in a new terminal):
   ```bash
   make frontend
   ```

4. **Access the application**:
   - Frontend: http://localhost:4200
   - Backend API: http://localhost:8080
   - GraphQL Playground: http://localhost:8080/q/graphql-ui
   - API Docs: http://localhost:8080/q/swagger-ui
   - Fraud Detection API: http://localhost:8090/docs

### Running Tests

```bash
# All tests
make test

# Backend only
make test-backend

# Frontend only
make test-frontend

# Integration tests
make test-backend-integration
```

## Project Structure

```
healthcare-claims-processing/
├── backend/                    # Quarkus backend application
│   ├── src/main/java/         # Java source code
│   └── src/test/              # Tests
├── frontend/                   # Angular frontend application
├── fraud-detection-service/    # Python FastAPI ML service
│   ├── app/                   # FastAPI application
│   ├── Dockerfile
│   └── requirements.txt
├── migrations/                 # Flyway database migrations
├── infrastructure/
│   ├── docker/                # Docker Compose files
│   └── terraform/             # Infrastructure as Code
├── .github/workflows/          # CI/CD pipelines
└── docs/                       # Documentation
```

## Documentation

- [Project Plan](docs/PROJECT-PLAN.md) - Implementation roadmap and tasks
- [Architecture](docs/ARCHITECTURE.md) - System design and diagrams
- [Technical Notes](docs/TECH-NOTES.md) - Development guidelines

## API Examples

### REST API

```bash
# Submit a claim
curl -X POST http://localhost:8080/api/v1/claims \
  -H "Content-Type: application/json" \
  -d '{
    "type": "MEDICAL",
    "amount": 250.00,
    "serviceDate": "2024-01-15",
    "patientId": "uuid",
    "providerId": "uuid"
  }'

# Get claim by ID
curl http://localhost:8080/api/v1/claims/{id}
```

### GraphQL

```graphql
# Query claims
query {
  claims(pagination: { page: 0, size: 10 }) {
    edges {
      id
      claimNumber
      status
      amount
    }
  }
}

# Submit claim mutation
mutation {
  submitClaim(input: {
    type: MEDICAL
    amount: 250.00
    serviceDate: "2024-01-15"
    patientId: "uuid"
    providerId: "uuid"
  }) {
    id
    claimNumber
    status
  }
}
```

## Deployment

### Development

```bash
make deploy-dev
```

### Production

Production deployment requires approval through GitHub Actions workflow.

## Configuration

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Key configuration:
- Database connection
- Kafka/Event Hubs
- Azure AD credentials
- Fraud API endpoint

## Contributing

1. Create a feature branch from `main`
2. Make changes and add tests
3. Run linting: `make lint`
4. Submit a pull request

## License

Proprietary - All rights reserved
