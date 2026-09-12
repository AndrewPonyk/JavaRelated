"""Unit tests for experiment + training-run business logic."""
from __future__ import annotations

import pytest

from src.api.schemas.experiment import ExperimentCreate, RunStatus, TrainingRequest
from src.core.errors import ConflictError, NotFoundError
from src.services.experiment_service import ExperimentService


@pytest.fixture
def svc(repo) -> ExperimentService:
    return ExperimentService(repo)


async def test_create_and_get(svc: ExperimentService) -> None:
    created = await svc.create(ExperimentCreate(name="Churn Model"), owner="alice")
    fetched = await svc.get(created.id)
    assert fetched.name == "Churn Model"


async def test_duplicate_name_conflicts(svc: ExperimentService) -> None:
    await svc.create(ExperimentCreate(name="Churn Model"), owner="alice")
    with pytest.raises(ConflictError):
        await svc.create(ExperimentCreate(name="Churn Model"), owner="bob")


async def test_get_missing_not_found(svc: ExperimentService) -> None:
    with pytest.raises(NotFoundError):
        await svc.get("nope")


async def test_list_paginates(svc: ExperimentService) -> None:
    for i in range(5):
        await svc.create(ExperimentCreate(name=f"exp-{i}"), owner="alice")
    page, total = await svc.list(limit=2, offset=0)
    assert total == 5
    assert len(page) == 2


async def test_training_missing_experiment_not_found(svc: ExperimentService) -> None:
    with pytest.raises(NotFoundError):
        await svc.start_training(
            "missing", TrainingRequest(framework="sklearn", dataset_uri="s3://x")
        )


async def test_training_registers_model_version(svc: ExperimentService, repo) -> None:
    exp = await svc.create(ExperimentCreate(name="Churn Model"), owner="alice")
    run = await svc.start_training(
        exp.id, TrainingRequest(framework="xgboost", dataset_uri="s3://data")
    )
    assert run.status is RunStatus.COMPLETED
    versions = await repo.list_model_versions("churn-model")
    assert len(versions) == 1
    assert versions[0].framework == "xgboost"
