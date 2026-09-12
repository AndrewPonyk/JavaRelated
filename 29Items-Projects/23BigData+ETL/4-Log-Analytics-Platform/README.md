# Log Analytics Platform

Centralized log analytics: **Kafka** ingestion → **Spark Structured Streaming** processing →
**Elasticsearch / AWS OpenSearch** storage & search → **Kibana / OpenSearch Dashboards**
visualization, with rule-based pattern detection and **Isolation Forest** anomaly detection.

- 📐 [Architecture](docs/ARCHITECTURE.md) — pattern, diagrams, data flow, security
- 🗺️ [Project plan](docs/PROJECT-PLAN.md) — file structure + phased TODO list
- 🔧 [Tech notes](docs/TECH-NOTES.md) — CI/CD, testing, deployment, env management, pitfalls
- 🚨 [Runbooks](docs/RUNBOOKS.md) — on-call: lag spikes, indexing/ISM failures, DLQ replay, model rollback

## Quickstart (local)

```bash
# 0) Python env
python -m venv .venv && . .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -e ".[dev]" -r requirements-spark.txt
cp .env.example .env

# 1) Infrastructure (Kafka, OpenSearch, Dashboards, Redis)
docker compose up -d kafka opensearch dashboards redis

# 2) Search schema + Kafka topics
python scripts/es_migrate.py
python scripts/create_kafka_topics.py

# 3) Bootstrap an anomaly model (synthetic baseline; retrain from real history later)
python -m log_analytics.ml.train --source synthetic

# 4) Services (separate terminals) — or containerized: docker compose --profile app up -d
uvicorn log_analytics.ingestion.gateway:app --port 8080 --reload   # ingestion edge
uvicorn log_analytics.api.main:app --port 8000 --reload            # query/admin API + ops console
python -m log_analytics.alerting.engine                            # alert dispatcher

# 5) Spark streaming jobs — containerized (the supported way on Windows, TECH-NOTES #11)
docker compose --profile pipeline up -d --build
#    (on Linux/WSL you can also run one directly: ./scripts/submit_spark_job.sh enrich_and_index)

# 6) Traffic + proof
python scripts/seed_sample_logs.py --count 2000 --burst-errors
pytest -m e2e          # ingest→search (idempotent), anomaly scores, recorded alerts
```

Then open:

- OpenSearch Dashboards → <http://localhost:5601> — import the three boards:

  ```bash
  cd kibana/saved_objects
  for f in index-patterns la-log-overview la-anomaly-triage la-alert-history; do
    curl -s -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
      -H "osd-xsrf: true" --form file=@$f.ndjson > /dev/null && echo "imported $f"
  done
  ```

- Ops console → <http://localhost:8000/console/>
- API docs → <http://localhost:8000/docs>, gateway docs → <http://localhost:8080/docs>

Poison messages? `python scripts/replay_dlq.py inspect` — full workflow in
[RUNBOOKS.md §4](docs/RUNBOOKS.md).

## API quick reference

Interactive OpenAPI docs: gateway <http://localhost:8080/docs>, query API <http://localhost:8000/docs>.

| Endpoint | What | Errors |
|---|---|---|
| `POST :8080/v1/logs` | ingest a JSON array of events (optionally gzip); per-item validation | 400 malformed body · 401 bad `X-API-Key` · 413 too large · 429 rate-limited · 503 Kafka down |
| `GET :8080/healthz` / `readyz` | gateway liveness / Kafka reachability | 503 not ready |
| `GET :8000/api/v1/logs/search` | constrained log search: `q`, `service`, `level`, `from_ts`, `to_ts`, `size` (≤500), `offset` (≤10k) | 422 bad params · 502 backend error |
| `POST/GET/PUT/DELETE :8000/api/v1/alert-rules[/{id}]` | alert-rule CRUD (paginated list: `limit`, `offset`, `enabled`) | 404 unknown id · 409 duplicate name · 422 validation |
| `GET :8000/healthz` / `readyz` | API liveness / OpenSearch cluster health | 503 degraded |

```bash
# ingest two events
curl -i -X POST localhost:8080/v1/logs -H 'Content-Type: application/json' -d '[
  {"timestamp":"2026-07-08T10:00:00Z","service":"checkout","level":"error","message":"upstream timeout after 3000ms"},
  {"timestamp":"2026-07-08T10:00:01Z","service":"checkout","level":"info","message":"retry succeeded"}]'

# search them (once the enrich job has run)
curl "localhost:8000/api/v1/logs/search?service=checkout&level=ERROR&size=10"

# create an alert rule
curl -X POST localhost:8000/api/v1/alert-rules -H 'Content-Type: application/json' -d \
  '{"name":"checkout errors","metric":"error_ratio","op":"gt","threshold":0.1,"window":"5m","severity":"warning","channels":["log","slack"]}'
```

## Tests

```bash
pytest                    # unit tests (fast, no services needed)
pytest -m integration     # needs docker compose stack up (Spark tests also need a JVM)
pytest -m e2e             # needs the full pipeline running
make lint typecheck       # ruff + mypy
```

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `docker compose` hangs or errors immediately | Docker Desktop isn't running — start it first |
| Ports already allocated | something else owns 8000/8080/9200/5601/6379/29092 — stop it or remap in `docker-compose.yml` |
| Gateway returns 202 but `la-logs` stays empty | the pipeline profile isn't up — `docker compose --profile pipeline up -d`; check `docker compose logs spark-enrich` |
| `spark-anomaly` exits with "model registry is empty" | bootstrap a model first: `python -m log_analytics.ml.train --source synthetic` |
| Gateway answers 401 | `LA_GATEWAY_API_KEYS` is set — send the `X-API-Key` header |
| Query API answers 503 "storage backend" | OpenSearch is down or red — `curl localhost:9200/_cluster/health`; single-node needs ~1 GB free RAM (`OPENSEARCH_JAVA_OPTS`) |
| Integration/e2e tests all skip | that's by design — each skip message names the service to start |
| Spark tests skip on Windows | native PySpark workers are unreliable here — run them in the spark image (command in `tests/integration/test_spark_transforms.py`), see TECH-NOTES #11 |
| Local requests hang behind a corporate proxy | `export NO_PROXY=localhost,127.0.0.1` (TECH-NOTES #14) |
| Git Bash mangles `/…` paths in docker commands | `export MSYS_NO_PATHCONV=1` |
| Poison messages piling up in `logs.dlq` | `python scripts/replay_dlq.py inspect`, then the workflow in [RUNBOOKS.md §4](docs/RUNBOOKS.md) |

## Repository map

| Path | What |
|---|---|
| `src/log_analytics/` | Python package: common contracts, ingestion gateway, Spark jobs, ML, alerting, API |
| `elasticsearch/migrations/` | versioned search-schema migrations (`scripts/es_migrate.py` applies them) |
| `config/` | alert rules (YAML) + Spark defaults |
| `kibana/` | dashboards-as-code (saved object exports) |
| `frontend/ops-console/` | zero-build internal console served by the API |
| `infra/terraform/` | AWS: MSK, OpenSearch, EMR Serverless, ECS, IAM/OIDC |
| `.github/workflows/` | CI (`ci.yml`) and env-gated deploys (`deploy.yml`) |
