"""End-to-end tests of the training pipeline and local model store."""

from __future__ import annotations

import json

import numpy as np
import pytest

from fraud_detection.ml.features import FEATURE_COLUMNS
from fraud_detection.ml.registry import LocalModelStore
from fraud_detection.ml.train import (
    QualityGateError,
    generate_synthetic_transactions,
    train_pipeline,
)

pytestmark = pytest.mark.model


def test_training_writes_versioned_artifacts_and_baseline(tmp_path) -> None:
    store_dir = tmp_path / "store"
    summary = train_pipeline(model_dir=store_dir, n_samples=3000, seed=5)

    assert summary["gates_passed"] is True
    assert summary["version"] == "1"
    assert summary["data_source"] == "synthetic"
    assert summary["metrics"]["auc"] >= 0.85
    assert summary["metrics"]["recall"] >= 0.7

    version_dir = store_dir / "v1"
    assert (version_dir / "model.joblib").exists()
    assert (version_dir / "metadata.json").exists()
    assert (version_dir / "baseline.json").exists()

    baseline = json.loads((version_dir / "baseline.json").read_text(encoding="utf-8"))
    assert set(baseline) == set(FEATURE_COLUMNS)
    for entry in baseline.values():
        assert len(entry["bin_edges"]) == 11
        assert len(entry["bin_counts"]) == 10
        assert sum(entry["bin_counts"]) > 0

    metadata = json.loads((version_dir / "metadata.json").read_text(encoding="utf-8"))
    assert metadata["version"] == "1"
    assert metadata["feature_names"] == FEATURE_COLUMNS

    assert LocalModelStore(store_dir).get_aliases() == {"champion": "1", "challenger": "1"}


def test_second_training_becomes_challenger_only(tmp_path) -> None:
    store_dir = tmp_path / "store"
    train_pipeline(model_dir=store_dir, n_samples=3000, seed=5)
    summary = train_pipeline(model_dir=store_dir, n_samples=3000, seed=6)
    assert summary["version"] == "2"
    assert LocalModelStore(store_dir).get_aliases() == {"champion": "1", "challenger": "2"}


def test_no_register_mode_writes_nothing(tmp_path) -> None:
    store_dir = tmp_path / "store"
    summary = train_pipeline(model_dir=store_dir, n_samples=3000, seed=5, register=False)
    assert summary["version"] is None
    assert summary["artifact_dir"] is None
    assert not store_dir.exists()


def test_quality_gates_reject_unlearnable_labels(tmp_path) -> None:
    frame = generate_synthetic_transactions(n_samples=4000, seed=3)
    rng = np.random.default_rng(3)
    frame["is_fraud"] = rng.permutation(frame["is_fraud"].to_numpy())
    csv_path = tmp_path / "shuffled.csv"
    frame.to_csv(csv_path, index=False)

    with pytest.raises(QualityGateError) as excinfo:
        train_pipeline(
            model_dir=tmp_path / "store",
            data_path=str(csv_path),
            seed=3,
            register=False,
        )
    assert any("auc" in reason for reason in excinfo.value.reasons)


def test_csv_with_missing_columns_is_rejected(tmp_path) -> None:
    frame = generate_synthetic_transactions(n_samples=100, seed=1).drop(columns=["is_fraud"])
    csv_path = tmp_path / "invalid.csv"
    frame.to_csv(csv_path, index=False)
    with pytest.raises(ValueError, match="missing columns"):
        train_pipeline(model_dir=tmp_path / "store", data_path=str(csv_path), register=False)
