# Dev shortcuts. On Windows, run via Git Bash (`make` from Git for Windows / scoop),
# or copy the underlying commands from here.

.PHONY: up down up-all migrate seed api worker downsampler fe test test-stack lint fmt

up:            ## start data plane only (cassandra, redis, influxdb, grafana)
	docker compose up -d cassandra redis influxdb grafana

up-all:        ## start everything including backend/workers/frontend
	docker compose --profile app up -d --build

down:
	docker compose --profile app down

migrate:
	./scripts/apply_migrations.sh

seed:
	python scripts/seed_demo_data.py

api:
	cd backend && uvicorn app.main:app --reload --port 8000

worker:
	cd backend && python -m app.workers.anomaly_worker

downsampler:
	cd backend && python -m app.workers.downsampler

fe:
	cd frontend && npm run dev

test:          ## fast suites (no databases needed)
	cd backend && python -m pytest tests -q --cov=app
	cd frontend && npm test

test-stack:    ## integration tests against live cassandra/redis (make up first)
	cd backend && python -m pytest tests/integration -q -m requires_stack

lint:
	cd backend && ruff check app tests && python -m mypy app
	cd frontend && npm run lint && npx tsc --noEmit

fmt:
	cd backend && ruff format app tests && ruff check --fix app tests
	cd frontend && npx prettier --write "src/**/*.{ts,tsx}"
