# Snowflake migrations (schemachange)

Versioned, **forward-only** DDL for all Snowflake objects that dbt does not own
(databases, warehouses, roles, raw landing tables, metadata tables).

## Naming

`V<major>.<minor>.<patch>__<description>.sql` — applied in version order, recorded in
`METADATA.SCHEMACHANGE.CHANGE_HISTORY`. **Never edit an applied migration** (checksums are
verified); ship a new version instead.

## Run

```bash
pip install schemachange
schemachange \
  -f migrations \
  -a "$SNOWFLAKE_ACCOUNT" -u "$SNOWFLAKE_USER" -r SYSADMIN -w LOAD_WH \
  -c "RAW.METADATA.CHANGE_HISTORY" --create-change-history-table
```

CD runs the same command per environment (see `.github/workflows/cd.yml`).

## Ownership boundaries

| Owner | Objects |
|---|---|
| schemachange (here) | databases, schemas, warehouses, roles/grants, RAW tables, stages, file formats, metadata tables |
| dbt | everything inside `ANALYTICS` staging/intermediate/marts |
| Terraform | AWS only (never Snowflake) |
