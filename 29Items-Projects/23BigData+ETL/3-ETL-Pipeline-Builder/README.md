# ETL Pipeline Builder

Real-time business metrics (sub-second) and governed batch ELT on one event backbone.

- **Orchestration:** Apache Airflow (MWAA) · **Transformations:** dbt on Snowflake
- **Lake processing:** AWS Glue (PySpark) · **Data quality:** Great Expectations gates
- **Speed layer:** AWS MSK → Python stream processor → Redis → FastAPI WebSocket → Angular
- **ML:** online anomaly detection on streaming metrics, alerting to Slack/PagerDuty
- **Infra/CD:** Terraform + GitHub Actions (OIDC)

📚 Docs: [Project plan](docs/PROJECT-PLAN.md) · [Architecture](docs/ARCHITECTURE.md) · [Tech notes](docs/TECH-NOTES.md)

## Quickstart (local)

Prerequisites: Docker Desktop, Python 3.12, Node 20.

```bash
# 1. Infra-in-a-box: kafka + redis, topics created
bash scripts/bootstrap_local.sh

# 2. API + stream processor + frontend
docker compose --profile apps up --build

# 3. Feed synthetic order events (add --burst to trigger an anomaly alert)
python scripts/seed_kafka_events.py --rate 20

# 4. Open the dashboard
#    UI:      http://localhost:8081       (nginx build)
#    API:     http://localhost:8000/docs  (OpenAPI)
#    Airflow: docker compose --profile airflow up  →  http://localhost:8080
```

Frontend dev loop instead of the container: `cd frontend && npm ci && npm start` → http://localhost:4200 (proxies `/api` to :8000).

## Repository map

| Path | What lives here |
|---|---|
| `airflow/` | DAGs (batch ELT, model retrain, streaming watchdog) + DAG tests |
| `dbt/` | Snowflake transformations, tests, seeds, lineage source of truth |
| `great_expectations/` | Data-quality suites & checkpoints (DAG gates) |
| `glue/` | PySpark jobs for the S3 lake |
| `streaming/` | MSK consumer: 500 ms windowing + ML anomaly detection |
| `api/` | FastAPI serving layer (REST + WebSocket) |
| `frontend/` | Angular 18 dashboard |
| `migrations/` | Snowflake DDL via schemachange (versioned, forward-only) |
| `infrastructure/` | Terraform (per-env roots + reusable modules) |
| `.github/workflows/` | CI / CD / infrastructure pipelines |

## Try the whole thing in two minutes

```bash
bash scripts/bootstrap_local.sh
docker compose --profile apps up --build -d
make smoke                                        # seeds events, asserts the full path
python scripts/seed_kafka_events.py --rate 40 --duration 70   # warm the anomaly detector
python scripts/seed_kafka_events.py --burst --duration 6      # trip it
# → open http://localhost:8081  (live tiles + sparklines, Alerts tab shows the anomaly)
```

## API at a glance

Interactive OpenAPI lives at `http://localhost:8000/docs`. The core surface:

```bash
curl localhost:8000/healthz                                        # liveness probe
curl localhost:8000/api/v1/metrics/current                         # latest 500ms windows
curl "localhost:8000/api/v1/metrics/orders_per_second/history?granularity=live&limit=50"
curl "localhost:8000/api/v1/metrics/orders_per_second/history?granularity=daily"   # marts / warmed cache
# ws://localhost:8000/api/v1/metrics/stream                        # live push (WS)

curl -X POST localhost:8000/api/v1/pipelines -H "Content-Type: application/json" \
  -d '{"name":"orders-daily","schedule":"0 2 * * *","source":"s3://lake/raw/orders/","target":"analytics.marts.fct_business_metrics_daily"}'
curl "localhost:8000/api/v1/pipelines?limit=100"                   # list (paginated)
curl -X PATCH localhost:8000/api/v1/pipelines/<id> -H "Content-Type: application/json" -d '{"status":"active"}'
curl -X DELETE localhost:8000/api/v1/pipelines/<id>

curl "localhost:8000/api/v1/alerts?limit=20"                       # anomaly feed
curl -X POST localhost:8000/api/v1/alerts/<alert_id>/ack           # acknowledge
```

With auth enabled: add `-H "X-API-Key: …"` (`AUTH_MODE=api_key`) or
`-H "Authorization: Bearer …"` (`AUTH_MODE=jwt`); WebSockets pass `?api_key=`/`?token=`.

## Common tasks

