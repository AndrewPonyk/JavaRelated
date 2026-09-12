.PHONY: help dev up down logs test lint format typecheck migrate test-e2e clean

help:
	@echo "dev        — backend hot-reload (uvicorn --reload) + postgres via compose"
	@echo "up         — full stack: db zap sqlmap backend frontend"
	@echo "down       — stop stack"
	@echo "test       — backend unit/integration tests (SQLite, scanners mocked)"
	@echo "test-e2e   — e2e: boots full stack against vuln-lab target (slow)"
	@echo "lint       — ruff check + format check + eslint"
	@echo "migrate    — alembic upgrade head (or generate with REV=msg make revision)"

dev:
	docker compose up -d db zap sqlmap
	cd backend && uvicorn app.main:app --reload --port 8000

up:
	docker compose up --build -d

down:
	docker compose down

logs:
	docker compose logs -f backend

test:
	cd backend && pytest

lint:
	cd backend && ruff check app tests && ruff format --check app tests
	cd frontend && npm run lint

format:
	cd backend && ruff format app tests && ruff check --fix app tests

typecheck:
	cd backend && mypy app/services

migrate:
	cd backend && alembic upgrade head

revision:
	@if [ -z "$(m)" ]; then echo "usage: make revision m='description'"; exit 1; fi
	cd backend && alembic revision -m "$(m)" --autogenerate

# Full stack + vulnerable lab target, then real end-to-end scans over HTTP
test-e2e:
	docker compose --profile e2e up -d
	cd backend && WSS_E2E=1 pytest -m e2e --no-cov
	docker compose --profile e2e down

clean:
	docker compose down -v --remove-orphans
