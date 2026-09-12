#!/usr/bin/env bash
# Apply Cassandra migrations, in order, through the compose container.
# The CQL directory is mounted at /migrations by docker-compose.yml.
# Idempotent: every statement is IF NOT EXISTS (see backend/migrations/README.md).
#
# MSYS_NO_PATHCONV stops Git Bash on Windows from rewriting the in-container
# /migrations/... path into a host path; it is inert on Linux.
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

for file in backend/migrations/cassandra/*.cql; do
    name="$(basename "$file")"
    echo "==> applying $name"
    docker compose exec -T cassandra cqlsh -f "/migrations/$name"
done

echo "==> verifying keyspace"
docker compose exec -T cassandra cqlsh -e "USE tsa; DESCRIBE TABLES;"
echo "All migrations applied."
