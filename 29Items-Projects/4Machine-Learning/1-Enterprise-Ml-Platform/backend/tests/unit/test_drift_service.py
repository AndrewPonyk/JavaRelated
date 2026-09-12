"""Unit tests for the drift service orchestration + edge cases."""
from __future__ import annotations

import numpy as np
import pytest

from src.core.errors import NotFoundError
from src.services.drift_service import DriftService


@pytest.fixture
def svc(repo, telemetry) -> DriftService:
    return DriftService(repo, telemetry)


async def test_check_without_baseline_is_not_drifted(svc: DriftService) -> None:
    report = await svc.run_check("churn")
    assert report["drifted"] is False
    assert report["evaluated_features"] == 0


async def test_latest_without_report_not_found(svc: DriftService) -> None:
    with pytest.raises(NotFoundError):
        await svc.latest("churn")


async def test_log_inference_ignores_non_numeric(svc: DriftService, telemetry) -> None:
    await svc.log_inference("churn", {"tenure": 12, "plan": "gold", "active": True}, 0.7)
    window = telemetry.window("churn")
    assert "tenure" in window and "plan" not in window and "active" not in window


async def test_detects_drift_against_baseline(svc: DriftService) -> None:
    rng = np.random.default_rng(0)
    svc.set_baseline("churn", {"tenure": rng.normal(0, 1, 2000).tolist()})
    for value in rng.normal(4, 1, 2000):  # strongly shifted live window
        await svc.log_inference("churn", {"tenure": float(value)}, 0.5)
    report = await svc.run_check("churn")
    assert report["drifted"] is True
    assert report["evaluated_features"] == 1
    # The latest report is now retrievable.
    assert (await svc.latest("churn"))["drifted"] is True
