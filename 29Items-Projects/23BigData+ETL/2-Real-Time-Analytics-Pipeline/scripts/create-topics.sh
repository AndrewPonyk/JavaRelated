#!/usr/bin/env bash
# Creates the RTAP Kafka topics. Runs in two modes:
#   host mode      — from your shell: wraps `docker compose exec kafka …`
#   container mode — as the compose kafka-init one-shot (INSIDE_KAFKA_CONTAINER=1)
# Mirrors kafka/topics.yaml — keep both in sync (topics.yaml is the source of truth).
set -euo pipefail

if [ "${INSIDE_KAFKA_CONTAINER:-}" = "1" ]; then
  BROKER=kafka:9092
  kt() { /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BROKER" "$@"; }
else
  BROKER=kafka:9092
  kt() { docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BROKER" "$@"; }
fi

create() {
  local topic=$1 partitions=$2
  shift 2
  local args=()
  for cfg in "$@"; do
    args+=(--config "$cfg")
  done
  echo "── creating ${topic} (${partitions} partitions)"
  kt --create --if-not-exists --topic "$topic" --partitions "$partitions" \
     --replication-factor 1 ${args[@]+"${args[@]}"}
}

create events.raw.v1          12 retention.ms=604800000
create events.raw.dlq.v1       3 retention.ms=1209600000
create events.raw.late.v1      3 retention.ms=604800000
create metrics.aggregates.v1  12 retention.ms=259200000
create alerts.anomalies.v1     3 retention.ms=2592000000
create ml.model-updates.v1     1 cleanup.policy=compact

echo
echo "── topics on ${BROKER}:"
kt --list
