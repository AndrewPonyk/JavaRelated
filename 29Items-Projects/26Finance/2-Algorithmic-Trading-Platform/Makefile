# Developer entry points. `make help` lists targets.
.DEFAULT_GOAL := help
PYTHON ?= python

# Services import each other (and trading_common) via PYTHONPATH — the same layout
# pytest and the Docker image use. trading_common's third-party deps come from
# `make install`.
export PYTHONPATH := shared:services/api-gateway/src:services/strategy-engine/src:services/risk-engine/src:services/market-data/src:services/ml-models/src:services/backtesting/src:services/pnl-projector/src:services/orchestrator/src

.PHONY: help install test lint typecheck cov run-api run-paper migrate \
        compose-up compose-down frontend-install frontend-test frontend-dev

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

install: ## Install trading_common (+ its 3rd-party deps) editable; services run via PYTHONPATH
	$(PYTHON) -m pip install -e "shared[dev]"

test: ## Run the full Python test suite with coverage
	$(PYTHON) -m pytest

lint: ## Lint with ruff
	ruff check shared services

typecheck: ## Type-check with mypy
	mypy shared services

cov: ## Run tests and write an HTML coverage report
	$(PYTHON) -m pytest --cov-report=html

run-api: ## Run the API gateway (SQLite by default)
	uvicorn api_gateway.asgi:app --reload --port 8000

run-paper: ## Run a single-process paper-trading session
	$(PYTHON) -m orchestrator.main

migrate: ## Apply DB migrations (needs DATABASE_URL)
	$(PYTHON) scripts/migrate.py

compose-up: ## Build and start the full stack (postgres + api + orchestrator + UI)
	docker compose up --build

compose-down: ## Stop the stack and remove volumes
	docker compose down -v

frontend-install: ## Install frontend deps
	cd frontend && npm install

frontend-test: ## Run frontend unit tests
	cd frontend && npm run test -- --run

frontend-dev: ## Run the frontend dev server
	cd frontend && npm run dev
