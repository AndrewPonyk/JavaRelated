# Developer ergonomics. Run `make help` for the list of targets.
.DEFAULT_GOAL := help
.PHONY: help install dev lint format typecheck test test-all train run migrate seed bootstrap docker-up clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Install runtime deps + package (editable)
	pip install -r requirements.txt && pip install -e .

dev: ## Install dev deps + pre-commit hooks
	pip install -r requirements-dev.txt && pip install -e . && pre-commit install

lint: ## Run ruff
	ruff check .

format: ## Auto-format with black + ruff --fix
	black . && ruff check --fix .

typecheck: ## Run mypy
	mypy src

test: ## Run fast tests (exclude slow model training)
	pytest -m "not slow"

test-all: ## Run the full suite including slow tests
	pytest

train: ## Train a model from the configured data source
	python -m churn_predictor.models.train

run: ## Launch the Streamlit app locally
	streamlit run app/streamlit_app.py

migrate: ## Apply SQL migrations to $DATABASE_URL (cross-platform, no psql needed)
	python -m churn_predictor.db.migrate

seed: ## Seed the database with synthetic demo data (idempotent)
	python -c "from churn_predictor.bootstrap import seed_if_empty; print(seed_if_empty())"

bootstrap: ## One-shot: wait for DB, migrate, seed, and train a model
	python -m churn_predictor.bootstrap

docker-up: ## Start app + PostgreSQL via docker compose
	docker compose up --build

clean: ## Remove caches and build artifacts
	rm -rf .pytest_cache .mypy_cache .ruff_cache htmlcov .coverage build dist *.egg-info
