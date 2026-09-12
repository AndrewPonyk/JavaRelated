"""Unit tests for the default serving predictor."""
from __future__ import annotations

from src.ml.serving.predictor import HashPredictor, load_predictor


def test_prediction_is_deterministic() -> None:
    p = HashPredictor(version=3)
    features = {"tenure": 12, "charges": 79.5}
    assert p.predict(features) == p.predict(features)


def test_prediction_in_unit_interval() -> None:
    p = HashPredictor(version=1)
    for tenure in range(50):
        score = p.predict({"tenure": tenure})
        assert 0.0 < score < 1.0


def test_empty_features_returns_prior() -> None:
    assert HashPredictor(version=1).predict({}) == 0.5


def test_version_changes_prediction() -> None:
    features = {"tenure": 12}
    assert HashPredictor(1).predict(features) != HashPredictor(2).predict(features)


def test_loader_returns_predictor_for_version() -> None:
    predictor = load_predictor("churn", 7)
    assert predictor.version == 7
