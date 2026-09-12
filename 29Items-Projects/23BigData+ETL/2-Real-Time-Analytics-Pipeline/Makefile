# Real-Time Analytics Pipeline — local development shortcuts.
# Run `make help` for a summary. Windows users: run via Git Bash (`make` from MSYS/WSL).

SHELL := bash
COMPOSE := docker compose

.PHONY: help all up down ps logs topics seed train \
        build-streaming build-api submit-jobs \
        run-api fe-dev test test-verify fmt

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

all: build-streaming build-api up ## Build everything and start the full stack (jobs auto-submit)
	@echo "──────────────────────────────────────────────────────"
	@echo " Dashboard  http://localhost:5173   API  http://localhost:8080/swagger-ui.html"
	@echo " Flink UI   http://localhost:8081   Grafana  http://localhost:3000"
	@echo " Now seed events:  make seed"
	@echo "──────────────────────────────────────────────────────"

up: ## Start the full stack (topics+templates auto-created; jobs auto-submitted if jars exist)
	$(COMPOSE) up -d

down: ## Stop the stack (volumes preserved)
	$(COMPOSE) down

ps: ## Show stack status
	$(COMPOSE) ps -a

logs: ## Tail all container logs
	$(COMPOSE) logs -f --tail=100

topics: ## (Re)create Kafka topics from the host
	bash scripts/create-topics.sh

seed: ## Produce synthetic business events with anomaly bursts (Ctrl+C to stop)
	python scripts/seed-events.py --rate 25

train: ## Train + publish anomaly models from local history
	cd ml && python training/train_anomaly_model.py --metrics orders.completed payments.captured users.signup --publish

build-streaming: ## Build shaded Flink job jars (skip tests for speed)
	JAVA_HOME=$${JAVA_HOME:-C:/Programs/jdk-21.0.2} mvn -f streaming/pom.xml -DskipTests package

build-api: ## Build the analytics API jar
	JAVA_HOME=$${JAVA_HOME:-C:/Programs/jdk-21.0.2} mvn -f services/analytics-api/pom.xml -DskipTests package

submit-jobs: ## (Re)submit both Flink jobs (safe if already running)
	$(COMPOSE) up -d job-aggregation job-anomaly

run-api: ## Run the analytics API on the host instead of in compose
	cd services/analytics-api && mvn spring-boot:run

fe-dev: ## Run the React dashboard dev server on the host
	cd frontend && npm run dev

test: ## Fast test suites (units; no Docker needed)
	JAVA_HOME=$${JAVA_HOME:-C:/Programs/jdk-21.0.2} mvn -f streaming/pom.xml test
	JAVA_HOME=$${JAVA_HOME:-C:/Programs/jdk-21.0.2} mvn -f services/analytics-api/pom.xml test
	cd frontend && npm test
	cd ml && python -m pytest -q

test-verify: ## Full verification incl. Testcontainers ITs + coverage gates (needs Docker)
	JAVA_HOME=$${JAVA_HOME:-C:/Programs/jdk-21.0.2} mvn -f streaming/pom.xml verify
	JAVA_HOME=$${JAVA_HOME:-C:/Programs/jdk-21.0.2} mvn -f services/analytics-api/pom.xml verify

fmt: ## Format / lint what has an autofixer
	cd frontend && npm run lint -- --fix
	terraform -chdir=infra/terraform fmt -recursive
