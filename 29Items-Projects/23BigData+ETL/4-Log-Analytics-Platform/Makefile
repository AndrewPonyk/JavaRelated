# Log Analytics Platform — developer entrypoints.
# Windows: run via Git Bash / WSL (make ships with Git for Windows' toolchain or `choco install make`).

PY ?= python

.PHONY: help install lint fmt typecheck test test-int test-e2e cov up down logs \
        migrate topics seed gateway api alerting train pipeline app build-images clean

help: ## list targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "};{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

install: ## editable install + dev tooling + local pyspark
	$(PY) -m pip install -e ".[dev]" -r requirements-spark.txt
	pre-commit install

lint: ## ruff lint + format check
	ruff check src tests scripts
	ruff format --check src tests scripts

fmt: ## auto-format
	ruff format src tests scripts
	ruff check --fix src tests scripts

typecheck: ## mypy
	mypy src

test: ## unit tests (fast, no services)
	$(PY) -m pytest

test-int: ## integration tests (docker compose stack must be up)
	$(PY) -m pytest -m integration

test-e2e: ## end-to-end smoke (full pipeline running)
	$(PY) -m pytest -m e2e

cov: ## unit tests with coverage report
	$(PY) -m pytest --cov --cov-report=term-missing

up: ## start local infra (Kafka, OpenSearch, Dashboards, Redis)
	docker compose up -d kafka opensearch dashboards redis

down: ## stop local stack
	docker compose down

logs: ## tail local stack logs
	docker compose logs -f --tail=100

migrate: ## apply search-schema migrations
	$(PY) scripts/es_migrate.py

topics: ## create Kafka topics
	$(PY) scripts/create_kafka_topics.py

seed: ## generate sample log traffic through the gateway
	$(PY) scripts/seed_sample_logs.py --count 500 --burst-errors

gateway: ## run ingestion gateway locally
	uvicorn log_analytics.ingestion.gateway:app --port 8080 --reload

api: ## run query/admin API locally
	uvicorn log_analytics.api.main:app --port 8000 --reload

alerting: ## run alerting engine locally
	$(PY) -m log_analytics.alerting.engine

train: ## bootstrap/retrain the anomaly model (synthetic source; see ml/train.py --help)
	$(PY) -m log_analytics.ml.train --source synthetic

app: ## run gateway/api/alerting as containers (includes init: migrations+topics)
	docker compose --profile app up -d --build

pipeline: ## run the three Spark streaming jobs as containers (train first!)
	docker compose --profile pipeline up -d --build

build-images: ## build service + spark images
	docker build -f docker/app.Dockerfile -t log-analytics/app:local .
	docker build -f docker/spark.Dockerfile -t log-analytics/spark:local .

clean: ## remove caches and build artifacts
	rm -rf .pytest_cache .mypy_cache .ruff_cache .coverage coverage.xml htmlcov build dist
