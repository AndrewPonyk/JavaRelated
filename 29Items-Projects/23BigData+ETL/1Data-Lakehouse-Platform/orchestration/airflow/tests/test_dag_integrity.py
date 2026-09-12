"""DAG integrity: every DAG imports cleanly, has no cycles, and follows house rules.

Catches the majority of Airflow production breakage before deploy.
"""

from __future__ import annotations

from pathlib import Path

import pytest

pytest.importorskip("airflow")

from airflow.models import DagBag  # noqa: E402

DAGS_DIR = Path(__file__).resolve().parents[1] / "dags"


@pytest.fixture(scope="session")
def dag_bag() -> DagBag:
    return DagBag(dag_folder=str(DAGS_DIR), include_examples=False)


def test_no_import_errors(dag_bag):
    assert dag_bag.import_errors == {}, f"DAG import failures: {dag_bag.import_errors}"


def test_dags_discovered(dag_bag):
    assert {"medallion_daily", "ml_feature_pipeline"} <= set(dag_bag.dag_ids)


def test_house_rules(dag_bag):
    for dag in dag_bag.dags.values():
        assert dag.catchup is False, f"{dag.dag_id}: explicit backfills only"
        assert dag.default_args.get("retries", 0) >= 1, f"{dag.dag_id}: retries required"
        assert dag.tags, f"{dag.dag_id}: tags required for ownership/filtering"
