"""Reusable KFP component: scheduled batch drift evaluation.

Runs on the batch plane (not the serving cluster) so heavy distribution
comparisons never contend with low-latency inference.
"""
from __future__ import annotations

from kfp import dsl


@dsl.component(
    base_image="python:3.12-slim",
    packages_to_install=["scipy", "numpy", "mlflow", "boto3"],
)
def drift_check(model_name: str, baseline_uri: str, window_hours: int = 24) -> float:
    """Compute drift (PSI) for ``model_name`` over the recent window.

    Emits the aggregate drift score; the platform persists a DriftReport and
    raises a ``drift.detected`` event when the score crosses threshold.
    TODO: load baseline + recent production features, compute per-feature PSI.
    """
    return 0.0
