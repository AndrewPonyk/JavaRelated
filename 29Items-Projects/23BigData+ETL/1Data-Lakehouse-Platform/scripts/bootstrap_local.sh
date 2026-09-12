#!/usr/bin/env bash
# One-shot local environment bootstrap. Prereq: docker compose up -d --build
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Waiting for Postgres..."
until docker compose exec -T postgres pg_isready -U lakehouse -d catalog >/dev/null 2>&1; do
  sleep 2
done

echo "==> Creating Kafka topics..."
bash scripts/create_kafka_topics.sh

echo "==> dbt profile (local defaults)..."
if [ ! -f transform/dbt/profiles/profiles.yml ]; then
  cp transform/dbt/profiles/profiles.yml.example transform/dbt/profiles/profiles.yml
fi

echo "==> Catalog DB migrations run inside the api container on startup."
echo "    (Run 'alembic upgrade head' here instead if you develop the API outside Docker.)"

echo "==> Done. Next steps:"
echo "    scripts/run_spark_docker.sh smoke      # end-to-end pipeline smoke"
echo "    Console:  http://localhost:3000        API docs: http://localhost:8000/docs"
echo "    Airflow:  http://localhost:8085        MinIO:    http://localhost:9001"
echo "    Trino:    http://localhost:8081"