```bash
make lint          # ruff + format check + mypy
make test          # pytest — 80 unit tests (api, streaming, airflow logic; glue/dags need deps)
make test-cov      # same with coverage (87% on api/app + processor)
make smoke         # end-to-end smoke against the running compose stack
make up / make down
make seed          # synthetic events
make dbt-build     # dbt deps + build (needs Snowflake env vars)
make ge-validate   # run the raw orders GE checkpoint
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless   # 12 karma specs
```

## How this was verified

Functionality is tested at three levels — and it matters what was *actually exercised*
versus only mocked:

**1. Unit/component tests — 80 passing, 87% coverage (`make test-cov`).**
Real logic against hermetic fakes: the windowing aggregator and anomaly detector carry
exact numeric assertions (2 orders in a 500 ms window → `orders_per_second == 4.0`);
API routers/services run over a fake Redis implementing real command semantics; the
Kafka consumer loops run against injected fake brokers (commit-only-after-processing,
DLQ mid-batch, retry-after-broker-failure). A parity test pins the Airflow training
replica to bit-identical anomaly flags with the streaming detector.

**2. Live end-to-end on the compose stack — real Kafka, real Redis, real containers.**
- `scripts/smoke_e2e.sh` (also the CI `integration` job): seeds real events through
  Kafka → asserts all 4 computed metrics, rolling history, a pipeline CRUD round-trip,
  and the alert feed.
- The flagship ML flow, observed live: ~70 s of steady seeding warms the detector past
  its 120-window warmup; a `--burst` then trips it (z≈36 critical on `avg_order_value`),
  alerts travel Kafka → API consumer → Redis → REST feed, and acknowledgements persist.
- Model hot-reload: params published to the `anomaly:model` Redis hash are picked up by
  the processor within one poll cycle (`anomaly model reloaded: version=…` in its log).
- Poison paths: raw garbage and NaN-amount events are quarantined to
  `events.deadletter.v1` with error context and a replayable base64 payload.
- WebSocket push verified with a real client both direct (`:8000`) and through the
  nginx proxy (`:8081`); gzip and security headers verified with curl.

**3. Frontend.** Production build under strict templates, 12 karma specs in headless
Chrome, `ng lint` clean.

**Known verification gaps** (need accounts a dev laptop doesn't have): everything
Snowflake-side — dbt models, GE checkpoints, schemachange migrations, the warehouse
history/pipeline backends — is syntax-validated and mock-tested but has never run
against a real warehouse; Airflow DAGs are import/integrity-tested but no DAG has
executed a real run; Terraform is `validate`-level only. First deploys of those paths
should be watched accordingly.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `docker compose` commands hang with no output | Docker Desktop isn't running — start it and retry (commands don't error, they wait). |
| `exec: "C:/Program Files/Git/opt/kafka/..." not found` inside containers | Git Bash path mangling. The scripts set `MSYS_NO_PATHCONV=1`; export it yourself for ad-hoc `docker exec` commands with container paths. |
| Dashboard tiles grey out ("stale") | The processor stopped receiving events or died — reseed (`make seed`) or check `docker compose logs processor`. Greying is intentional: frozen numbers must not look live. |
| No anomaly alerts after `--burst` | The detector needs ~120 closed windows of warmup (~60 s of steady seeding at 40 ev/s) before it scores. Warm first, then burst. |
| `metrics/current` returns `503` | Redis is down. The API deliberately refuses to serve stale hot data — `docker compose up -d redis`. |
| Port 8000/8081/29092 already in use | Another stack instance: `make down`, or change the published ports in `docker-compose.yml`. |
| First `kafka` start takes ~30–60 s | KRaft storage formatting on a fresh volume; the healthcheck gates dependent services, just wait. |
| Python WebSocket client hangs on `ws://localhost` (Windows) | The `websockets` library auto-detects the system proxy; pass `proxy=None` to `websockets.connect`. Browsers are unaffected. |
| Airflow container can't reach dbt/GE paths | Use the compose profile (`--profile airflow`): it mounts `dbt/`, `great_expectations/`, `scripts/` into `/opt/project`. |

## Feature status

Implemented and verified locally end-to-end: sub-second live tiles with sparklines,
rolling + daily metric history, pipeline registry CRUD (persisted; creation form in the UI),
ML anomaly detection with alert feed + acknowledgements, model retrain loop with hot-reload,
dead-letter quarantine, graceful processor drains, pluggable API auth
(`none`/`api_key`/`jwt`), request-id structured logging. Remaining work is AWS/Snowflake
account provisioning — tracked with owners in [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md).
