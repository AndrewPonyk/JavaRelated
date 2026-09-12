# Developer entrypoints. `make help` lists targets.
.DEFAULT_GOAL := help
.PHONY: help up down install dev-api dev-web lint format test test-int migrate migration es-setup seed reindex

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## Start local stack (ES + PG + Redis + API)
	docker compose up -d --build

down: ## Stop local stack
	docker compose down

install: ## Install backend (editable+dev) and frontend deps
	cd backend && pip install -e ".[dev]"
	cd frontend && npm install

dev-api: ## Run the API locally with reload (expects compose infra up)
	cd backend && uvicorn app.main:app --reload --port 8000

dev-web: ## Run the frontend dev server (proxies /api -> :8000)
	cd frontend && npm run dev

lint: ## Lint & typecheck everything
	cd backend && ruff check . && ruff format --check . && mypy app
	cd frontend && npm run lint

format: ## Auto-format everything
	cd backend && ruff check --fix . && ruff format .
	cd frontend && npm run format

test: ## Fast tests (backend unit + frontend)
	cd backend && pytest tests/unit -q
	cd frontend && npm test

test-int: ## Backend integration/API tests
	cd backend && pytest tests/integration -q

migrate: ## Apply database migrations
	cd backend && alembic upgrade head

migration: ## Autogenerate a migration: make migration m="add xyz"
	cd backend && alembic revision --autogenerate -m "$(m)"

es-setup: ## Upload synonyms set + create index + alias
	python scripts/create_indexes.py

seed: ## Seed demo catalog into PG + ES
	python scripts/seed_products.py

reindex: ## Zero-downtime full reindex (alias swap)
	python scripts/reindex.py
