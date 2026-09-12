# Log Analytics Platform — On-Call Runbooks

Ordered by how often they page. Every runbook follows the same shape:
**symptom → confirm → mitigate → root-cause → prevent.**

Conventions: `$BOOTSTRAP` = Kafka bootstrap servers, `$OS` = OpenSearch endpoint.
Locally: `localhost:29092` / `http://localhost:9200`. In AWS, both come from
Terraform outputs (`infra/terraform/outputs.tf`).

---

## 1. Consumer-group lag spike (pipeline falling behind)

**Symptom.** Dashboards show logs minutes old; lag alarm on a `spark-kafka-source-*`
group or on `alerting-engine`.

**Confirm.**

```bash
# lag per group (local; MSK: same tool against the TLS listener)
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups | grep -v " 0 *$"
# is the job alive and committing batches?
docker compose logs --tail 50 spark-enrich | grep "Streaming query made progress"
```

**Mitigate.**

- *Job dead / crash-looping:* restart it (`docker compose --profile pipeline up -d spark-enrich`;
  EMR Serverless: restart the job run from `deploy.yml` or the console). It resumes from its
  checkpoint; idempotent `_id`s absorb the reprocessing.
- *Job alive but slow:* check OpenSearch first — indexing backpressure (429s in the sink log)
  slows `foreachBatch`. See runbook 2/3.
