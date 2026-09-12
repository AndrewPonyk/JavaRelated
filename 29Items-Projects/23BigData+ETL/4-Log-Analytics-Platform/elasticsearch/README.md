# Search schema as code

OpenSearch/Elasticsearch is this platform's database, so its schema is versioned here the
same way SQL projects version DDL migrations.

## How it works

- Each `migrations/NNNN_*.json` file is one migration: an `id`, a `description`, and an
  ordered list of REST `requests` (`method`, `path`, `body`).
- `python scripts/es_migrate.py` applies them **in filename order, idempotently**: applied
  ids are recorded in the `la-migrations-state` index, so re-running is always safe (CI/CD
  runs it on every deploy, before services roll).
- Migrations are **forward-only and additive** (new fields, new templates, new indices).
  Never edit an applied migration — add a new one. Breaking mapping changes require a new
  index + reindex + alias flip, expressed as new migrations.

## Current schema

| # | What | Why |
|---|---|---|
| 0001 | ISM policy `la-logs-rollover` | hot (rollover 30 GB/1d) → warm (force-merge, 3d) → delete (30d) |
| 0002 | Component templates `la-logs-settings` / `la-logs-mappings` | shared building blocks; `dynamic: false` + `flat_object` attributes prevent mapping explosion |
| 0003 | Index template `la-logs` + bootstrap `la-logs-000001` | write alias `la-logs`, rollover wired to the ISM policy |
| 0004 | Index template + bootstrap for `la-anomalies` | ML scores per service-window |
| 0005 | Index template + bootstrap for `la-alerts` | fired alerts (triage dashboards, audit) |
| 0006 | Index `la-alert-rules` | CRUD storage for the admin API (strict mapping) |

## Notes

- Targets **OpenSearch 2.x** (matches AWS). On vanilla Elasticsearch: ISM (`_plugins/_ism`)
  becomes ILM, and `flat_object` becomes `flattened` — see TECH-NOTES pitfall #2. Skip 0001
  with `--skip 0001` if you must run against ES locally.
- Verify lifecycle wiring after the first deploy:
  `GET _plugins/_ism/explain/la-logs-000001`.
