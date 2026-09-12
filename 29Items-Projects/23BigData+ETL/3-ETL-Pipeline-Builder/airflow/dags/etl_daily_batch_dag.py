"""Daily governed ELT: Glue → Snowflake COPY → GE gate → dbt → GE gate → publish.

Write–audit–publish (docs/ARCHITECTURE.md §2.3):
    wait_raw_data → glue_raw_to_staged → copy_into_raw → ge_validate_raw
        → dbt_build → ge_validate_marts → export_lineage → warm_serving_cache
"""

from __future__ import annotations

from datetime import timedelta

import pendulum
from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator
from airflow.providers.amazon.aws.operators.glue import GlueJobOperator
from airflow.providers.amazon.aws.sensors.s3 import S3KeySensor
from airflow.providers.common.sql.operators.sql import SQLExecuteQueryOperator

from common.callbacks import notify_failure, notify_sla_miss, record_success
from common.config import CFG
from common.validation import run_checkpoint

default_args = {
    "owner": "data-platform",
    "retries": 2,
    "retry_delay": timedelta(minutes=5),
    "retry_exponential_backoff": True,
    "on_failure_callback": notify_failure,
    "on_success_callback": record_success,  # full audit trail in PIPELINE_RUNS
}

COPY_INTO_ORDERS_SQL = """
copy into raw.orders.orders_raw (payload, _source_file, _loaded_at)
from (
    select $1, metadata$filename, current_timestamp()
    from @raw.orders.orders_stage/dt={{ ds }}/
)
file_format = (format_name = 'raw.orders.json_gz')
on_error = 'abort_statement';
"""

with DAG(
    dag_id="etl_daily_batch",
    description="Governed batch ELT with GE quality gates (write-audit-publish)",
    schedule="0 2 * * *",
    start_date=pendulum.datetime(2026, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    dagrun_timeout=timedelta(hours=3),
    default_args=default_args,
    sla_miss_callback=notify_sla_miss,
    tags=["batch", "elt", "p0"],
    doc_md=__doc__,
) as dag:
    wait_raw_data = S3KeySensor(
        task_id="wait_raw_data",
        bucket_key=f"s3://{CFG.lake_bucket}/raw/events/dt={{{{ ds }}}}/_SUCCESS",
        aws_conn_id=CFG.aws_conn_id,
        deferrable=True,  # requires the triggerer (MWAA has one; compose standalone too)
        poke_interval=300,
        timeout=60 * 60 * 4,
    )

    glue_raw_to_staged = GlueJobOperator(
        task_id="glue_raw_to_staged",
        job_name=f"{CFG.env}-raw-events-to-parquet",  # created by infrastructure/modules/data_lake
        script_args={"--lake_bucket": CFG.lake_bucket, "--ds": "{{ ds }}"},
        aws_conn_id=CFG.aws_conn_id,
        region_name=CFG.aws_region,
        wait_for_completion=True,
    )

    copy_into_raw = SQLExecuteQueryOperator(
        task_id="copy_into_raw",
        conn_id=CFG.snowflake_conn_id,
        sql=COPY_INTO_ORDERS_SQL,
        sla=timedelta(minutes=45),
    )

    ge_validate_raw = PythonOperator(
        task_id="ge_validate_raw",
        python_callable=run_checkpoint,
        op_kwargs={"checkpoint_name": "raw_orders_checkpoint"},
    )

    # dbt must not live inside the Airflow python env (dependency conflicts on
    # MWAA — TECH-NOTES §3.6 #2). Cloud environments run it as an ECS task
    # (set DBT_ECS_TASK_DEFINITION + ECS_CLUSTER + PRIVATE_SUBNET_IDS); the
    # BashOperator path serves local compose, where dbt shares the container.
    if CFG.dbt_ecs_task_definition:
        from airflow.providers.amazon.aws.operators.ecs import EcsRunTaskOperator

        dbt_build = EcsRunTaskOperator(
            task_id="dbt_build",
            aws_conn_id=CFG.aws_conn_id,
            region_name=CFG.aws_region,
            cluster=CFG.ecs_cluster,
            task_definition=CFG.dbt_ecs_task_definition,
            launch_type="FARGATE",
            overrides={
                "containerOverrides": [
                    {
                        "name": "dbt",
                        "command": ["dbt", "build", "--fail-fast", "--target", CFG.env],
                    }
                ]
            },
            network_configuration={
                "awsvpcConfiguration": {
                    "subnets": list(CFG.private_subnet_ids),
                    "assignPublicIp": "DISABLED",
                }
            },
            sla=timedelta(hours=1),
        )
    else:
        dbt_build = BashOperator(
            task_id="dbt_build",
            bash_command=(
                'set -euo pipefail && cd "$DBT_PROJECT_DIR" && '
                "dbt deps --quiet && "
                'dbt build --fail-fast --profiles-dir "$DBT_PROFILES_DIR" --target "$DBT_TARGET"'
            ),
            env={
                "DBT_PROJECT_DIR": CFG.dbt_project_dir,
                "DBT_PROFILES_DIR": CFG.dbt_profiles_dir,
                "DBT_TARGET": CFG.env if CFG.env in ("dev", "ci", "prod") else "dev",
            },
            append_env=True,  # keep SNOWFLAKE_* from the runtime environment
            sla=timedelta(hours=1),
        )

    ge_validate_marts = PythonOperator(
        task_id="ge_validate_marts",
        python_callable=run_checkpoint,
        op_kwargs={"checkpoint_name": "marts_metrics_checkpoint"},
    )

    export_lineage = BashOperator(
        task_id="export_lineage",
        bash_command=(
            'python "$PROJECT_ROOT/scripts/export_dbt_lineage.py" '
            '--manifest "$DBT_PROJECT_DIR/target/manifest.json" '
            '--out "$PROJECT_ROOT/docs/lineage"'
        ),
        env={"PROJECT_ROOT": CFG.project_root, "DBT_PROJECT_DIR": CFG.dbt_project_dir},
        append_env=True,
    )

    def _warm_serving_cache(**_context) -> None:
        """Push recent mart aggregates into the Redis daily cache so the
        dashboard's daily history is warm before business hours."""
        import os

        from airflow.exceptions import AirflowSkipException
        from airflow.providers.snowflake.hooks.snowflake import SnowflakeHook

        from common.cache_warmer import YESTERDAY_AGGREGATES_SQL, warm_daily_cache

        redis_url = os.environ.get("REDIS_URL", "")
        if not redis_url:
            raise AirflowSkipException("REDIS_URL not configured — skipping cache warm")

        hook = SnowflakeHook(snowflake_conn_id=CFG.snowflake_conn_id)
        rows = hook.get_records(YESTERDAY_AGGREGATES_SQL, parameters={"days": 90})
        written = warm_daily_cache(rows, redis_url=redis_url)
        print(f"warmed {written} daily metric points into the serving cache")

    warm_serving_cache = PythonOperator(
        task_id="warm_serving_cache",
        python_callable=_warm_serving_cache,
    )

    (
        wait_raw_data
        >> glue_raw_to_staged
        >> copy_into_raw
        >> ge_validate_raw
        >> dbt_build
        >> ge_validate_marts
        >> export_lineage
        >> warm_serving_cache
    )
