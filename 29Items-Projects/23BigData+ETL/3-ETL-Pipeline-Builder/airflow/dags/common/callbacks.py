"""Failure / SLA / success callbacks: SNS alerting + run bookkeeping.

Pipeline failures and business anomaly alerts use SEPARATE channels on purpose
— see docs/ARCHITECTURE.md §2.6 (alert taxonomy). Task outcomes are recorded
(best-effort) in RAW.METADATA.PIPELINE_RUNS for the ops audit trail.
"""

from __future__ import annotations

import json
import logging

from common.config import CFG

log = logging.getLogger(__name__)

_RUN_RECORD_SQL = (
    "insert into raw.metadata.pipeline_runs "
    "(run_id, dag_id, task_id, status, started_at, finished_at, details) "
    "select %s, %s, %s, %s, %s, %s, parse_json(%s)"
)


def _publish_sns(subject: str, message: str) -> None:
    if not CFG.alerts_sns_topic_arn:
        log.warning("ALERTS_SNS_TOPIC_ARN not set — alert only logged: %s | %s", subject, message)
        return
    # Imported lazily: keeps DAG parse time fast and local runs boto3-free.
    import boto3

    boto3.client("sns", region_name=CFG.aws_region).publish(
        TopicArn=CFG.alerts_sns_topic_arn,
        Subject=subject[:100],
        Message=message,
    )


def _record_run(context: dict, status: str) -> None:
    """Best-effort bookkeeping — never lets metadata writes break the DAG."""
    try:
        from airflow.providers.snowflake.hooks.snowflake import SnowflakeHook

        ti = context["task_instance"]
        details = json.dumps(
            {
                "try_number": ti.try_number,
                "log_url": ti.log_url,
                "exception": str(context.get("exception", ""))[:500],
            }
        )
        SnowflakeHook(snowflake_conn_id=CFG.snowflake_conn_id).run(
            _RUN_RECORD_SQL,
            parameters=(
                str(context.get("run_id", "")),
                ti.dag_id,
                ti.task_id,
                status,
                ti.start_date,
                ti.end_date,
                details,
            ),
        )
    except Exception:
        log.warning("could not record pipeline run (non-fatal)", exc_info=True)


def notify_failure(context: dict) -> None:
    """Airflow on_failure_callback: one alert per failed task instance."""
    ti = context["task_instance"]
    subject = f"[airflow][{CFG.env}] {ti.dag_id}.{ti.task_id} FAILED"
    message = (
        f"DAG:       {ti.dag_id}\n"
        f"Task:      {ti.task_id}\n"
        f"Execution: {context.get('logical_date')}\n"
        f"Try:       {ti.try_number}\n"
        f"Log:       {ti.log_url}\n"
        f"Exception: {context.get('exception')}\n"
    )
    log.error("%s\n%s", subject, message)
    _publish_sns(subject, message)
    _record_run(context, "failed")


def record_success(context: dict) -> None:
    """on_success_callback for pipelines that keep a full run audit trail."""
    _record_run(context, "success")


def notify_sla_miss(dag, task_list, blocking_task_list, slas, blocking_tis) -> None:
    """sla_miss_callback: 'slow but not failed' is the most dangerous state."""
    subject = f"[airflow][{CFG.env}] SLA MISS in {dag.dag_id}"
    message = f"Tasks over SLA:\n{task_list}\nBlocking:\n{blocking_task_list}"
    log.error("%s\n%s", subject, message)
    _publish_sns(subject, message)
