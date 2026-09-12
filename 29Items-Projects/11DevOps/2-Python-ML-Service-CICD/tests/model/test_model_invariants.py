"""Behavioural invariants of the serving models (real and fallback)."""

from __future__ import annotations

import numpy as np
import pytest

from fraud_detection.ml.registry import LocalModelStore
from fraud_detection.services.model_loader import HeuristicStubModel, SklearnLocalModel

pytestmark = pytest.mark.model


@pytest.fixture()
def local_model(trained_model_dir) -> SklearnLocalModel:
    pipeline, metadata = LocalModelStore(trained_model_dir).load("champion")
    return SklearnLocalModel(pipeline, metadata["feature_names"], metadata["version"])


def test_local_model_bounds_and_determinism_under_fuzzing(local_model) -> None:
    rng = np.random.default_rng(1234)
    for _ in range(200):
        features = {
            "log_amount": float(rng.uniform(0, 12)),
            "hour_of_day": float(rng.integers(0, 24)),
            "merchant_category_hash": float(rng.uniform(0, 1)),
        }
        probability = local_model.predict_proba(features)
        assert 0.0 <= probability <= 1.0
        assert probability == local_model.predict_proba(features)


def test_local_model_tolerates_missing_features(local_model) -> None:
    assert 0.0 <= local_model.predict_proba({}) <= 1.0
    assert 0.0 <= local_model.predict_proba({"log_amount": 5.0}) <= 1.0


def test_local_model_declares_identity(local_model) -> None:
    assert local_model.source == "local"
    assert local_model.version == "1"


def test_fallback_is_monotone_in_amount() -> None:
    model = HeuristicStubModel()
    scores = [
        model.predict_proba({"amount": amount, "merchant_category": "electronics"})
        for amount in (1, 10, 100, 1_000, 10_000, 100_000)
    ]
    assert scores == sorted(scores)


def test_fallback_is_deterministic_and_bounded() -> None:
    model = HeuristicStubModel("challenger")
    assert model.source == "fallback"
    assert model.version == "stub-challenger"
    features = {"amount": 250.0, "merchant_category": "travel"}
    first = model.predict_proba(features)
    assert first == model.predict_proba(features)
    assert 0.0 <= first <= 1.0
    # Degenerate inputs stay bounded.
    assert 0.0 <= model.predict_proba({}) <= 1.0
    assert 0.0 <= model.predict_proba({"amount": -50, "merchant_category": ""}) <= 1.0
