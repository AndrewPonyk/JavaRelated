-- V1.1.0 — Raw landing tables, file formats, and external stage.
-- VARIANT-first landing: producers evolve payloads without DDL churn;
-- schema is applied (and tested) in dbt staging models.

-- ── Batch: order exports landed in S3, loaded via COPY INTO ────────────────
create file format if not exists raw.orders.json_gz
    type = json
    compression = gzip
    strip_outer_array = true
    comment = 'Gzipped JSON-lines exports in the S3 lake';

-- TODO: create the storage integration once (account-level, needs ACCOUNTADMIN):
--   create storage integration lake_s3_int
--     type = external_stage storage_provider = 'S3'
--     storage_aws_role_arn = 'arn:aws:iam::<acct>:role/snowflake-lake-read'
--     enabled = true storage_allowed_locations = ('s3://etl-pipeline-builder-*-lake/');
create stage if not exists raw.orders.orders_stage
    url = 's3://etl-pipeline-builder-dev-lake/staged/orders/'
    -- storage_integration = lake_s3_int   -- TODO: uncomment after the integration exists
    file_format = raw.orders.json_gz
    comment = 'Staged order exports (partitioned by dt=YYYY-MM-DD)';

create table if not exists raw.orders.orders_raw (
    payload      variant       not null,
    _source_file varchar       comment 'metadata$filename at COPY time',
    _loaded_at   timestamp_ntz not null default current_timestamp()
)
comment = 'Append-only landing for order exports. Deduped downstream in dbt staging.';

-- ── Streaming: MSK Connect (Snowpipe Streaming) target ──────────────────────
create table if not exists raw.events.stream_events (
    record_metadata variant      comment 'Kafka metadata written by the Snowflake connector',
    record_content  variant      not null comment 'Event envelope: event_id, event_type, ts_ms, ...',
    _ingested_at    timestamp_ntz not null default current_timestamp()
)
comment = 'Landing for events.orders.v1 via the Snowflake Kafka connector (Snowpipe Streaming).';
