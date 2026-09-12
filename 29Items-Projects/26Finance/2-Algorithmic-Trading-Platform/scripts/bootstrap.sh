#!/usr/bin/env bash
# One-shot local dev bootstrap. Brings up infra, installs the Python workspace,
# applies migrations, and prints next steps.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> 1/5 Env file"
[[ -f .env ]] || cp .env.example .env

echo "==> 2/5 Local infra (postgres, redis, kafka, schema-registry)"
docker compose -f infra/docker/docker-compose.yml up -d

echo "==> 3/5 Python workspace (editable installs)"
python -m venv .venv
# shellcheck disable=SC1091
source .venv/bin/activate
pip install --upgrade pip
pip install -e shared
pip install -e "services/api-gateway[dev]" -e "services/strategy-engine[dev]"

echo "==> 4/5 Waiting for postgres, then applying migrations"
until docker compose -f infra/docker/docker-compose.yml exec -T postgres pg_isready -U trader >/dev/null 2>&1; do
  sleep 1
done
# shellcheck disable=SC1090
source .env
psql "$DATABASE_URL" -f db/migrations/V0001__initial_schema.sql
psql "$DATABASE_URL" -f db/migrations/V0002__strategies_and_signals.sql

echo "==> 5/5 Done. Next steps:"
echo "    uvicorn api_gateway.main:app --reload --port 8000   # API"
echo "    (cd frontend && npm install && npm run dev)          # UI"
