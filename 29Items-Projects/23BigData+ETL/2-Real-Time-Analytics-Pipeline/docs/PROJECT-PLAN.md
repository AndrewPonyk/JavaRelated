# Real-Time Analytics Pipeline — Project Plan

| | |
|---|---|
| **Project** | Real-Time Analytics Pipeline (RTAP) |
| **Stack** | Kafka (AWS MSK), Apache Flink, Elasticsearch/OpenSearch, PostgreSQL, Grafana, React |
| **Deployment** | AWS (MSK, Managed Flink, OpenSearch, RDS, ECS, S3/CloudFront), Terraform, GitHub Actions |
| **Core requirements** | Sub-second business metrics, exactly-once processing semantics, windowed streaming aggregations, ML anomaly detection with alerts |

Companion documents: [ARCHITECTURE.md](./ARCHITECTURE.md) (design & diagrams), [TECH-NOTES.md](./TECH-NOTES.md) (CI/CD, testing, deployment, pitfalls).

---

## 1. Project File Structure

The repository is a **monorepo** — the pipeline is one product whose parts (stream jobs, serving API, UI, infra) evolve together and share contracts (topic names, event schemas, index mappings). Path-filtered CI keeps builds fast despite the single repo.

```
2-Real-Time-Analytics-Pipeline/
├── README.md                        # Quickstart + repo map
├── .env.example                     # Local configuration template (never commit .env)
├── .gitignore
├── .editorconfig
├── Makefile                         # One-liners for the local dev loop
├── docker-compose.yml               # Local stack: Kafka, Flink, Elasticsearch, Postgres, Grafana
│
├── docs/                            # Architecture & engineering docs (this folder)
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── .github/                         # CI/CD (GitHub Actions)
│   ├── workflows/
│   │   ├── ci.yml                   # Lint + test + build per changed path (PRs, main)
│   │   ├── cd-deploy.yml            # Artifact promotion: dev → staging → prod
│   │   └── terraform.yml            # terraform fmt/validate/plan on PR, apply on main
│   ├── dependabot.yml
│   ├── CODEOWNERS
│   └── pull_request_template.md
│
├── streaming/                       # Apache Flink jobs — Java 17, Maven multi-module
│   ├── pom.xml                      # Parent POM: versions, shade/surefire plugin mgmt
│   ├── flink-common/                # Shared models, serdes, Kafka config helpers
│   │   └── src/main/java/com/rtap/streaming/common/
│   │       ├── model/               # BusinessEvent, MetricAggregate, AnomalyAlert
│   │       ├── serde/               # Jackson (de)serialization schemas, poison-pill safe
│   │       └── config/              # Topic names, Kafka properties, MSK IAM auth helper
│   ├── flink-aggregation-job/       # Windowed aggregations (1s hot path + 1m rollup),
│   │   │                            #   exactly-once Kafka sink, ES/PG sink factories
│   │   └── src/{main,test}/java/com/rtap/streaming/aggregation/
│   └── flink-anomaly-job/           # Streaming anomaly detection (EWMA z-score, keyed
│       │                            #   state), consumes aggregates read_committed
│       └── src/main/java/com/rtap/streaming/anomaly/
│
├── services/
│   └── analytics-api/               # Spring Boot 3 serving API (REST + SSE)
│       ├── pom.xml
│       ├── Dockerfile
│       └── src/
│           ├── main/java/com/rtap/api/
│           │   ├── metrics/         # Metric definitions CRUD + aggregate queries
│           │   ├── alerts/          # Anomaly alert list / acknowledge
│           │   ├── stream/          # SSE endpoint pushing live aggregates to the UI
│           │   └── common/          # Problem-details error handling
│           ├── main/resources/application.yml
│           └── test/java/com/rtap/api/
│
├── frontend/                        # React 18 + TypeScript + Vite dashboard
│   ├── package.json
│   ├── vite.config.ts               # Dev proxy /api → analytics-api
│   ├── tsconfig.json
│   ├── eslint.config.js / .prettierrc
│   ├── index.html
│   └── src/
│       ├── main.tsx / App.tsx / styles.css
│       ├── api/client.ts            # Typed fetch wrapper + error mapping
│       ├── hooks/useMetricsStream.ts# SSE hook for live metric pushes
│       ├── types/metrics.ts         # Shared TS contracts (mirror API DTOs)
│       └── components/
│           ├── MetricsDashboard.tsx # Fetch + loading/error/empty states + sparkline
│           └── AnomalyAlertsPanel.tsx
│
├── migrations/                      # Flyway SQL migrations for PostgreSQL (Vnnn__*.sql)
│   ├── V001__core_schema.sql        # metric_definitions, metric_aggregates, anomaly_alerts
│   └── V002__seed_metric_definitions.sql
│
├── infra/
│   └── terraform/
│       ├── modules/                 # Reusable building blocks
│       │   ├── networking/          # VPC, subnets, endpoints
│       │   ├── msk/                 # MSK cluster + broker config (transaction settings!)
│       │   ├── flink-app/           # Amazon Managed Service for Apache Flink app
│       │   ├── rds-postgres/        # RDS PostgreSQL 16
│       │   ├── opensearch/          # OpenSearch domain (Elasticsearch-compatible)
│       │   ├── ecs-api/             # ECS Fargate service for analytics-api
│       │   └── grafana/             # Amazon Managed Grafana workspace
│       └── envs/
│           ├── dev/                 # main.tf + variables.tf + backend.tf + dev.tfvars
│           ├── staging/
│           └── prod/
│
├── kafka/
│   └── topics.yaml                  # Source of truth for topics, partitions, configs
│
├── elasticsearch/
│   └── index-templates/             # Composable index templates (flattened dimensions!)
│       ├── metrics-aggregates.json
│       └── anomaly-alerts.json
│
├── grafana/
│   ├── provisioning/                # Auto-provisioned datasources & dashboard providers
│   │   ├── datasources/datasources.yml
│   │   └── dashboards/dashboards.yml
│   └── dashboards/business-metrics.json
│
├── scripts/
│   ├── create-topics.sh             # Creates topics on the local broker from kafka/topics.yaml
│   └── seed-events.py               # Synthetic event generator (with injectable anomalies)
│
└── ml/                              # Offline anomaly-model training pipeline (Python)
    ├── README.md                    # Model lifecycle: train → publish → broadcast → score
    ├── requirements.txt
    └── training/train_anomaly_model.py
```

