"""Unit tests for A/B variant assignment and significance evaluation."""
from __future__ import annotations

import pytest

from src.core.errors import ConflictError, NotFoundError, UnprocessableError
from src.services.ab_testing_service import ABTestingService


@pytest.fixture
def svc(repo, telemetry) -> ABTestingService:
    return ABTestingService(repo, telemetry)


def _config(**over) -> dict:
    base = {
        "model_name": "churn",
        "champion_version": 1,
        "challenger_version": 2,
        "challenger_traffic_pct": 50.0,
    }
    base.update(over)
    return base


async def test_no_active_test_defaults_to_champion(svc: ABTestingService) -> None:
    assert await svc.resolve_variant("churn", subject_id="user-1") == "champion"


async def test_assignment_is_stable_for_same_subject(svc: ABTestingService) -> None:
    await svc.create_test(_config())
    first = await svc.resolve_variant("churn", subject_id="user-42")
    second = await svc.resolve_variant("churn", subject_id="user-42")
    assert first == second
    assert first in {"champion", "challenger"}


async def test_no_subject_resolves_to_champion(svc: ABTestingService) -> None:
    await svc.create_test(_config())
    assert await svc.resolve_variant("churn", subject_id=None) == "champion"


async def test_duplicate_active_test_conflicts(svc: ABTestingService) -> None:
    await svc.create_test(_config())
    with pytest.raises(ConflictError):
        await svc.create_test(_config())


async def test_same_versions_unprocessable(svc: ABTestingService) -> None:
    with pytest.raises(UnprocessableError):
        await svc.create_test(_config(champion_version=1, challenger_version=1))


async def test_evaluate_missing_test_not_found(svc: ABTestingService) -> None:
    with pytest.raises(NotFoundError):
        await svc.evaluate("does-not-exist")


async def test_evaluate_insufficient_data_is_inconclusive(svc: ABTestingService) -> None:
    test_id = await svc.create_test(_config())
    result = await svc.evaluate(test_id)
    assert result["significant"] is False
    assert result["p_value"] == 1.0


async def test_evaluate_detects_significant_difference(svc: ABTestingService) -> None:
    test_id = await svc.create_test(_config())
    # Two clearly-separated, non-degenerate samples (real variance in each).
    for i in range(30):
        await svc.record_outcome("churn", "champion", 0.50 + (i % 5) * 0.01)
        await svc.record_outcome("churn", "challenger", 0.90 + (i % 5) * 0.01)
    result = await svc.evaluate(test_id)
    assert result["significant"] is True
    assert result["challenger_metric"] > result["champion_metric"]
