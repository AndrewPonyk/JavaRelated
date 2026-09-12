# Common dev tasks. Run `make help` for the list.
.DEFAULT_GOAL := help
.PHONY: help up down logs migrate test lint fmt seed

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS=":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## Start the local dev stack (Postgres, MinIO, API, worker, ML, web)
	docker compose up --build

down: ## Stop the stack
	docker compose down

logs: ## Tail service logs
	docker compose logs -f

migrate: ## Apply DB migrations
	cd backend && alembic upgrade head

test: ## Run backend tests
	cd backend && pytest

lint: ## Lint backend + frontend
	cd backend && ruff check . && mypy app
	cd frontend && npm run lint

fmt: ## Auto-format backend + frontend
	cd backend && ruff check --fix . && black .
	cd frontend && npm run format

seed: ## Push synthetic sample studies into the dev stack
	cd backend && python ../scripts/seed_sample_dicom.py
