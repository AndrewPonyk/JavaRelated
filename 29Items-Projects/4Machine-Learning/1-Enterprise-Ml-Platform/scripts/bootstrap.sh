#!/usr/bin/env bash
# One-shot local bootstrap: deps, env file, DB schema, infra services.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[ -f .env ] || cp .env.example .env

echo "==> Backend deps"
python -m pip install -r backend/requirements-dev.txt

echo "==> Infra (postgres, redis, mlflow)"
docker compose up -d postgres redis mlflow

echo "==> DB migrations"
( cd backend && alembic upgrade head )

echo "==> Frontend deps"
( cd frontend && npm install )

echo "Done. Run the API:  cd backend && uvicorn src.api.main:app --reload"
echo "Run the UI:        cd frontend && npm run dev"
