#!/usr/bin/env bash
# Bring up the local stack and seed Neo4j.
set -euo pipefail

cd "$(dirname "$0")/.."

echo ">> Starting docker compose stack..."
docker compose up -d --build

echo ">> Waiting for Neo4j to become healthy..."
until [ "$(docker inspect -f '{{.State.Health.Status}}' dic-neo4j 2>/dev/null)" = "healthy" ]; do
  printf '.'
  sleep 3
done
echo " ready."

echo ">> Applying migrations + seed data..."
NEO4J_URI="${NEO4J_URI:-bolt://localhost:7687}" \
NEO4J_USER="${NEO4J_USER:-neo4j}" \
NEO4J_PASSWORD="${NEO4J_PASSWORD:-password}" \
  python scripts/seed_neo4j.py

cat <<'EOF'

Stack is up:
  Frontend : http://localhost:5173
  API docs : http://localhost:8000/docs
  Neo4j    : http://localhost:7474  (neo4j / password)
EOF
