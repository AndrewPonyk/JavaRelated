# Common developer tasks. Run `make help` for the list.
.DEFAULT_GOAL := help

.PHONY: help install lint typecheck test run migrate db-up db-down fmt frontend-dev

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

install: ## Install backend (dev) + frontend deps
	cd backend && pip install -e ".[dev]"
	cd frontend && npm install

lint: ## Lint backend + frontend
	cd backend && ruff check . && ruff format --check .
	cd frontend && npm run lint

typecheck: ## Typecheck backend + frontend
	cd backend && mypy app
	cd frontend && npm run typecheck

test: ## Run backend tests
	cd backend && pytest

fmt: ## Auto-format backend
	cd backend && ruff format . && ruff check --fix .

db-up: ## Start local Postgres
	docker compose up -d postgres

db-down: ## Stop local stack
	docker compose down

migrate: ## Apply DB migrations
	cd backend && alembic upgrade head

run: ## Run the API with reload
	cd backend && uvicorn app.main:app --reload

frontend-dev: ## Run the Next.js dev server
	cd frontend && npm run dev
