"""ML feature refresh — data-aware: runs whenever gold order stats are updated.

Scheduling on the Dataset (not cron) means features can never be computed
against stale Silver/Gold data, and backfilling the medallion DAG
automatically re-triggers feature snapshots. The feature job itself performs
drift checks against the previous snapshot and registers the new snapshot in
the governance catalog (see lakehouse/features/customer_order_features.py).
"""

from __future__ import annotations

import pendulum
from airflow import DAG
from lakehouse_ops import GOLD_ORDERS_DATASET, spark_task

default_args = {
    "owner": "ml-platform",
    "retries": 2,
    "retry_delay": pendulum.duration(minutes=5),
}

with DAG(
    dag_id="ml_feature_pipeline",
    description="Refresh customer order features for ML training",
    schedule=[GOLD_ORDERS_DATASET],  # data-aware scheduling
    start_date=pendulum.datetime(2026, 6, 1, tz="UTC"),
    catchup=False,
    default_args=default_args,
    max_active_runs=1,
    tags=["lakehouse", "ml", "features"],
    doc_md=__doc__,
) as dag:
    dq_gate_gold_stats = spark_task(
        "dq_gate_gold_stats",
        "lakehouse.quality.checks",
        ["--table", "gold/sales/order_daily_stats", "--run-date", "{{ ds }}"],
    )

    compute_customer_features = spark_task(
        "compute_customer_features",
        "lakehouse.features.customer_order_features",
        ["--feature-date", "{{ ds }}"],
    )

    dq_gate_gold_stats >> compute_customer_features