### Module ownership & boundaries

| Path | Owns | Must not |
|---|---|---|
| `streaming/` | Event contracts, all stateful processing, exactly-once guarantees | Serve queries; call the API |
| `services/analytics-api` | Read path (ES + PG), SSE fan-out, alert workflow | Write aggregates (Flink owns writes) |
| `frontend/` | Presentation only | Talk to Kafka/ES/PG directly |
| `migrations/` | Single source of truth for the PG schema (Flyway) | Be edited after merge (append-only) |
| `infra/terraform` | All AWS resources | Contain secrets (use SSM/Secrets Manager) |
| `kafka/topics.yaml` | Topic names/partitions/configs | Drift from `KafkaConfig.java` constants |

---

## 2. Implementation TODO List

### Phase 1 — Foundation (high priority)
*Goal: a walking skeleton — one event travels producer → Kafka → Flink → sink → API → UI, locally and in dev.*

- [x] Local dev stack boots green: `docker compose up` (Kafka KRaft, Flink 1.20, ES 8, PG 16, Grafana 11 **+ API, frontend, init one-shots, auto job submission**)
- [x] Topics created from `kafka/topics.yaml` (kafka-init / `make topics`); `auto.create.topics.enable=false` everywhere
- [x] Event contract v1 frozen: `BusinessEvent` JSON schema + `schemaVersion` field (Avro migration deferred, see TECH-NOTES §3.6.7)
- [x] `flink-aggregation-job`: 1s tumbling event-time windows, watermarks + idleness, exactly-once checkpointing, transactional Kafka sink to `metrics.aggregates.v1` *(verified by AggregationEndToEndIT with a read_committed consumer)*
- [x] Poison-pill handling in deserializer — dead-letter envelope, counted, pipeline survives malformed events
- [x] Flyway `V001`/`V002` applied; PG reachable from API
- [x] `analytics-api`: `GET /metrics`, `POST /metrics`, `GET /metrics/{key}/aggregates`, problem-details errors, OpenAPI at /swagger-ui.html
- [x] React dashboard renders a metric series with loading/error/empty states
- [x] CI: path-filtered lint+test+build for streaming / api / frontend / ml / terraform on every PR
- [ ] Terraform `envs/dev`: VPC + MSK + S3 — **code complete & validated; `apply` pending an AWS account + state-bucket bootstrap**
- [x] Seed generator `scripts/seed-events.py` produces realistic load with injectable anomaly spikes

