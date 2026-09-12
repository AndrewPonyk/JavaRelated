"""Unit tests for evaluation metrics and quality gates."""

from __future__ import annotations

import numpy as np
import pytest

from fraud_detection.ml.evaluate import evaluate_model, passes_quality_gates

pytestmark = pytest.mark.unit


def test_metrics_match_hand_computation() -> None:
    y_true = np.array([0, 0, 1, 1])
    y_prob = np.array([0.1, 0.4, 0.35, 0.8])
    metrics = evaluate_model(y_true, y_prob)
    # Concordant pairs: (0.35>0.1), (0.8>0.1), (0.8>0.4); discordant: (0.35<0.4).
    assert metrics["auc"] == pytest.approx(0.75)
    # At threshold 0.5 predictions are [0, 0, 0, 1].
    assert metrics["precision"] == pytest.approx(1.0)
    assert metrics["recall"] == pytest.approx(0.5)
    assert metrics["f1"] == pytest.approx(2 / 3)
    assert metrics["threshold"] == 0.5
    assert metrics["positive_rate"] == pytest.approx(0.25)


def test_single_class_labels_raise() -> None:
    with pytest.raises(ValueError, match="single class"):
        evaluate_model(np.zeros(10), np.linspace(0, 1, 10))


def test_empty_or_mismatched_inputs_raise() -> None:
    with pytest.raises(ValueError):
        evaluate_model(np.array([]), np.array([]))
    with pytest.raises(ValueError):
        evaluate_model(np.array([0, 1]), np.array([0.5]))


def test_quality_gates_pass_and_fail_with_reasons() -> None:
    good = {"auc": 0.9, "recall": 0.8}
    passed, reasons = passes_quality_gates(good)
    assert passed and reasons == []

    bad = {"auc": 0.8, "recall": 0.6}
    passed, reasons = passes_quality_gates(bad, min_auc=0.85, min_recall=0.7)
    assert not passed
    assert len(reasons) == 2
    assert any("auc" in reason for reason in reasons)
    assert any("recall" in reason for reason in reasons)
