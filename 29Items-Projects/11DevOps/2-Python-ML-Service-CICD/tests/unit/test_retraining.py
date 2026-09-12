"""Unit tests for the retraining service (real local training runs)."""

from __future__ import annotations

import time

import pytest

from fraud_detection.db.repositories import ModelVersionRepository
from fraud_detection.db.session import get_session_factory
from fraud_detection.ml.registry import LocalModelStore
from fraud_detection.services.model_loader import ModelLoader
from fraud_detection.services.retraining import RetrainingService

pytestmark = pytest.mark.unit


def _wait_terminal(job, timeout: float = 120.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if job.status in ("succeeded", "failed"):
            return job
        time.sleep(0.2)
    raise TimeoutError(f"job stuck in status {job.status!r}")


def test_local_retraining_registers_challenger(settings, db_session) -> None:
    loader = ModelLoader()
    assert loader.get_model("challenger").version == "1"

    service = RetrainingService(model_loader=loader)
    job = service.submit(reason="manual", debounce=False)
    assert job is not None and service.get(job.job_id) is job
    _wait_terminal(job)

    assert job.status == "succeeded", job.detail
    assert job.result is not None and job.result["version"] == "2"
    assert job.finished_at is not None

    store = LocalModelStore(settings.model_dir)
    assert store.get_aliases() == {"champion": "1", "challenger": "2"}
    # Cache was invalidated, so the challenger alias now serves the new model.
    assert loader.get_model("challenger").version == "2"

    with get_session_factory()() as session:
        row = ModelVersionRepository(session).get_by_version("2")
        assert row is not None and row.stage == "challenger"


def test_debounce_swallows_rapid_auto_triggers(settings) -> None:
    service = RetrainingService()
    first = service.submit(reason="drift:log_amount", debounce=True)
    assert first is not None
    assert service.submit(reason="drift:log_amount", debounce=True) is None
    # Manual triggers bypass the debounce window.
    manual = service.submit(reason="manual", debounce=False)
    assert manual is not None
    _wait_terminal(first)
    _wait_terminal(manual)


def test_unknown_job_lookup_returns_none(settings) -> None:
    assert RetrainingService().get("does-not-exist") is None
