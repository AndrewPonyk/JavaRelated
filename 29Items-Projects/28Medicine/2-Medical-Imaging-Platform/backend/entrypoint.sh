#!/bin/sh
# Container entrypoint: wait for Postgres, optionally run migrations, then exec
# the given command (uvicorn for the API, the worker module for the worker).
set -e

echo "Waiting for database at ${POSTGRES_HOST:-postgres}:${POSTGRES_PORT:-5432}..."
python - <<'PY'
import os, socket, time
host = os.getenv("POSTGRES_HOST", "postgres")
port = int(os.getenv("POSTGRES_PORT", "5432"))
for _ in range(60):
    try:
        with socket.create_connection((host, port), timeout=2):
            break
    except OSError:
        time.sleep(1)
else:
    raise SystemExit(f"Database {host}:{port} not reachable after 60s")
print("Database is up.")
PY

if [ "${RUN_MIGRATIONS:-false}" = "true" ]; then
  echo "Applying database migrations..."
  alembic upgrade head
fi

exec "$@"
