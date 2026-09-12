"""Model quality tests: gates on stored metrics and fresh-holdout generalization."""

from __future__ import annotations

import json

import numpy as np
import pytest

from fraud_detection.ml.evaluate import evaluate_model, passes_quality_gates
from fraud_detection.ml.features import FEATURE_COLUMNS, build_features
from fraud_detection.ml.registry import LocalModelStore
from fraud_detection.ml.train import generate_synthetic_transactions

pytestmark = pytest.mark.model


def test_registered_champion_meets_quality_gates(trained_model_dir) -> None:
    metadata = json.loads((trained_model_dir / "v1" / "metadata.json").read_text(encoding="utf-8"))
    passed, reasons = passes_quality_gates(metadata["metrics"])
    assert passed, reasons


def test_champion_generalizes_to_fresh_holdout(trained_model_dir) -> None:
    pipeline, metadata = LocalModelStore(trained_model_dir).load("champion")
    holdout = build_features(generate_synthetic_transactions(n_samples=3000, seed=99))
    y_prob = pipeline.predict_proba(holdout[metadata["feature_names"]])[:, 1]
    metrics = evaluate_model(holdout["is_fraud"].to_numpy(), y_prob)
    # Fresh, never-seen sample: allow a small generalization gap below the gate.
    assert metrics["auc"] >= 0.8
    assert metrics["recall"] >= 0.6


def test_champion_cannot_score_shuffled_labels(trained_model_dir) -> None:
    pipeline, metadata = LocalModelStore(trained_model_dir).load("champion")
    holdout = build_features(generate_synthetic_transactions(n_samples=3000, seed=17))
    rng = np.random.default_rng(17)
    shuffled = rng.permutation(holdout["is_fraud"].to_numpy())
    y_prob = pipeline.predict_proba(holdout[metadata["feature_names"]])[:, 1]
    metrics = evaluate_model(shuffled, y_prob)
    passed, _ = passes_quality_gates(metrics)
    assert not passed
    assert abs(metrics["auc"] - 0.5) < 0.1


def test_feature_columns_are_stable_contract(trained_model_dir) -> None:
    metadata = json.loads((trained_model_dir / "v1" / "metadata.json").read_text(encoding="utf-8"))
    assert (
        metadata["feature_names"]
        == FEATURE_COLUMNS
        == [
            "log_amount",
            "hour_of_day",
            "merchant_category_hash",
        ]
    )
