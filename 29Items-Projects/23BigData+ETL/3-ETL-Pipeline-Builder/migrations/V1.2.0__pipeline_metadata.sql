-- V1.2.0 — Operational metadata: run history, data-quality results,
-- anomaly alert archive, and the pipeline registry backing the API's CRUD.

create table if not exists raw.metadata.pipeline_runs (
    run_id        varchar        not null,
    dag_id        varchar        not null,
    task_id       varchar,
    status        varchar        not null,           -- running | success | failed
    started_at    timestamp_ntz  not null,
    finished_at   timestamp_ntz,
    rows_processed number,
    details       variant,
    constraint pk_pipeline_runs primary key (run_id, dag_id, task_id)
)
comment = 'One row per Airflow task attempt; written by DAG callbacks. TODO: wire callbacks.';

create table if not exists raw.metadata.data_quality_results (
    validation_id   varchar       not null,
    checkpoint_name varchar       not null,
    suite_name      varchar       not null,
    dataset         varchar       not null,
    success         boolean       not null,
    statistics      variant,
    run_at          timestamp_ntz not null default current_timestamp(),
    constraint pk_dq_results primary key (validation_id)
)
comment = 'Great Expectations checkpoint outcomes — quality evidence per dataset version (lineage).';

create table if not exists raw.metadata.anomaly_alerts (
    alert_id     varchar       not null,
    metric       varchar       not null,
    metric_value double        not null,
    score        double        not null,
    severity     varchar       not null,             -- info | warning | critical
    message      varchar,
    triggered_at timestamp_ntz not null,
    acknowledged boolean       default false,
    constraint pk_anomaly_alerts primary key (alert_id)
)
comment = 'Archive of streaming anomaly alerts (hot copies fan out via SNS).';

create table if not exists raw.metadata.pipelines (
    id            varchar       not null,
    name          varchar       not null,
    description   varchar,
    schedule      varchar       not null,             -- 5-field cron
    source        varchar       not null,
    target        varchar       not null,
    transform_ref varchar,                            -- dbt selector or Glue job name
    status        varchar       not null default 'draft',
    created_at    timestamp_ntz not null default current_timestamp(),
    updated_at    timestamp_ntz not null default current_timestamp(),
    constraint pk_pipelines primary key (id),
    constraint uq_pipelines_name unique (name)
)
comment = 'Pipeline registry backing the API CRUD (api/app/services/pipeline_service.py TODO).';
