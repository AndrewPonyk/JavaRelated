# Task shortcuts. On Windows run via Git Bash (make from Git for Windows / scoop),
# or copy the underlying commands directly — they are all plain python/docker calls.
.PHONY: install lint format typecheck test test-all run db-up db-down migrate seed

install:            ## dev environment + git hooks
	python -m pip install -r requirements-dev.txt
	pre-commit install

lint:
	ruff check .
	ruff format --check .

format:
	ruff check --fix .
	ruff format .

typecheck:
	mypy app

test:               ## fast suite — no database needed
	python -m pytest -m "not integration"

test-all:           ## needs TEST_DATABASE_URL (make db-up first)
	python -m pytest

run:
	streamlit run streamlit_app.py

db-up:              ## local PostgreSQL only
	docker compose -f docker/docker-compose.yml up -d db

db-down:
	docker compose -f docker/docker-compose.yml down

migrate:
	alembic upgrade head

seed:
	python scripts/seed_demo_data.py
