#!/usr/bin/env bash
# Create platform topics on the local broker (idempotent).
# Prod topics follow the same conventions on MSK (see infra/terraform/modules/msk).
set -euo pipefail

# Git Bash on Windows rewrites /opt/... arguments into host paths; disable that.
export MSYS_NO_PATHCONV=1

BROKER_CONTAINER="${BROKER_CONTAINER:-$(docker compose ps -q kafka)}"
KAFKA_TOPICS="/opt/kafka/bin/kafka-topics.sh"

create_topic() {
  local topic="$1" partitions="$2"
  docker exec "$BROKER_CONTAINER" "$KAFKA_TOPICS" \
    --bootstrap-server localhost:9092 \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor 1 \
    --config retention.ms=604800000   # 7 days — must exceed max ingestion outage
  echo "topic ready: $topic"
}

create_topic "orders.v1" 12
create_topic "orders.v1.dlq" 3
create_topic "platform.audit.v1" 3
