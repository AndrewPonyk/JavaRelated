#!/usr/bin/env bash
# End-to-end smoke test against the running compose stack (kafka+redis+api+processor):
#   seed events → assert live metrics, rolling history, pipeline CRUD round-trip.
# Used by CI's integration job and `make smoke`. Requires: curl, python3 (aiokafka).
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

API="${API_URL:-http://localhost:8000}"

fail() { echo "SMOKE FAIL: $1" >&2; docker compose logs --tail 40 api processor >&2 || true; exit 1; }

echo "── waiting for API health..."
for _ in $(seq 1 45); do
  if curl -sf "$API/healthz" >/dev/null 2>&1; then break; fi
  sleep 2
done
curl -sf "$API/healthz" >/dev/null || fail "API never became healthy"

echo "── seeding events (12s @ 40/s)..."
python scripts/seed_kafka_events.py --rate 40 --duration 12 >/dev/null

echo "── asserting live metrics..."
METRICS_OK=0
for _ in $(seq 1 15); do
  COUNT=$(curl -sf "$API/api/v1/metrics/current" | python -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)
  if [ "$COUNT" -ge 4 ]; then METRICS_OK=1; break; fi
  sleep 2
done
[ "$METRICS_OK" = 1 ] || fail "expected >=4 live metrics, got $COUNT"

echo "── asserting rolling history..."
HIST=$(curl -sf "$API/api/v1/metrics/orders_per_second/history?granularity=live&limit=50" \
  | python -c "import json,sys; print(len(json.load(sys.stdin)))")
[ "$HIST" -ge 2 ] || fail "expected live history points, got $HIST"

echo "── pipeline CRUD round-trip..."
CREATED=$(curl -sf -X POST "$API/api/v1/pipelines" -H "Content-Type: application/json" \
  -d '{"name":"smoke-check","schedule":"0 2 * * *","source":"s3://lake/raw/","target":"analytics.marts.fct_business_metrics_daily"}')
PID=$(echo "$CREATED" | python -c "import json,sys; print(json.load(sys.stdin)['id'])")
curl -sf "$API/api/v1/pipelines/$PID" >/dev/null || fail "created pipeline not readable"
STATUS=$(curl -sf -X PATCH "$API/api/v1/pipelines/$PID" -H "Content-Type: application/json" \
  -d '{"status":"active"}' | python -c "import json,sys; print(json.load(sys.stdin)['status'])")
[ "$STATUS" = "active" ] || fail "status toggle failed"
curl -sf -X DELETE "$API/api/v1/pipelines/$PID" >/dev/null || fail "delete failed"

echo "── alerts endpoint reachable..."
curl -sf "$API/api/v1/alerts" >/dev/null || fail "alerts endpoint failed"

echo "SMOKE PASS ✅  (live metrics, history, pipeline CRUD, alert feed)"
