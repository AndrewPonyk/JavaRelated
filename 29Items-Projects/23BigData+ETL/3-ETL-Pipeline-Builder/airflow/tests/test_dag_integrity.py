"""DAG integrity tests: every DAG must import cleanly and obey hygiene rules.

Runs in CI with apache-airflow installed (see requirements-dev.txt for the
constraints-pinned install command); auto-skips locally without Airflow.
"""

import pathlib
import sys

import pytest

DAGS_DIR = pathlib.Path(__file__).resolve().parents[1] / "dags"
# Airflow puts the dags folder on sys.path at runtime; mirror that for imports
# like `from common.config import CFG`.
sys.path.insert(0, str(DAGS_DIR))

# Probe airflow.models (not bare "airflow"): the repo's airflow/ directory is a
# namespace-package shadow when CWD is on sys.path (python -m pytest) and would
# fool a bare-name importorskip.
pytest.importorskip("airflow.models", reason="apache-airflow not installed; DAG tests run in CI")

from airflow.models import DagBag  # noqa: E402


@pytest.fixture(scope="session")
def dag_bag() -> "DagBag":
    return DagBag(dag_folder=str(DAGS_DIR), include_examples=False)


def test_no_import_errors(dag_bag):
    assert dag_bag.import_errors == {}, f"DAG import failures: {dag_bag.import_errors}"


def test_expected_dags_present(dag_bag):
    expected = {"etl_daily_batch", "anomaly_model_retrain", "streaming_health"}
    assert expected.issubset(dag_bag.dags.keys())


def test_dag_hygiene(dag_bag):
    for dag_id, dag in dag_bag.dags.items():
        assert dag.catchup is False, f"{dag_id}: catchup must be False (no surprise backfills)"
        assert dag.tags, f"{dag_id}: tags are required"
        assert dag.default_args.get("retries", 0) >= 1, f"{dag_id}: retries >= 1 required"
        assert dag.default_args.get("on_failure_callback"), f"{dag_id}: failure callback required"
