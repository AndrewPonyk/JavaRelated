"""Validate production seams: ORM mapping integrity and the trainer factory."""
from __future__ import annotations

import pytest

from src.db.models import ABTest, Base, DriftReport, Experiment, ModelVersion, Run
from src.ml.training.trainer import (
    SklearnTrainer,
    TensorFlowTrainer,
    TorchTrainer,
    get_trainer,
)


def test_orm_metadata_declares_all_tables() -> None:
    # Guards against mapping errors the Alembic migration relies on.
    tables = set(Base.metadata.tables)
    assert tables == {"experiments", "runs", "model_versions", "ab_tests", "drift_reports"}


def test_orm_relationships_present() -> None:
    assert "runs" in Experiment.__mapper__.relationships
    assert "ab_tests" in Experiment.__mapper__.relationships
    # Touch the remaining models so import/mapping is exercised.
    assert Run.__tablename__ == "runs"
    assert ModelVersion.__mapper__.primary_key[0].name == "name"
    assert ABTest.__tablename__ == "ab_tests"
    assert DriftReport.__tablename__ == "drift_reports"


@pytest.mark.parametrize(
    "framework,expected",
    [
        ("sklearn", SklearnTrainer),
        ("xgboost", SklearnTrainer),
        ("pytorch", TorchTrainer),
        ("tensorflow", TensorFlowTrainer),
    ],
)
def test_get_trainer_maps_frameworks(framework: str, expected: type) -> None:
    assert isinstance(get_trainer(framework), expected)


def test_get_trainer_rejects_unknown_framework() -> None:
    with pytest.raises(ValueError):
        get_trainer("scikit-magic")
