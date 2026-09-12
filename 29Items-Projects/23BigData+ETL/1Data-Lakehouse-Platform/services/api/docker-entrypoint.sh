#!/bin/sh
# Apply catalog DB migrations, then start the API.
# Postgres may still be warming up when the container starts (compose
# health-checks cover the normal path) — retry briefly before giving up.
set -e

attempts=0
until alembic upgrade head; do
  attempts=$((attempts + 1))
  if [ "$attempts" -ge 10 ]; then
    echo "migrations failed after $attempts attempts" >&2
    exit 1
  fi
  echo "database not ready (attempt $attempts) — retrying in 3s..."
  sleep 3
done

exec "$@"
