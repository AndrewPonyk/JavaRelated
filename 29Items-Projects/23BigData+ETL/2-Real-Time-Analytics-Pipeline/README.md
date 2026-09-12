# Real-Time Analytics Pipeline (RTAP)

Kafka + Flink streaming pipeline delivering **sub-second business metrics** with
**exactly-once semantics**, **windowed aggregations**, and **ML anomaly detection**
— served through Elasticsearch/PostgreSQL to a React dashboard and Grafana.

```
producers ─► Kafka (MSK) ─► Flink aggregation (1s/1m windows, exactly-once)
                               ├─► Elasticsearch (hot, <1s dashboards)
                               ├─► PostgreSQL   (durable history)
                               └─► Kafka ─► Flink anomaly detection ─► alerts
                                     ▲              ▲ broadcast models
                          ml/ trainer┘              │
                              React dashboard ◄── Analytics API (REST + SSE)
```

📐 **Docs:** [Project plan](docs/PROJECT-PLAN.md) · [Architecture](docs/ARCHITECTURE.md) · [Tech notes](docs/TECH-NOTES.md)

## Repository map

| Path | What it is |
|---|---|
| `streaming/` | Flink jobs (Java 17, Maven): windowed aggregation + anomaly detection |
| `services/analytics-api/` | Spring Boot 3 serving API (REST + SSE, OpenAPI at `/swagger-ui.html`) |
| `frontend/` | React 18 + TypeScript + Vite dashboard |
| `migrations/` | Flyway SQL migrations for PostgreSQL |
| `ml/` | Offline anomaly-model trainer (publishes to the broadcast control topic) |
| `infra/terraform/` | AWS infra: MSK, Managed Flink, OpenSearch, RDS, ECS, Grafana |
| `kafka/` · `elasticsearch/` · `grafana/` | Topic, index-template, and dashboard definitions |
| `scripts/` | Topic creation + synthetic event generator (with anomaly bursts) |
| `.github/workflows/` | CI, CD, and Terraform pipelines |

## Quickstart (local, turn-key)

Prereqs: Docker Desktop, JDK 21 (`JAVA_HOME` set), Maven, Node 22, Python 3.12.

```bash
cp .env.example .env
make all         # builds Flink jars + API, starts the full compose stack,
                 # creates topics + index templates, submits both Flink jobs
make seed        # start the event generator (injects anomaly bursts every 60s)
```

Then open:

| URL | What you see |
|---|---|
| http://localhost:5173 | **React dashboard** — live tiles (SSE), series, anomaly alerts + ack |
| http://localhost:8080/swagger-ui.html | Analytics API (OpenAPI) |
| http://localhost:8081 | Flink UI — both jobs, checkpoints |
| http://localhost:3000 | Grafana (provisioned ES + PG datasources & dashboard) |
| http://localhost:8085 | Kafka UI — topics incl. the transactional aggregate stream |

Optional: train + publish seasonal anomaly models from the collected history:

```bash
make train       # ml/ trainer → ml.model-updates.v1 → broadcast into the running job
```

## API

Live spec: `/v3/api-docs` · Swagger UI: `/swagger-ui.html`. The essentials:

```bash
# Metric registry
curl localhost:8080/api/v1/metrics
curl -X POST localhost:8080/api/v1/metrics -H 'Content-Type: application/json' \
     -d '{"metricKey":"carts.abandoned","displayName":"Carts abandoned","unit":"count"}'
# → 201 + Location; duplicate → 409; bad key → 400 with per-field errors

# Aggregate series — window ∈ 1s|10s|1m|5m|1h; defaults to the last 15 minutes.
# Recent ranges are served from Elasticsearch, history from PostgreSQL (automatic).
curl 'localhost:8080/api/v1/metrics/orders.completed/aggregates?window=10s'
curl 'localhost:8080/api/v1/metrics/orders.completed/aggregates?window=1m&from=2026-07-03T10:00:00Z&to=2026-07-03T11:00:00Z'

# Anomaly alerts — status ∈ open|acknowledged|resolved|all, limit ∈ 1..500
curl 'localhost:8080/api/v1/alerts?status=open&limit=50'
curl -X POST localhost:8080/api/v1/alerts/<alertId>/ack   # idempotent

# Live stream (SSE): connected/heartbeat/aggregate/alert events
curl -N localhost:8080/api/v1/stream/metrics

# Health (liveness/readiness probes for ECS/ALB)
curl localhost:8080/actuator/health
```

## Tests

```bash
make test          # unit suites: Flink (39), API (26), frontend (15), ml (6) — no Docker
make test-verify   # + Testcontainers integration tests (exactly-once E2E through real
                   #   Kafka+PG, ES idempotent-upsert, full API stack) + coverage gates (≥70%)
```

The integration suites self-skip when Docker is unavailable (the coverage gate then
needs `-Djacoco.skip=true`, or just run `make test`); CI always runs the full `verify`.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `job-aggregation` logs "jar missing" | Build first: `make build-streaming && make submit-jobs` |
| `analytics-api` container idles with "jar missing" | `make build-api && docker compose restart analytics-api` |
| Flink job FAILED: "Failed to create checkpoint storage" | Stale root-owned volumes from an old checkout: `docker compose down -v && make up` (the `flink-init` one-shot fixes ownership) |
| es-init HTTP 400 "same priority" | Our templates use priority 200 because stock ES ships a built-in `metrics-*-*` template at 100 — don't lower it |
| Dashboard empty | Pipeline needs data: `make seed`; check jobs are RUNNING at http://localhost:8081 |
| No anomaly alerts | Detectors need warm-up (~20 windows per metric series) — keep the seeder running >1 min; bursts fire every 60s |
| Kafka-fed consumers lag ~10s behind ES | By design: transactional output is visible on checkpoint completion; the sub-second path is ES (ARCHITECTURE §2.3) |
| `docker` commands hang or 500 | Docker Desktop engine not (fully) up — start/restart it and wait for `docker info` |
| Ports 5173/8080/8081/9200/3000/5432/29092 busy | Stop the conflicting service or edit the port mappings in docker-compose.yml |

## Deploying to AWS

Infra is Terraform (`infra/terraform/envs/dev`), deploys run through GitHub Actions
(`.github/workflows/`): build-once artifact promotion, OIDC auth, savepoint-based
Flink deploys. See [TECH-NOTES §3.3](docs/TECH-NOTES.md) — applying to a real AWS
account requires the state-bucket bootstrap + role/ARN variables described there.
