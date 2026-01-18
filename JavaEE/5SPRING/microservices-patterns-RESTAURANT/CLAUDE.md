# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FTGO (Food To Go) Application - Reference implementation from Chris Richardson's "Microservice Patterns" book. Demonstrates microservices architecture patterns for a restaurant ordering system.

## Build Commands

```bash
# Build Spring Cloud Contracts (required first)
./gradlew buildContracts

# Build all services
./gradlew assemble

# Full build with tests
./gradlew build

# Run specific test types
./gradlew test                    # Unit tests
./gradlew integrationTest         # Integration tests (requires Docker)
./gradlew componentTest           # BDD/Cucumber tests

# Run single test class
./gradlew :ftgo-order-service:test --tests "*.OrderServiceTest"

# Docker Compose operations
./gradlew :composeUp              # Start all services (note the colon)
./gradlew :composeDown            # Stop all services
./gradlew infrastructureComposeUp # Start only infrastructure (MySQL, Kafka, etc.)

# Kubernetes deployment
./deployment/kubernetes/scripts/kubernetes-deploy-all.sh
./deployment/kubernetes/scripts/kubernetes-delete-all.sh
```

## Architecture

### Microservices (all in ftgo-application/)

| Service | Port | Description |
|---------|------|-------------|
| ftgo-consumer-service | 8081 | Consumer profile management |
| ftgo-order-service | 8082 | Order creation/management, saga orchestration |
| ftgo-kitchen-service | 8083 | Kitchen ticket management |
| ftgo-restaurant-service | 8084 | Restaurant & menu management |
| ftgo-accounting-service | 8085 | Financial transactions (event sourced) |
| ftgo-order-history-service | 8086 | CQRS read model |
| ftgo-api-gateway | 8087 | REST API composition & routing |
| ftgo-api-gateway-graphql | 8088 | GraphQL gateway (Node.js/TypeScript) |

### Module Naming Convention
- `ftgo-{name}-service` - Service implementation
- `ftgo-{name}-service-api` - Public API/interfaces
- `ftgo-{name}-service-contracts` - Spring Cloud Contract definitions

### Service Package Structure
Each Java service follows this pattern:
- `domain/` - DDD aggregates and domain logic
- `messaging/` - Kafka message handlers
- `web/` - REST controllers
- `main/` - Spring Boot application class

### Key Patterns Implemented
- **Sagas**: `CreateOrderSaga`, `CancelOrderSaga`, `ReviseOrderSaga` in ftgo-order-service orchestrate distributed transactions
- **Event Sourcing**: Accounting Service uses Eventuate Client for event-sourced aggregates
- **CQRS**: Order History Service maintains a read-optimized view
- **Transactional Outbox**: Eventuate Tram CDC publishes domain events via Kafka

## Key Frameworks

- **Eventuate Tram** - Transactional messaging and saga orchestration
- **Eventuate Client** - Event sourcing
- **Spring Boot 2.2.6** - Service foundation
- **Spring Cloud Gateway** - API gateway implementation
- **gRPC/Protobuf** - Inter-service communication (Order Service)

## Infrastructure (Docker Compose)

- MySQL 5.7 (port 3306) - Per-service databases
- Kafka (port 9092) - Event streaming
- Zookeeper (port 2181) - Kafka coordination
- CDC Service (port 8099) - Change Data Capture for outbox pattern

## Testing

- Unit tests: `src/test/java/`
- Integration tests: `src/integration-test/java/` (custom source set)
- Component tests: `src/component-test/java/` (Cucumber BDD)
- End-to-end tests: `ftgo-end-to-end-tests/`

Services have Swagger UI at `http://localhost:{port}/swagger-ui/index.html`

## Environment Setup

If Docker is not accessible via localhost:
```bash
source ./set-env.sh  # Sets DOCKER_HOST_IP
```

## Additional Components

- **ftgo-consumer-web-ui/** - React frontend (separate npm project)
- **ftgo-monolith/** - Monolithic version for refactoring examples
- **buildSrc/** - Custom Gradle plugins (FtgoServicePlugin, IntegrationTestsPlugin, etc.)

## Notes

- Requires ~16GB RAM to run full stack
- All inter-service communication is asynchronous via Kafka (CDC-based)
- Each service has its own MySQL database (ftgo_{service}_service)