- *Backlog after downtime:* expected to drain gradually — `maxOffsetsPerTrigger` bounds each
  micro-batch (TECH-NOTES pitfall #10). Don't raise it in a panic; scale executors instead
  (EMR Serverless max capacity, or more cores in `spark-defaults.conf` locally).
- *Sustained input growth:* more Kafka partitions is the real fix (a deliberate operation —
  partition counts live in `common/kafka.py`), then more executors up to the partition count.

**Prevent.** Lag alarm per consumer group; batch-duration vs trigger-interval metric; load
test before onboarding a chatty team.

---

## 2. OpenSearch indexing failures (sink retries / job crash)

**Symptom.** `opensearch_sink` logs `bulk indexing attempt N failed`, job eventually crashes
loudly and restarts; or gateway `/readyz` fine but nothing new in `la-logs`.

**Confirm.**

```bash
curl -s $OS/_cluster/health | jq '{status, active_shards_percent_as_number}'
curl -s $OS/_cat/thread_pool/write?v          # rejected column = backpressure
curl -s "$OS/_cat/indices/la-*?v&s=store.size:desc" | head
```

**Mitigate.**

- *Cluster `red`:* find unassigned shards — `curl -s $OS/_cluster/allocation/explain | jq .` —
  usually disk watermark or a lost node. Free disk (delete expired indices manually if ISM is
  stuck, see runbook 3) or grow the domain.
- *429 rejections (write queue full):* the sink already backs off; reduce pressure — lower
  `LA_MAX_OFFSETS_PER_TRIGGER`, or raise `refresh_interval` on `la-logs-*` to `30s` during
  the incident (`PUT la-logs-*/_settings {"index":{"refresh_interval":"30s"}}`).
- *Mapping errors in the sink log:* someone changed the event contract. The batch that fails
  validation is in the log with the response body; fix the producer or add a migration —
  never `dynamic: true` (TECH-NOTES pitfall #3).

**Root-cause.** The job crash is by design ("fail loudly, resume from checkpoint, idempotent
writes") — the data is safe; the incident is about *why* OpenSearch pushed back.

---

## 3. ISM / rollover failure (one index growing forever)

**Symptom.** `la-logs-000001` is weeks old and huge; disk-usage alarm; ISM explain shows
`Failed` or the policy never attached.

**Confirm.**

```bash
curl -s "$OS/_plugins/_ism/explain/la-logs-000001" | jq .
curl -s "$OS/_alias/la-logs" | jq .        # which index holds the write alias?
```

**Mitigate.**

- *Policy failed:* retry it — `POST $OS/_plugins/_ism/retry/la-logs-000001`.
- *`rollover_alias` mismatch* (TECH-NOTES pitfall #7): fix the index setting
  (`PUT la-logs-000001/_settings {"index":{"plugins.index_state_management.rollover_alias":"la-logs"}}`),
  then retry as above.
- *Emergency disk pressure:* roll over manually — `POST $OS/la-logs/_rollover` — and, if
  needed, delete the oldest non-write indices (`DELETE la-logs-000001`) **after confirming
  they're outside the retention promise**.

**Prevent.** Post-deploy smoke check runs `_ism/explain` on the newest index (deploy.yml);
alarm on `store.size` per index.

---

## 4. Poison messages / DLQ growth

**Symptom.** DLQ-rate metric above ~0.1% of ingest, or a `logs.dlq` depth alarm.

**Confirm & triage.**

```bash
python scripts/replay_dlq.py inspect            # count per error reason + payload previews
```

Typical reasons: producer schema drift (new agent version), truncated JSON (network),
or a pipeline bug rejecting valid events.

**Mitigate.**

- *Pipeline bug (valid events rejected):* fix + deploy the parser, then replay as-is:

  ```bash
  python scripts/replay_dlq.py replay            # dry-run first — always
  python scripts/replay_dlq.py replay --execute
  ```

- *Broken payloads (producer bug):* fix the producer, then repair the stranded messages:

  ```bash
  python scripts/replay_dlq.py dump --dir dlq-out     # one JSON file per message
  # edit the `payload` field in the files (scripted or by hand)
  python scripts/replay_dlq.py replay --from-dir dlq-out --execute
  ```

- Replays are idempotent end-to-end: deterministic `_id`s mean a twice-replayed event
  indexes once. Replayed messages carry an `la-replayed-from` header with the original
  DLQ coordinates, so a second failure is traceable.
- An **unmodified** poison message will simply return to the DLQ — that's the loop
  guard, not a bug. Use `--limit 1 --execute` to test a fix on one message first.

**Prevent.** Per-service DLQ-rate metric (a single team's deploy usually explains a spike);
contract tests on `common/models.py`.

---

## 5. Anomaly model rollback / misbehaving ML

**Symptom.** 3 a.m. page storm from `ml-anomaly-*` rules (model too sensitive), or
suspicious silence for days (model stale/broken).

**Confirm.**

```bash
# what is deployed? (local registry: ./models; AWS: s3://…/models)
cat models/latest.json
# scores lately — all ~1.0 (noisy) or all ~0.0 (blind)?
curl -s "$OS/la-anomalies/_search" -H 'Content-Type: application/json' \
  -d '{"size":0,"aggs":{"s":{"stats":{"field":"score"}}}}' | jq .aggregations.s
```

**Mitigate (fastest first).**

1. *Silence the pages, keep the data:* raise `LA_ANOMALY_ALERT_THRESHOLD` on the alerting
   engine (env change + restart, seconds) — scoring continues, alerting calms down. Or
   disable the `ml-anomaly-*` rules via the alert-rules API.
2. *Roll back the model:* the registry keeps every version; `latest.json` is just a pointer.
   Point it at the previous version (edit the JSON locally / copy the S3 object), and the
   scoring job hot-reloads within `LA_MODEL_REFRESH_SECONDS` (default 300) — **no restart**.
3. *Retrain forward:* `python -m log_analytics.ml.train --source opensearch --days 7`
   publishes a new version and moves the pointer; same hot-reload path. Use
   `--source synthetic` only to bootstrap empty environments.

**Root-cause.** Check `models/<version>/metadata.json` (training window, holdout scores):
trained on an incident window ⇒ the "normal" baseline is poisoned — retrain on a clean
window. Traffic mix changed (new service tier) ⇒ retrain; consider per-tier models
(TECH-NOTES pitfall #9).

**Prevent.** Scheduled weekly retraining (`.github/workflows/retrain.yml`) plus the score
alarm: alert if the daily mean score drifts past 2σ of its 30-day baseline.

---

## 6. Service went silent (the platform's own canary)

**Symptom.** `[Service silent] <name>` alert — a service that logged in the last 24 h has
sent nothing for `LA_SILENT_SERVICE_AFTER_MINUTES`.

**Triage order.** (1) Is the *service* down? Check its own health/deploy status. (2) Is only
its *logging* broken — agent crashed, disk full, wrong gateway URL/API key after a config
change? (3) Is it a decommission? Then the alert is working as intended; it stops firing
24 h after the last event.

**False-positive tuning.** Batchy/cron services trip this: raise
`LA_SILENT_SERVICE_AFTER_MINUTES`, or (Phase-3 multi-tenant work) move the threshold
per-service.

---

## Meta-monitoring quick reference

| Signal | Where | Healthy |
|---|---|---|
| Consumer-group lag | `kafka-consumer-groups.sh --describe` | ≈ 0, or draining |
| Batch duration vs trigger | job logs `Streaming query made progress` | duration < trigger |
| DLQ rate | `replay_dlq.py inspect` count vs ingest volume | < 0.1% |
| Indexing errors | `opensearch_sink` WARN/ERROR log lines | none |
| ISM state | `GET _plugins/_ism/explain/la-logs-*` | not `Failed` |
| Alert delivery | `la-alerts` doc count vs notifier errors in engine log | every alert indexed |
