# Developer entry points. On Windows use Git Bash or WSL (or run the underlying
# commands directly — each target is a thin, readable wrapper).

.PHONY: help up down bootstrap lint fmt test test-spark test-spark-docker smoke api-dev frontend-dev frontend-test dbt-build migrate clean

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-18s %s\n", $$1, $$2}'

up: ## Start local stack (MinIO, Kafka, Postgres, Trino, Airflow, API, Console)
	docker compose up -d --build

down: ## Stop local stack (keeps volumes)
	docker compose down

bootstrap: ## Topics + dbt profile (stack must be up; API migrates the DB itself)
	bash scripts/bootstrap_local.sh

lint: ## Static checks for all Python
	ruff check .
	ruff format --check .

fmt: ## Auto-format Python
	ruff check --fix .
	ruff format .

test: ## Fast tests + API coverage gate (no Spark)
	pytest -m "not spark" --cov=app --cov-report=term --cov-fail-under=70

test-spark: ## Spark tests with local pyspark (Linux/mac; flaky on some Windows setups)
	pytest -m spark

test-spark-docker: ## Spark tests inside the Linux runner image (works everywhere)
	bash scripts/run_spark_docker.sh tests

smoke: ## End-to-end pipeline smoke against the compose stack
	bash scripts/run_spark_docker.sh smoke

api-dev: ## Run catalog API with reload on :8000
	cd services/api && uvicorn app.main:app --reload --port 8000

frontend-dev: ## Run React console on :5173
	cd frontend && npm install && npm run dev

frontend-test: ## Frontend lint + unit tests + build
	cd frontend && npm install && npm run lint && npm test && npm run build

dbt-build: ## Build dbt marts against local Trino
	cd transform/dbt && dbt build --profiles-dir profiles

migrate: ## Apply catalog DB migrations (running the API in Docker does this automatically)
	alembic upgrade head

clean: ## Remove local build junk
	rm -rf .pytest_cache .ruff_cache .mypy_cache dist build
