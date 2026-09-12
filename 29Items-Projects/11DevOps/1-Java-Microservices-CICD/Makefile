# Developer shortcuts. On Windows run via Git Bash (`make` from Git for Windows/MSYS)
# or execute the underlying commands directly.

SERVICE_DIR := services/order-service
CHART_DIR   := deploy/helm/order-service
IMAGE       := order-service:local

.PHONY: help build test verify coverage lint run docker-build compose-up compose-down \
        helm-lint helm-template risk-score smoke

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}'

build: ## Compile without tests
	mvn -f $(SERVICE_DIR)/pom.xml -B -ntp -DskipTests package

test: ## Unit tests only
	mvn -f $(SERVICE_DIR)/pom.xml -B -ntp test

verify: ## Full local gate: checkstyle + tests + JaCoCo >=80%
	mvn -f $(SERVICE_DIR)/pom.xml -B -ntp verify

coverage: verify ## Open the JaCoCo HTML report path
	@echo "Report: $(SERVICE_DIR)/target/site/jacoco/index.html"

lint: ## Checkstyle only
	mvn -f $(SERVICE_DIR)/pom.xml -B -ntp checkstyle:check

run: ## Run the service locally (expects postgres up)
	mvn -f $(SERVICE_DIR)/pom.xml spring-boot:run

docker-build: ## Build the container image locally
	docker build -t $(IMAGE) $(SERVICE_DIR)

compose-up: ## Start local PostgreSQL
	docker compose up -d postgres

compose-down: ## Stop local stack
	docker compose down

helm-lint: ## Lint the chart with all value files
	helm lint $(CHART_DIR) -f $(CHART_DIR)/values.yaml -f $(CHART_DIR)/values-staging.yaml

helm-template: ## Render manifests locally
	helm template order-service $(CHART_DIR) -f $(CHART_DIR)/values-staging.yaml

risk-score: ## Demo run of the deployment risk model
	python tools/risk-score/risk_score.py --demo

smoke: ## Smoke-test a running instance (BASE_URL/MANAGEMENT_URL overridable)
	BASE_URL=$${BASE_URL:-http://localhost:8080} MANAGEMENT_URL=$${MANAGEMENT_URL:-http://localhost:8081} bash scripts/smoke-test.sh