### Phase 2 — Core features (medium priority)
*Goal: production semantics — exactly-once end-to-end, anomaly alerts, real serving path, deployed to dev+staging.*

- [x] Elasticsearch sink with **deterministic document IDs** (idempotent upsert ⇒ effectively-once hot path); index template with `flattened` dimensions *(replay-idempotence verified by ElasticsearchBulkSinkIT)*
- [x] PostgreSQL sink via idempotent `INSERT … ON CONFLICT DO UPDATE` (durable history)
- [x] 1m rollup cascade from 1s aggregates (windowed re-aggregation, not raw re-read)
- [x] `flink-anomaly-job`: EWMA z-score detector on keyed state (warm-up, state TTL, cooldown), consumes `read_committed`, emits `alerts.anomalies.v1` *(AnomalyEndToEndIT)*
- [x] Alert ingestion: API consumes alerts → PG history (idempotent on alertId) + SSE push; acknowledge workflow (optimistic DB transition)
- [x] SSE live stream wired end-to-end (Kafka → API → `useMetricsStream` → dashboard tiles + alert refresh)
- [x] Late-data side outputs → `events.raw.late.v1`; DLQ topic + envelope for undeserializable input
- [x] API queries real stores: ES for hot ranges (date_histogram), PG for history (`date_bin` re-bucketing), automatic fallback
- [ ] AuthN/Z: Cognito JWT on API + frontend login — **AWS-gated** (MSK IAM client support is implemented: `--kafka.security msk-iam`)
- [ ] Terraform: Managed Flink app, OpenSearch, RDS, ECS API, Managed Grafana; staging env — **code complete, apply AWS-gated**
- [ ] CD pipeline: build-once promotion implemented in cd-deploy.yml; **ARN/role wiring pending an AWS account**
- [ ] Savepoint-based Flink deploys — **AWS-gated**; operator `uid()`s are locked on every stateful operator ✓
- [x] Grafana provisioned dashboards for business metrics *(pipeline-health board: remaining)*
- [x] Integration tests with Testcontainers (Kafka + PG + ES) for both Flink jobs and the API

### Phase 3 — Polish & optimization (lower priority)
*Goal: scale, cost, and operability.*

- [x] ML model lifecycle: offline trainer (`ml/`) publishes seasonal baselines to `ml.model-updates.v1`; anomaly job consumes via broadcast state; model version stamped on alerts *(pulled forward from Phase 3 — robust median/MAD fitting, graceful degradation without a model)*
- [ ] Flink autoscaling (Managed Flink autoscaling or Kubernetes operator + autoscaler if migrated to EKS)
- [ ] ES ILM/ISM: rollover + delete for `metrics-aggregates-*`; PG native partitioning + retention job for `metric_aggregates`
- [ ] Load & latency verification: k6 + producer at target rate; measure event→dashboard p99 against the sub-second budget
- [ ] Chaos drills: broker restart, taskmanager kill, AZ failure — verify exactly-once (no dupes/loss) with an audit consumer
- [ ] Schema Registry (AWS Glue) + Avro migration for `events.raw.v1`
- [ ] Cost pass: MSK storage tiering, OpenSearch UltraWarm, checkpoint retention, NAT egress
- [ ] Runbooks: consumer lag, stuck watermark, checkpoint failures, alert storming; on-call dashboard
- [ ] Frontend polish: metric explorer, alert filtering/paging, dark mode, a11y audit
- [ ] Security hardening: penetration checklist, dependency scanning gates, SBOM publication

---

## 3. Milestones

| Milestone | Definition of done |
|---|---|
| **M1 — Walking skeleton** (end Phase 1) | Seeded event visible on local dashboard < 5s after produce; CI green on PRs |
| **M2 — Trusted numbers** (mid Phase 2) | Exactly-once verified under induced failure; ES/PG serving real queries in dev |
| **M3 — Alerting live** (end Phase 2) | Anomaly spike → alert on dashboard < 2s, acknowledged workflow works in staging |
| **M4 — Production-ready** (Phase 3) | Load test at target throughput meets p99 < 1s; runbooks + on-call dashboards exist |

## 4. Working agreements

- Topic names, schema versions, and operator `uid()`s are **contracts** — changing them requires an ADR note in `docs/ARCHITECTURE.md` §7.
- Every PR that touches `streaming/` must state its **savepoint compatibility** (see PR template).
- `main` is always deployable; artifacts are promoted, never rebuilt per environment.
