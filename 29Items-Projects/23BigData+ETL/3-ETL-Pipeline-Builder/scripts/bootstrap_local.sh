#!/usr/bin/env bash
# Local bootstrap: kafka + redis up, topics created, .env in place.
set -euo pipefail
cd "$(dirname "$0")/.."

# Git Bash on Windows rewrites /opt/... args into C:/Programs/Git/opt/... before
# docker sees them; disable that conversion (no-op on Linux/macOS).
export MSYS_NO_PATHCONV=1

[ -f .env ] || { cp .env.example .env; echo "created .env from .env.example"; }

docker compose up -d kafka redis

echo "waiting for kafka to accept connections..."
for _ in $(seq 1 30); do
  if docker compose exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh \
      --bootstrap-server localhost:9092 >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

for topic in events.orders.v1 alerts.anomaly.v1 events.deadletter.v1; do
  docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --create --if-not-exists --topic "$topic" \
    --partitions 6 --replication-factor 1 \
    --bootstrap-server localhost:9092
done

echo
echo "✔ kafka (localhost:29092) + redis (localhost:6379) ready; topics created."
echo "next:"
echo "  docker compose --profile apps up --build     # api + processor + frontend"
echo "  python scripts/seed_kafka_events.py --rate 20"
