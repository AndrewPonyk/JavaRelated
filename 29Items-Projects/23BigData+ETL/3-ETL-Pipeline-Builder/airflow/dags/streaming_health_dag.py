"""Speed-layer watchdog (every 5 min): the batch plane alarms on the streaming plane.

Checks (implemented in common/health.py, unit-tested without Airflow):
  1. Hot-store freshness — the newest window in Redis must be younger than the SLO.
  2. MSK consumer lag for the metrics-processor group (CloudWatch MaxOffsetLag);
     skips cleanly in environments without MSK (local compose).
Failures page via the standard pipeline-failure channel (SNS → Slack).
"""

from __future__ import annotations

from datetime import timedelta

import pendulum
from airflow import DAG
from airflow.operators.python import PythonOperator

from common.callbacks import notify_failure
from common.config import CFG

default_args = {
    "owner": "data-platform",
    "retries": 1,
    "retry_delay": timedelta(minutes=1),
    "on_failure_callback": notify_failure,
}

MAX_CONSUMER_LAG = 10_000  # messages
MAX_STALENESS_SECONDS = 30  # hot-store freshness SLO


def _check_hot_store_freshness(**_context) -> None:
    import os

    from airflow.exceptions import AirflowFailException, AirflowSkipException

    from common.health import HotStoreStale, check_hot_store_freshness

    redis_url = os.environ.get("REDIS_URL", "")
    if not redis_url:
        raise AirflowSkipException("REDIS_URL not configured for this Airflow environment")
    try:
        staleness = check_hot_store_freshness(
            redis_url, max_staleness_seconds=MAX_STALENESS_SECONDS
        )
    except HotStoreStale as exc:
        raise AirflowFailException(str(exc)) from exc
    print(f"hot store staleness: {staleness:.1f}s")


def _check_consumer_lag(**_context) -> None:
    import os

    from airflow.exceptions import AirflowFailException, AirflowSkipException

    from common.health import ConsumerLagExceeded, check_consumer_lag

    cluster_name = os.environ.get("MSK_CLUSTER_NAME", "")
    if not cluster_name:
        raise AirflowSkipException("MSK_CLUSTER_NAME not set — no MSK in this environment")
    try:
        lag = check_consumer_lag(
            cluster_name=cluster_name,
            consumer_group="metrics-processor",
            region=CFG.aws_region,
            threshold=MAX_CONSUMER_LAG,
        )
    except ConsumerLagExceeded as exc:
        raise AirflowFailException(str(exc)) from exc
    print(f"consumer lag: {lag:.0f}")


with DAG(
    dag_id="streaming_health",
    description="Watchdog: consumer lag + hot-store freshness for the sub-second path",
    schedule="*/5 * * * *",
    start_date=pendulum.datetime(2026, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    default_args=default_args,
    tags=["streaming", "watchdog", "p0"],
    doc_md=__doc__,
) as dag:
    check_hot_store_freshness = PythonOperator(
        task_id="check_hot_store_freshness",
        python_callable=_check_hot_store_freshness,
    )

    check_consumer_lag = PythonOperator(
        task_id="check_consumer_lag",
        python_callable=_check_consumer_lag,
    )

    # Independent checks — no ordering dependency between them.
