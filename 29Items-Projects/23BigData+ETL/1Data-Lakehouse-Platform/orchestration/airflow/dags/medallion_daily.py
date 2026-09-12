"""Daily medallion pipeline: Bronze → Silver → DQ gate → Gold → dbt → maintenance.

Design rules for all DAGs in this platform:
- DAGs are declarative only: no business logic, no heavy top-level imports.
- Every task is idempotent for its {{ ds }} — retries and backfills are safe.
- A DQ failure blocks everything downstream of that table, nothing else.
- Publishing an Airflow Dataset lets downstream DAGs (ML features) schedule
  data-aware instead of using cron guesswork.

Spark submission is environment-driven (see lakehouse_ops.spark_task): EMR
Serverless when configured, the local runner otherwise.
"""

from __future__ import annotations

import pendulum
from airflow import DAG
from airflow.operators.bash import BashOperator
from lakehouse_ops import DBT_DIR, GOLD_ORDERS_DATASET, spark_task

default_args = {
    "owner": "data-engineering",
    "retries": 2,
    "retry_delay": pendulum.duration(minutes=5),
    "retry_exponential_backoff": True,
}

with DAG(
    dag_id="medallion_daily",
    description="Promote orders through Bronze → Silver → Gold with DQ gates",
    schedule="0 2 * * *",  # 02:00 UTC, after upstream day closes
    start_date=pendulum.datetime(2026, 6, 1, tz="UTC"),
    catchup=False,
    default_args=default_args,
    max_active_runs=1,  # serialize writers per table → no Delta write conflicts
    tags=["lakehouse", "medallion", "sales"],
    doc_md=__doc__,
) as dag:
    drain_kafka_to_bronze = spark_task(
        "drain_kafka_to_bronze",
        "lakehouse.ingestion.kafka_bronze_stream",
        ["--trigger", "available-now"],
    )

    bronze_to_silver = spark_task(
        "bronze_to_silver",
        "lakehouse.jobs.bronze_to_silver",
        ["--run-date", "{{ ds }}"],
    )

    dq_gate_silver_orders = spark_task(
        "dq_gate_silver_orders",
        "lakehouse.quality.checks",
        ["--table", "silver/sales/orders", "--run-date", "{{ ds }}"],
    )

    silver_to_gold = spark_task(
        "silver_to_gold",
        "lakehouse.jobs.silver_to_gold",
        ["--run-date", "{{ ds }}"],
        outlets=[GOLD_ORDERS_DATASET],
    )

    dbt_build_marts = BashOperator(
        task_id="dbt_build_marts",
        bash_command=f"dbt build --project-dir {DBT_DIR} --profiles-dir {DBT_DIR}/profiles",
    )

    table_maintenance = spark_task(
        "table_maintenance",
        "lakehouse.jobs.table_maintenance",
        ["--retention-hours", "168"],
    )

    (
        drain_kafka_to_bronze
        >> bronze_to_silver
        >> dq_gate_silver_orders
        >> silver_to_gold
        >> dbt_build_marts
        >> table_maintenance
    )
