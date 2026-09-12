"""Weekly retrain loop for the streaming anomaly detector.

The stream processor runs the model; Airflow owns the training lifecycle:

    fetch series (Snowflake marts) → fit params → evaluate on holdout
        → publish (Redis hash the processor hot-reloads, + S3 archive)

An unacceptable candidate (holdout alert rate above the noise budget) skips
publish — the champion keeps running. Fitting/evaluation logic lives in
common/anomaly_training.py and is unit-tested without Airflow.
"""

from __future__ import annotations

from datetime import timedelta

import pendulum
from airflow import DAG
from airflow.operators.python import PythonOperator

from common.callbacks import notify_failure
from common.config import CFG

default_args = {
    "owner": "ml-platform",
    "retries": 1,
    "retry_delay": timedelta(minutes=10),
    "on_failure_callback": notify_failure,
}

TRAINING_WINDOW_SQL = """
select metric_name, metric_value
from analytics.marts.fct_business_metrics_daily
where metric_date >= dateadd('day', -30, %(ds)s::date)
order by metric_name, metric_date
"""


def _fetch_training_series(ds: str, **_context) -> dict[str, list[float]]:
    from airflow.providers.snowflake.hooks.snowflake import SnowflakeHook

    hook = SnowflakeHook(snowflake_conn_id=CFG.snowflake_conn_id)
    rows = hook.get_records(TRAINING_WINDOW_SQL, parameters={"ds": ds})
    series: dict[str, list[float]] = {}
    for metric_name, metric_value in rows:
        series.setdefault(metric_name, []).append(float(metric_value))
    if not series:
        from airflow.exceptions import AirflowFailException

        raise AirflowFailException("no training data in the last 30 days — is the mart populated?")
    return series


def _train(ti, **_context) -> dict:
    from common.anomaly_training import fit_detector_params

    series = ti.xcom_pull(task_ids="fetch_training_series")
    params = fit_detector_params(series)
    print(f"fitted params for {sorted(params)}")
    return params


def _evaluate(ti, **_context) -> dict:
    from airflow.exceptions import AirflowSkipException

    from common.anomaly_training import evaluate_params

    series = ti.xcom_pull(task_ids="fetch_training_series")
    params = ti.xcom_pull(task_ids="train_model")
    report = evaluate_params(series, params)
    print(f"evaluation: {report}")
    if not report["acceptable"]:
        # Skipping cascades: publish is skipped and the champion model stays.
        raise AirflowSkipException(f"candidate too noisy on holdout: {report['per_metric']}")
    return report


def _publish(ti, ds_nodash: str, **context) -> dict:
    import os

    from common.anomaly_training import publish_params

    params = ti.xcom_pull(task_ids="train_model")
    version = f"{ds_nodash}-{context['run_id'][-8:]}"
    return publish_params(
        params,
        version,
        redis_url=os.environ.get("REDIS_URL", "redis://localhost:6379/0"),
        s3_bucket=os.environ.get("ARTIFACTS_BUCKET", ""),
    )


with DAG(
    dag_id="anomaly_model_retrain",
    description="Weekly retrain/evaluate/publish for the streaming anomaly model",
    schedule="0 4 * * 1",
    start_date=pendulum.datetime(2026, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    default_args=default_args,
    tags=["ml", "anomaly", "p1"],
    doc_md=__doc__,
) as dag:
    fetch_training_series = PythonOperator(
        task_id="fetch_training_series",
        python_callable=_fetch_training_series,
    )

    train_model = PythonOperator(task_id="train_model", python_callable=_train)
    evaluate_model = PythonOperator(task_id="evaluate_model", python_callable=_evaluate)
    publish_model = PythonOperator(task_id="publish_model", python_callable=_publish)

    fetch_training_series >> train_model >> evaluate_model >> publish_model
