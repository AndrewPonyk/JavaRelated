# Dashboards as code (OpenSearch Dashboards / Kibana)

Dashboards are built interactively in the UI, then **exported into this directory** so they
are versioned, reviewed, and re-importable into any environment.

## Import (new environment)

UI: *Stack/Dashboards Management → Saved Objects → Import* → pick `saved_objects/*.ndjson`.

API:

```bash
curl -s -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "osd-xsrf: true" \
  --form file=@saved_objects/index-patterns.ndjson
```

(For vanilla Kibana the header is `kbn-xsrf: true`. Exports are **not** cross-compatible
between Kibana and OpenSearch Dashboards — we standardize on OpenSearch Dashboards to match
AWS; see TECH-NOTES pitfall #13.)

## Export (after editing dashboards)

*Saved Objects → select the dashboard (include related objects) → Export* → commit the
`.ndjson` here with a meaningful name.

## Contents

Import `index-patterns.ndjson` first — the boards reference its index patterns.

| File | What |
|---|---|
| `saved_objects/index-patterns.ndjson` | index patterns for `la-logs*`, `la-anomalies*`, `la-alerts*` |
| `saved_objects/la-log-overview.ndjson` | **LA — Log Overview**: stacked volume by level over time, volume by service, top error templates, error counter |
| `saved_objects/la-anomaly-triage.ndjson` | **LA — Anomaly Triage**: max score per service over time, score distribution, anomalous windows table, model-version split |
| `saved_objects/la-alert-history.ndjson` | **LA — Alert History**: alerts by severity over time, by rule, recent-alerts triage list, alerted services |

Further edits happen in the UI; re-export over these files (same object ids ⇒ clean diffs).
