# Healthcare Claims Processing - Makefile
# Common development commands

.PHONY: help dev-up dev-down backend frontend test build deploy clean

# Default target
help:
	@echo "Healthcare Claims Processing - Available Commands"
	@echo ""
	@echo "Development:"
	@echo "  make dev-up       - Start local development environment"
	@echo "  make dev-down     - Stop local development environment"
	@echo "  make backend      - Start backend in dev mode"
	@echo "  make frontend     - Start frontend in dev mode"
	@echo ""
	@echo "Testing:"
	@echo "  make test         - Run all tests"
	@echo "  make test-backend - Run backend tests"
	@echo "  make test-frontend- Run frontend tests"
	@echo ""
	@echo "Build:"
	@echo "  make build        - Build all components"
	@echo "  make build-native - Build native executable"
	@echo "  make docker       - Build Docker images"
	@echo ""
	@echo "Deployment:"
	@echo "  make deploy-dev   - Deploy to development"
	@echo "  make deploy-prod  - Deploy to production"
	@echo ""
	@echo "Utilities:"
	@echo "  make clean        - Clean build artifacts"
	@echo "  make migrate      - Run database migrations"
	@echo "  make lint         - Run linters"

# =============================================================================
# Development
# =============================================================================

dev-up:
	docker-compose -f infrastructure/docker/docker-compose.dev.yml up -d
	@echo "Development environment started"
	@echo "PostgreSQL: localhost:5432"
	@echo "Kafka: localhost:9092"
	@echo "Elasticsearch: localhost:9200"
	@echo "Redis: localhost:6379"

dev-down:
	docker-compose -f infrastructure/docker/docker-compose.dev.yml down

dev-up-tools:
	docker-compose -f infrastructure/docker/docker-compose.dev.yml --profile tools up -d

backend:
	cd backend && ./mvnw quarkus:dev

frontend:
	cd frontend && npm start

# =============================================================================
# Testing
# =============================================================================

test: test-backend test-frontend

test-backend:
	cd backend && ./mvnw test

test-backend-integration:
	cd backend && ./mvnw verify -DskipUnitTests

test-frontend:
	cd frontend && npm run test:ci

# =============================================================================
# Build
# =============================================================================

build: build-backend build-frontend

build-backend:
	cd backend && ./mvnw package -DskipTests

build-native:
	cd backend && ./mvnw package -Pnative -DskipTests

build-frontend:
	cd frontend && npm ci && npm run build:prod

docker:
	docker build -t healthcare-claims/backend:latest ./backend
	docker build -t healthcare-claims/frontend:latest ./frontend

# =============================================================================
# Deployment
# =============================================================================

deploy-dev:
	cd infrastructure/terraform && \
	terraform init && \
	terraform apply -var-file="environments/dev/terraform.tfvars" -auto-approve

deploy-prod:
	@echo "Production deployment requires manual approval"
	cd infrastructure/terraform && \
	terraform init && \
	terraform plan -var-file="environments/prod/terraform.tfvars"

# =============================================================================
# Database
# =============================================================================

migrate:
	docker run --rm \
		-v $(PWD)/migrations:/flyway/sql \
		--network host \
		flyway/flyway:latest \
		-url=jdbc:postgresql://localhost:5432/claims \
		-user=claims_user \
		-password=claims_password \
		migrate

migrate-info:
	docker run --rm \
		-v $(PWD)/migrations:/flyway/sql \
		--network host \
		flyway/flyway:latest \
		-url=jdbc:postgresql://localhost:5432/claims \
		-user=claims_user \
		-password=claims_password \
		info

# =============================================================================
# Utilities
# =============================================================================

lint: lint-backend lint-frontend

lint-backend:
	cd backend && ./mvnw checkstyle:check spotbugs:check

lint-frontend:
	cd frontend && npm run lint

clean:
	cd backend && ./mvnw clean
	cd frontend && rm -rf node_modules dist .angular
	docker-compose -f infrastructure/docker/docker-compose.dev.yml down -v

# =============================================================================
# Setup
# =============================================================================

setup: setup-backend setup-frontend

setup-backend:
	cd backend && ./mvnw dependency:go-offline

setup-frontend:
	cd frontend && npm ci
