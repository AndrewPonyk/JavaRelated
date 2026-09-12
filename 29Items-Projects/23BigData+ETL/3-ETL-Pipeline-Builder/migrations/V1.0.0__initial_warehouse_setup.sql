-- V1.0.0 — Bootstrap databases, warehouses, and functional roles.
-- Run as SYSADMIN (role/grant statements need SECURITYADMIN — split in prod).

-- ── Databases ───────────────────────────────────────────────────────────────
create database if not exists raw
    comment = 'Immutable landing zone. Written by LOADER (COPY INTO / Snowpipe Streaming) only.';
create database if not exists analytics
    comment = 'dbt-managed. Staging/intermediate/marts schemas created by dbt runs.';

create schema if not exists raw.orders   comment = 'Batch-landed order exports';
create schema if not exists raw.events   comment = 'Streaming events via Snowpipe Streaming (MSK Connect)';
create schema if not exists raw.metadata comment = 'Pipeline runs, DQ results, alerts, pipeline registry';

-- ── Warehouses: one per workload so cost and contention are isolated ────────
create warehouse if not exists load_wh
    warehouse_size = 'XSMALL' auto_suspend = 60 auto_resume = true initially_suspended = true
    comment = 'Ingestion: COPY INTO, schemachange';
create warehouse if not exists transform_wh
    warehouse_size = 'SMALL' auto_suspend = 60 auto_resume = true initially_suspended = true
    comment = 'dbt builds; size up for the nightly window only';
create warehouse if not exists serve_wh
    warehouse_size = 'XSMALL' auto_suspend = 60 auto_resume = true initially_suspended = true
    -- TODO(prod): enable multi-cluster (min 1 / max 3) for BI + API concurrency
    comment = 'Read-only serving: metrics API history queries, BI';

-- ── Functional roles (grant to service users; humans come via SSO groups) ───
create role if not exists loader      comment = 'Writes RAW; runs migrations';
create role if not exists transformer comment = 'dbt: reads RAW, owns ANALYTICS';
create role if not exists reporter    comment = 'Read-only on ANALYTICS marts';

grant usage on database raw to role loader;
grant usage on all schemas in database raw to role loader;
grant insert, select on all tables in schema raw.orders to role loader;
grant insert, select on all tables in schema raw.events to role loader;
grant insert, select, update on all tables in schema raw.metadata to role loader;
grant usage on warehouse load_wh to role loader;

grant usage on database raw to role transformer;
grant usage on all schemas in database raw to role transformer;
grant select on future tables in schema raw.orders to role transformer;
grant select on future tables in schema raw.events to role transformer;
grant select on future tables in schema raw.metadata to role transformer;
grant usage, create schema on database analytics to role transformer;
grant usage on warehouse transform_wh to role transformer;

grant usage on database analytics to role reporter;
grant usage on warehouse serve_wh to role reporter;
-- Mart-level SELECT grants are applied by dbt post-hooks / grants config.

-- TODO: create service users with key-pair auth and grant the roles:
--   ETL_LOADER_SVC → loader, ETL_DBT_SVC → transformer, ETL_API_SVC → reporter
