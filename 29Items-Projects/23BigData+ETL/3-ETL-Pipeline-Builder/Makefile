# ETL Pipeline Builder — developer entrypoints.
# On Windows without make, copy the underlying commands from each target.

.PHONY: help setup lint fmt test test-cov smoke up down seed dbt-build dbt-test ge-validate \
        fe-install fe-start fe-build fe-test dag-test compile-check

help:            ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}'

setup:           ## Install python dev deps + pre-commit hooks
	pip install -r requirements-dev.txt
	pre-commit install

lint:            ## Ruff + format check + mypy
	ruff check .
	ruff format --check .
	mypy api streaming glue

fmt:             ## Auto-fix lint + format
	ruff check --fix .
	ruff format .

test:            ## Unit tests (glue/airflow suites auto-skip without deps)
	pytest

test-cov:        ## Unit tests with coverage report
	pytest --cov=api/app --cov=streaming/processor --cov-report=term-missing

smoke:           ## End-to-end smoke against the running compose stack
	bash scripts/smoke_e2e.sh

up:              ## Local stack: kafka+redis+api+processor+frontend
	docker compose --profile apps up --build -d

down:            ## Stop the local stack
	docker compose --profile apps --profile airflow down

seed:            ## Produce synthetic order events into local kafka
	python scripts/seed_kafka_events.py --rate 20 --duration 60

dbt-build:       ## dbt deps + build (requires SNOWFLAKE_* env vars)
	cd dbt && dbt deps && dbt build --profiles-dir profiles

dbt-test:        ## dbt tests only
	cd dbt && dbt test --profiles-dir profiles

ge-validate:     ## Run the raw orders Great Expectations checkpoint
	python scripts/run_ge_checkpoint.py --checkpoint raw_orders_checkpoint

fe-install:      ## Frontend dependencies
	cd frontend && npm ci

fe-start:        ## Frontend dev server (proxies /api to :8000)
	cd frontend && npm start

fe-build:        ## Frontend production build
	cd frontend && npm run build

fe-test:         ## Frontend unit tests (headless)
	cd frontend && npm test -- --watch=false --browsers=ChromeHeadless

dag-test:        ## Airflow DAG integrity tests (needs apache-airflow installed)
	pytest airflow/tests -q

compile-check:   ## Fast syntax check of all python sources
	python -m compileall -q api streaming glue airflow scripts
