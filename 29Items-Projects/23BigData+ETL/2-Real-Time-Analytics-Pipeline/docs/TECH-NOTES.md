# Real-Time Analytics Pipeline — Technical Notes

## 3.1 CI/CD Pipeline Design

```
PR opened ──► lint ──► unit tests ──► integration (Testcontainers) ──► package (no publish)
                                                                          │
merge to main ────────────────────────────────────────────────────────────┤
                                                                          ▼
                              build once: shaded Flink jars · API image · frontend bundle
                                                                          │
                                   deploy DEV (auto) ── smoke tests ──► deploy STAGING (auto)
                                                                          │  E2E + load smoke
                                                                          ▼
                                                     deploy PROD (manual approval, savepoint deploy)
```

Implementation (see `.github/workflows/`):

- **`ci.yml`** — path-filtered (`dorny/paths-filter`): only what changed builds. Jobs: `streaming` (JDK 17, `mvn verify`), `api` (JDK 21, `mvn verify`), `frontend` (Node 22: eslint → `tsc --noEmit` → vitest → `vite build`), `terraform` (fmt-check + validate). All four are branch-protection required checks.
- **`cd-deploy.yml`** — triggered on push to `main` and by `workflow_dispatch` (env picker). Builds artifacts **once**, uploads jars to the S3 artifacts bucket keyed by git SHA, pushes the API image to ECR tagged with the SHA, syncs the SPA to S3 + CloudFront invalidation. The same artifact set is promoted dev → staging → prod; environments carry approvals and per-env variables. Flink deploys are savepoint-based (§3.3).
- **`terraform.yml`** — `plan` on PRs touching `infra/**` (plan posted as PR comment), `apply` on main per environment behind GitHub environment protection.
- **Auth:** GitHub OIDC → per-env IAM role (`aws-actions/configure-aws-credentials`); no static keys. Roles are least-privilege per pipeline stage (the CI role can't touch prod).
- **Quality gates:** unit+integration green, coverage threshold on core logic, `terraform validate`, dependabot weekly, secret-scanning + dependency review on PRs.

## 3.2 Testing Strategy

| Layer | Tooling | Target |
|---|---|---|
| Unit — Flink functions | JUnit 5 + AssertJ; `AggregateFunction`/detector math as plain classes; the real window operators run on the in-JVM local environment via `executeAndCollect` (AggregationFlowTest) | ≥70% enforced by JaCoCo per module; core logic higher |
| Unit — API | JUnit 5, `@WebMvcTest` + Mockito for web slice; plain tests for services | ≥80% on services/controllers |
| Unit — Frontend | Vitest + React Testing Library (components: loading/error/empty/data states; hooks with mocked `EventSource`) | Critical components covered; no % chase |
| Integration — jobs | **Testcontainers** (Kafka, PostgreSQL, Elasticsearch) + `executeAsync` on the in-JVM cluster: produce events → run the real `buildPipeline` → assert committed Kafka output (read_committed), PG rows, DLQ envelopes, ES replay-idempotence | AggregationEndToEndIT · AnomalyEndToEndIT · ElasticsearchBulkSinkIT (self-skip without Docker) |
| Integration — API | `@SpringBootTest` + Testcontainers PG (Flyway applies `migrations/`) + embedded Kafka or Testcontainers | All endpoints + SSE handshake |
| E2E | Playwright against `docker compose` stack: seed events → dashboard shows series → injected spike → alert appears → ack | Smoke suite on every staging deploy |
| Load / latency | k6 (API) + `seed-events.py --rate N`; measure event→ES visibility and event→SSE p99 | Verify sub-second budget before prod sign-off |
| Semantics audit | Chaos script: kill taskmanager / restart broker mid-load; audit consumer asserts **no gaps, no duplicates** on `metrics.aggregates.v1` (`read_committed`) | Run in staging before every prod release of `streaming/**` |

Conventions: tests live beside their module; ITs suffixed `*IT` and run in the `verify` phase (Failsafe); test data builders in `flink-common` test-jar. **A bug fix ships with the test that would have caught it.**

## 3.3 Deployment Strategy

Everything is containerized/packaged and immutable; infra is Terraform-only (no console changes).

- **Flink jobs → Amazon Managed Service for Apache Flink.** Deploy = `stop with savepoint` → update application code (new jar S3 key) → restore from savepoint. Rollback = redeploy previous jar + its savepoint. Hard rules: every stateful operator has a stable `uid()`; state schema changes must be backward compatible or shipped as a new job reading from a replay point. Checkpoints: incremental RocksDB → S3.
- **Analytics API → ECS Fargate** behind ALB, rolling deployment (min healthy 100%, max 200%) with health checks on `/actuator/health`; blue/green via CodeDeploy is the upgrade path if SSE connection draining becomes an issue (SSE clients auto-reconnect, so rolling is acceptable).
- **Frontend → S3 + CloudFront.** Hashed asset filenames cached ~1y; `index.html` no-cache; deploy = sync + targeted invalidation.
- **PostgreSQL migrations → Flyway** as an explicit pipeline step before the API rollout (never at app startup in prod). Migrations are append-only and must be backward compatible one release back (expand → migrate → contract).
- **Kafka topics** are provisioned declaratively from `kafka/topics.yaml` (Terraform kafka provider / MSK CLI step in CD) — partition counts are capacity decisions, reviewed like schema changes.
- **Local dev** is `docker-compose.yml` (Kafka KRaft, Flink 1.20 session cluster, ES 8, PG 16, Grafana 11) + `make topics seed` + `flink run` of the shaded jar. Local exists to make Testcontainers-grade experiments interactive; it is not a mini-prod.

## 3.4 Environment Management

- **Local:** `.env` (from `.env.example`, git-ignored) consumed by docker-compose and dev servers.
- **AWS:** configuration lives in SSM Parameter Store (plain) and Secrets Manager (secret), namespaced `/rtap/{env}/…`; ECS tasks and Managed Flink runtime properties reference them at deploy time. **The artifact never changes between environments — only its environment does.**
- **Terraform:** one state per env (`infra/terraform/envs/{dev,staging,prod}` with its own `backend.tf` S3 key); sizing/flags via `{env}.tfvars`. Modules are shared; envs differ only in variables.
- **Frontend:** `VITE_*` variables baked at build; per-env config injected as `config.json` fetched at boot (avoids per-env rebuilds — the exception to nothing-changes rule is config, not code).

`.env.example` template (the real file lives at repo root):

```dotenv
# ─── Kafka ────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS=localhost:29092        # inside compose network: kafka:9092
# ─── PostgreSQL ───────────────────────────────────────────
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=analytics
POSTGRES_USER=analytics
POSTGRES_PASSWORD=analytics_local_pw           # local only — AWS uses Secrets Manager
# ─── Elasticsearch ────────────────────────────────────────
ELASTICSEARCH_URL=http://localhost:9200
# ─── Analytics API ────────────────────────────────────────
API_PORT=8080
FLYWAY_ENABLED=true
# ─── Frontend ─────────────────────────────────────────────
VITE_API_BASE_URL=http://localhost:8080
# ─── Grafana (local) ──────────────────────────────────────
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin_local_pw
# ─── AWS (deploy tooling only) ────────────────────────────
AWS_REGION=eu-central-1
ARTIFACTS_BUCKET=rtap-dev-artifacts            # TODO: set after terraform bootstrap
```

## 3.5 Version Control Workflow

**Trunk-based development.** Short-lived branches (`feat/…`, `fix/…`, `chore/…`, ≤ ~2 days) → PR → squash-merge to `main`. `main` is always releasable; releases are tags (`v0.3.0`), prod deploys pick a tagged SHA. Hotfix = branch from the prod tag, fix, tag, cherry-pick to `main`.

Why not Gitflow: this system deploys continuously to dev/staging and its riskiest artifact (a stateful stream job) is safest when changes are **small and frequent** — long-lived release branches would batch risk and make savepoint compatibility reviews harder. Environment promotion is handled by the CD pipeline and artifacts, not by branches. Feature flags (config-level: e.g. `enable-es-sink`) decouple merge from release.

Conventions: Conventional Commits (`feat:`, `fix:`, `perf:`…) for changelog generation; PR template forces a **savepoint-compatibility statement** for `streaming/**` changes; CODEOWNERS routes reviews.

## 3.6 Common Pitfalls (this stack, learned the hard way)

1. **Kafka transaction timeout mismatch.** Flink's Kafka sink defaults `transaction.timeout.ms` to 1h; brokers cap at `transaction.max.timeout.ms` (15m default) → `InvalidTxnTimeoutException` at first checkpoint. Fix: sink sets 10m, MSK config sets max 15m (already in `modules/msk`).
2. **Exactly-once silently lost at the last hop.** Any consumer of transactional topics without `isolation.level=read_committed` reads uncommitted/aborted data. It "works" in the happy path and betrays you on the first failover. Grep for every consumer config in reviews.
3. **Transactional sink latency surprises.** Output of an exactly-once Kafka sink is visible only on checkpoint completion. If a product owner asks why Kafka-fed numbers lag ~checkpoint-interval: that's physics, not a bug. Sub-second surfaces read the idempotent ES path (see ARCHITECTURE §2.3).
4. **Idle partitions stall watermarks.** One quiet partition freezes event time for the whole job — windows never close, dashboards flatline while data flows. `withIdleness()` is set, but also alert on watermark lag; and keep partition count sane vs. parallelism.
5. **Elasticsearch mapping explosion** from dynamic `dimensions.*` keys (cardinality bomb → cluster instability). Templates map `dimensions` as `flattened` (OpenSearch: `flat_object`). Never let producer-controlled keys become index fields.
6. **Managed Flink overrides code-level checkpoint config.** On KDA/Managed Flink, checkpointing interval/mode come from the application configuration (Terraform `flink-app` module), silently overriding `env.enableCheckpointing(...)`. Configure it in Terraform; treat code values as local-dev defaults.
7. **JSON schema drift.** Without a registry, a producer adding a field is fine — renaming one breaks deserialization into the DLQ. Mitigations: `schemaVersion` field, `FAIL_ON_UNKNOWN_PROPERTIES=false`, DLQ monitoring, planned Avro+Glue migration (ADR #4).
8. **RocksDB local disk & managed memory.** Big keyed state on small task nodes → mysterious restarts. Watch `state.backend.rocksdb` metrics, use incremental checkpoints, set state TTL on per-key detector state (done in `EwmaZScoreDetector` — unbounded key cardinality is a memory leak with extra steps).
9. **Kafka consumer-group intuition doesn't apply to Flink.** Flink assigns partitions statically and commits offsets only on checkpoint — `group.id` is for lag monitoring, not rebalancing. Lag dashboards therefore show sawtooth lag at checkpoint cadence: normal.
10. **PG exactly-once via XA is a trap here.** `max_prepared_transactions=0` by default (and RDS discourages it). Idempotent upserts on the natural key deliver the same effective guarantee with none of the 2PC fragility.
11. **MSK IAM auth friction.** Clients need `aws-msk-iam-auth` on the classpath, port **9098** (not 9092), and SG rules per client; a wrong port yields opaque timeouts. The `KafkaConfig.mskIamProperties()` helper centralizes this.
12. **Hot keys skew windows.** One dominant metric key pins a subtask at 100%. Incremental aggregation keeps per-event cost O(1); if skew appears, salt the 1s stage key and merge in the 1m rollup.
13. **SSE behind proxies/ALB.** Disable response buffering, keep heartbeat comments (`:\n\n`) under the ALB idle timeout, and remember EventSource can't set headers — pass short-lived tokens via query/cookie.
14. **Grafana ES datasource quirks.** Needs exact time field (`windowStart` as `epoch_millis`) and index pattern; version compatibility with OpenSearch differs from stock ES — pin the datasource type in provisioning and test on upgrade.
15. **Compose networking.** Advertised listeners: host clients use `localhost:29092`, in-network clients `kafka:9092`. The Flink containers must use the internal listener — mixing them costs an afternoon.
16. **Jackson core/databind drift.** `flink-connector-kafka` drags in an older `jackson-core`; paired with a newer databind it dies at runtime with `NoSuchMethodError: BufferRecycler.releaseToPool()`. The parent POM imports the Jackson BOM so core/annotations/databind always align. Related trap: Jackson mangles `getZThreshold` → property `zthreshold` and silently drops the trainer's `zThreshold` — hence the explicit `@JsonProperty` (leading-capitals rule).
17. **Flink serializers want mutable collections.** `Map.of(...)` inside a POJO dies in Kryo's `MapSerializer` (`UnsupportedOperationException` on `put`). Model setters take defensive `HashMap` copies. Also: running the in-JVM cluster on JDK 17+ needs the same `--add-opens` flags flink-dist ships — wired into Surefire/Failsafe.
18. **Two compose landmines.** (a) Stock ES 8 ships a built-in `metrics` template on `metrics-*-*` at priority 100 — an overlapping custom pattern at the same priority is rejected with HTTP 400; ours run at 200. (b) Docker named volumes are created root-owned while the flink image runs as user `flink` — checkpoint storage fails with "Failed to create directory"; the `flink-init` one-shot chowns the volumes before the cluster starts.
19. **Transactional-id prefixes are per SINK OPERATOR, not per job.** Two `sinkTo` calls sharing one prefix make the operators fence each other's producers (`ProducerFencedException` crash-loop with zero committed output). The aggregation job suffixes the prefix per resolution (`rtap-agg-1s` / `rtap-agg-1m`); caught by AggregationEndToEndIT.
