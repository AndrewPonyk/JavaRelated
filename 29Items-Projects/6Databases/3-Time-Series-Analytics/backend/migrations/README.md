# Cassandra Migrations

Plain, **ordered**, **idempotent** CQL files (`IF NOT EXISTS` everywhere).
Cassandra has no mature Alembic equivalent — numbered files + a runner script
is the pragmatic standard.

## Rules

1. Never edit an applied migration — add a new numbered file.
2. Every statement must be idempotent (running the whole set twice is a no-op;
   the integration suite asserts this).
3. Schema-affecting settings that matter (TTL, compaction) live HERE, in CQL,
   not in application code. `.env` values like `RAW_TTL_SECONDS` are
   informational mirrors for humans.

## Apply

```bash
# local (cassandra running via docker compose; files are mounted at /migrations)
./scripts/apply_migrations.sh

# manually, one file
docker compose exec cassandra cqlsh -f /migrations/001_keyspace.cql
```

In deployment, migrations run **before** the app rollout (see
`ci/deploy.gitlab-ci.yml` TODO note).

## Files

| File | Contents |
|---|---|
| `001_keyspace.cql` | keyspace `tsa` (dev RF1; prod topology in comments) |
| `002_metrics_tables.cql` | `metrics_raw` (30d TTL, TWCS 1d windows), `metrics_rollup_1h` (365d TTL, TWCS 30d windows) |
| `003_devices_and_anomalies.cql` | `devices` registry, `anomalies` (90d TTL) |
| `004_users_and_catalog.cql` | `users` (dashboard auth, PBKDF2 hashes), `series_catalog` (device → metric discovery) |

Idempotency of the full set is asserted by
`tests/integration/test_stack_roundtrip.py::test_migrations_are_idempotent`.
