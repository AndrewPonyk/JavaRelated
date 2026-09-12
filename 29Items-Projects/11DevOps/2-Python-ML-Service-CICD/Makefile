# Fraud Detection API — developer tasks.
# Requires: poetry, docker (for docker-* / compose-* targets), node 20+ (frontend).

.PHONY: install lint format typecheck test coverage run train seed migrate openapi \
        frontend-build docker-build compose-up compose-down

install:
	poetry install

lint:
	poetry run ruff check src tests

format:
	poetry run ruff format src tests
	poetry run ruff check --fix src tests

typecheck:
	poetry run mypy src

test:
	poetry run pytest

coverage:
	poetry run pytest --cov=fraud_detection --cov-report=term-missing --cov-report=xml --cov-fail-under=70

run:
	PYTHONPATH=src poetry run uvicorn fraud_detection.main:app --reload --port 8000

train:
	PYTHONPATH=src poetry run python -m fraud_detection.ml.train

seed:
	poetry run python scripts/seed_db.py --count 200

migrate:
	poetry run alembic -c migrations/alembic.ini upgrade head

openapi:
	PYTHONPATH=src poetry run python scripts/export_openapi.py

frontend-build:
	cd dashboard && npm install && npm run build

docker-build:
	docker build -t fraud-detection-api:local .

compose-up:
	docker compose up -d --build

compose-down:
	docker compose down -v
