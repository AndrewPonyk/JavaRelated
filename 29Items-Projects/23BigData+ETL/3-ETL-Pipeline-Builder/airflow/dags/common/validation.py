"""Great Expectations checkpoint runner — the hard quality gate inside DAGs.

A failed checkpoint FAILS the task (and therefore halts the DAG): this is the
'audit' step of write–audit–publish. Never downgrade this to a warning.

Each run's outcome is also recorded (best-effort) in
RAW.METADATA.DATA_QUALITY_RESULTS — the quality-lineage evidence trail.
"""

from __future__ import annotations

import json
import logging
import uuid

from common.config import CFG

log = logging.getLogger(__name__)

_RECORD_SQL = (
    "insert into raw.metadata.data_quality_results "
    "(validation_id, checkpoint_name, suite_name, dataset, success, statistics) "
    "select %s, %s, %s, %s, %s, parse_json(%s)"
)


def _record_result(checkpoint_name: str, result) -> None:
    """Persist the outcome for lineage. Best-effort: bookkeeping failures are
    logged, never allowed to fail (or worse, pass) the pipeline gate."""
    try:
        from airflow.providers.snowflake.hooks.snowflake import SnowflakeHook

        hook = SnowflakeHook(snowflake_conn_id=CFG.snowflake_conn_id)
        for validation_id, run_result in result.run_results.items():
            validation = run_result.get("validation_result")
            if validation is None:
                continue
            meta = validation.meta or {}
            hook.run(
                _RECORD_SQL,
                parameters=(
                    uuid.uuid4().hex,
                    checkpoint_name,
                    meta.get("expectation_suite_name", "unknown"),
                    str(meta.get("active_batch_definition", validation_id))[:500],
                    bool(validation.success),
                    json.dumps(dict(validation.statistics or {})),
                ),
            )
    except Exception:
        log.warning("could not record DQ result for %s (non-fatal)", checkpoint_name, exc_info=True)


def run_checkpoint(checkpoint_name: str, **_context) -> None:
    """PythonOperator callable. GE imported lazily (heavy; keeps DAG parse fast)."""
    import great_expectations as gx
    from airflow.exceptions import AirflowFailException

    context = gx.get_context(context_root_dir=CFG.ge_root_dir)
    result = context.run_checkpoint(checkpoint_name=checkpoint_name)

    _record_result(checkpoint_name, result)

    if not result.success:
        raise AirflowFailException(
            f"Great Expectations checkpoint '{checkpoint_name}' failed — halting pipeline. "
            f"See Data Docs for the failing expectations."
        )
    log.info("Checkpoint '%s' passed.", checkpoint_name)
