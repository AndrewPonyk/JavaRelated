# API Reference (v1)

Base URL: `http://localhost:8000/api/v1` (interactive OpenAPI at `/docs` outside production).

Two credential planes (never mixed):
- **Users** send `Authorization: Bearer <JWT>` — can query and manage, can never ingest.
- **Devices** send `X-API-Key` — can only ingest. Either the shared gateway key
  (`DEVICE_API_KEY`) or a per-device key `<device_id>.<secret>` issued at registration.

All errors share one shape:

```json
{ "error": { "code": "not_found", "message": "Device not found", "request_id": "1f0c53a6b2d4" } }
```

`request_id` matches the `X-Request-ID` response header — quote it in bug reports.

---

## Auth

**POST `/auth/token`** — exchange credentials for a JWT.

```bash
curl -s -X POST http://localhost:8000/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "changeme-admin"}'
# → {"access_token":"eyJ...","token_type":"bearer","expires_in":1800,"roles":["admin"]}
export TOKEN="eyJ..."
```

Errors: `401 unauthorized` (bad credentials — same message whether the user exists),
`422 validation_error` (empty fields).

**GET `/auth/me`** — whoami for the presented token.

```bash
curl -s http://localhost:8000/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
# → {"subject":"admin","roles":["admin"]}
```

## Devices (JWT; mutations need `operator`/`admin` role)

**GET `/devices`** — list the registry.

**POST `/devices`** — register; response contains the API key **exactly once**.

```bash
curl -s -X POST http://localhost:8000/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name": "Boiler sensor", "site": "lviv-lab", "device_type": "thermo"}'
# → {"device_id":"dev-a1b2c3d4e5f6", ..., "api_key":"dev-a1b2c3d4e5f6.9f2e..."}
```

**GET `/devices/{device_id}`** — one device (`404 not_found` if unknown).

**PATCH `/devices/{device_id}/enabled?enabled=false`** — disable ingest (revokes the key's
power; the permission cache is invalidated immediately).

**DELETE `/devices/{device_id}`** — `204`; metric history expires via TTL on its own.

Role errors: `403 forbidden` for viewers on mutations; `401 unauthorized` without a token.

## Ingest (X-API-Key)

**POST `/ingest`** — batch of points; `202` with accepted/rejected counts.

```bash
curl -s -X POST http://localhost:8000/api/v1/ingest \
  -H "X-API-Key: dev-a1b2c3d4e5f6.9f2e..." -H "Content-Type: application/json" \
  -d '{"points": [
        {"device_id": "dev-a1b2c3d4e5f6", "metric": "temperature",
         "ts": "2026-07-02T12:00:00Z", "value": 21.5, "tags": {"fw": "1.4"}}
      ]}'
# → {"accepted": 1, "rejected": 0}
```

`rejected` counts points dropped by policy: older than `INGEST_MAX_AGE_HOURS`, ahead of the
clock-skew tolerance, or for unknown/disabled devices. Other outcomes:
`401` bad key · `403` per-device key used for another device's data ·
`413` batch over `MAX_INGEST_BATCH_SIZE` · `429` + `Retry-After: 60` over the per-device
rate limit · `503 backend_unavailable` when Cassandra is down (safe to retry).

`metric` must match `^[a-z][a-z0-9_.]*$`; naive timestamps are treated as UTC.

## Metrics (JWT)

**GET `/devices/{id}/metrics`** — metric names the device has ever reported.

**GET `/devices/{id}/metrics/{metric}?start=...&end=...`** — series for a range
(default: last hour). Ranges wider than `ROLLUP_QUERY_THRESHOLD_HOURS` (48h) are served
from hourly rollups; responses larger than `MAX_SERIES_POINTS` are stride-decimated and
flagged.

```bash
curl -s "http://localhost:8000/api/v1/devices/dev-a1b2c3d4e5f6/metrics/temperature?start=2026-07-02T00:00:00Z&end=2026-07-02T12:00:00Z" \
  -H "Authorization: Bearer $TOKEN"
# → {"device_id":"...","metric":"temperature","source":"raw","decimated":false,
#    "points":[{"ts":"2026-07-02T00:00:05Z","value":21.5}, ...]}
```

`422` when `start >= end`.

**GET `/devices/{id}/metrics/{metric}/live`** — current 1-minute window from Redis.

```bash
# → {"window_start":"2026-07-02T12:00:00Z","count":30,"sum":614.6,"avg":20.49,
#    "min":19.74,"max":21.19,"latest":20.19, ...}
```

## Anomalies (JWT)

**GET `/devices/{id}/anomalies?start=...&end=...&limit=100`** — newest first, default last 24h.

```bash
# → [{"metric":"power_w","ts":"...","value":24.5,"expected":118.8,
#     "lower":76.8,"upper":160.8,"score":6.73,"method":"zscore"}]
```

**GET `/events/anomalies?device_id=dev-...`** — Server-Sent Events stream of anomalies as
the worker publishes them (`data: <anomaly JSON>` frames, keep-alive comments every 15s).
Consume with fetch-streams or `curl -N`:

```bash
curl -N "http://localhost:8000/api/v1/events/anomalies?device_id=dev-a1b2c3d4e5f6" \
  -H "Authorization: Bearer $TOKEN"
```

## Health (no auth)

**GET `/health/live`** — process up; never checks dependencies (safe restart signal).
**GET `/health/ready`** — `200` or `503` with per-dependency detail:

```json
{ "status": "degraded", "checks": { "cassandra": "down", "redis": "ok", "influxdb": "ok" } }
```
