.DEFAULT_GOAL := help
.PHONY: help install dev up down logs seed migrate test test-int lint fmt build clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS=":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Install backend + frontend dev dependencies
	cd backend && pip install -r requirements-dev.txt
	cd frontend && npm install

dev: ## Run the full stack locally (docker compose, build + reload)
	docker compose up --build

up: ## Start the stack in the background
	docker compose up -d

down: ## Stop the stack
	docker compose down

logs: ## Tail stack logs
	docker compose logs -f

migrate: ## Apply Neo4j constraints/indexes
	python scripts/seed_neo4j.py --schema-only

seed: ## Apply schema + seed reference data
	python scripts/seed_neo4j.py

test: ## Run backend unit tests
	cd backend && pytest -m "not integration"

test-int: ## Run backend integration tests (needs Neo4j)
	cd backend && pytest -m integration

lint: ## Lint backend + frontend
	cd backend && ruff check . && black --check .
	cd frontend && npm run lint

fmt: ## Auto-format backend + frontend
	cd backend && ruff check --fix . && black .
	cd frontend && npm run format

build: ## Build backend + frontend images
	docker build -t dic-backend ./backend
	docker build -t dic-frontend ./frontend

clean: ## Remove caches and build artifacts
	find . -type d -name __pycache__ -prune -exec rm -rf {} + 2>/dev/null || true
	rm -rf backend/.pytest_cache backend/.mypy_cache backend/.ruff_cache frontend/dist
