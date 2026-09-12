# ML — Anomaly Model Lifecycle

The pipeline detects anomalies **in-stream** (flink-anomaly-job). This folder owns the
**offline half** of that story: training seasonal baselines that correct the online
detector for daily/weekly cycles, so Monday-morning traffic doesn't page anyone.

```
PostgreSQL metric_aggregates (history)
        │  train (daily, scheduled CI job)
        ▼
train_anomaly_model.py ──► model-params JSON ──► S3 (versioned artifact)
                                   │
                                   └──► Kafka ml.model-updates.v1 (compacted, key = metricKey)
                                              │
                                              ▼
                          flink-anomaly-job broadcast state (no redeploy!)
```

## Contract (`ml.model-updates.v1` message value)

Field names are consumed verbatim by `ModelParams.java` (flink-common); both arrays
hold one bucket per hour of week, Monday 00:00 UTC first:

```json
{
  "metricKey": "orders.completed",
  "modelVersion": "2026-07-03T02:00Z-a1b2c3",
  "zThreshold": 4.0,
  "seasonalMeans": [42.1, "… 168 entries"],
  "seasonalStds":  [6.3,  "… 168 entries"]
}
```

Rules:
- **Latest-wins per metricKey** (compacted topic). Rollback = re-publish the previous artifact from S3.
- The job stamps `modelVersion` on every alert → every alert is attributable to a model.
- A missing/withdrawn model degrades gracefully to the pure online EWMA detector (verified in AnomalyEndToEndIT).
- Fitting is robust (median + MAD·1.4826) so past anomalies don't poison the baseline;
  sparse buckets fall back to global statistics (see `tests/`).

## Running

```bash
cd ml
python -m pytest                                   # unit tests (pure math)
python training/train_anomaly_model.py --metrics orders.completed --dry-run
python training/train_anomaly_model.py --metrics orders.completed --publish   # local compose
```

## Roadmap

- [ ] Scheduled retraining workflow (GitHub Actions cron) with a train/validate gate
- [ ] Later: IsolationForest / ONNX in-job scoring if params-on-a-topic stops being enough (ADR #6)
