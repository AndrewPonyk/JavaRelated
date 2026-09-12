# Scientific Computing Platform — developer entry points.
# Requires: uv (https://docs.astral.sh/uv), node 22+, docker.
# Windows users: run these under Git Bash, or copy the commands directly.

.PHONY: install lint fmt typecheck test test-fe dev-api dev-fe worker up down migrate notebook

install:            ## Sync Python workspace + frontend deps
	uv sync --all-packages
	cd frontend && npm install

lint:               ## Ruff + eslint
	uv run ruff check .
	uv run ruff format --check .
	cd frontend && npm run lint

fmt:                ## Auto-format everything
	uv run ruff format .
	uv run ruff check --fix .
	cd frontend && npm run format

typecheck:          ## mypy + tsc
	uv run mypy backend/app libs/sciengine/src
	cd frontend && npx tsc --noEmit

test:               ## All Python tests with coverage
	uv run pytest libs/sciengine/tests --cov=sciengine
	uv run pytest backend/tests --cov=app

test-fe:            ## Frontend tests
	cd frontend && npm test

dev-api:            ## Run the API with reload (expects db/redis via `make up`)
	cd backend && uv run uvicorn app.main:app --reload --port 8000

dev-fe:             ## Run the Vite dev server (proxies /api to :8000)
	cd frontend && npm run dev

worker:             ## Run a compute worker (threads pool — sandbox does the heavy math)
	cd backend && uv run celery -A app.workers.celery_app worker -Q compute --pool=threads --concurrency=4 -l info

up:                 ## Start local data stores
	docker compose up -d db redis

down:               ## Stop the local stack
	docker compose down

migrate:            ## Apply DB migrations
	cd backend && uv run alembic upgrade head

notebook:           ## Local Jupyter Lab with sciengine importable
	uv run --with jupyterlab jupyter lab --notebook-dir=notebooks
