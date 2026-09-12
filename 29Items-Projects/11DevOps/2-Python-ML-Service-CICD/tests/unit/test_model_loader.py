"""Unit tests for the two-tier model loader."""

from __future__ import annotations

from datetime import UTC, datetime

import pytest

from fraud_detection.api import deps
from fraud_detection.core.config import get_settings
from fraud_detection.ml.features import FEATURE_COLUMNS, build_feature_vector
from fraud_detection.ml.registry import LocalModelStore
from fraud_detection.monitoring.metrics import FRAUD_MODEL_FALLBACK_TOTAL
from fraud_detection.services.model_loader import ModelLoader

pytestmark = pytest.mark.unit


def _feature_dict(amount: float = 120.5, category: str = "electronics") -> dict[str, float]:
    features = build_feature_vector(amount, category, datetime(2026, 7, 12, 14, tzinfo=UTC), {})
    features["amount"] = amount
    features["merchant_category"] = category  # type: ignore[assignment]
    return features


def test_resolves_local_store(settings) -> None:
    model = ModelLoader().get_model("champion")
    assert model.source == "local"
    assert model.version == "1"
    assert 0.0 <= model.predict_proba(_feature_dict()) <= 1.0


def test_prediction_is_deterministic(settings) -> None:
    model = ModelLoader().get_model("champion")
    features = _feature_dict()
    assert model.predict_proba(features) == model.predict_proba(features)


def test_fallback_when_store_is_empty(settings, tmp_path, monkeypatch) -> None:
    monkeypatch.setenv("FRAUD_MODEL_DIR", str(tmp_path / "empty-store"))
    get_settings.cache_clear()
    deps.reset_singletons()
    before = FRAUD_MODEL_FALLBACK_TOTAL._value.get()
    model = ModelLoader().get_model("champion")
    assert model.source == "fallback"
    assert FRAUD_MODEL_FALLBACK_TOTAL._value.get() == before + 1
    assert 0.0 <= model.predict_proba({"amount": 500.0, "merchant_category": "travel"}) <= 1.0


def test_cache_and_invalidate(settings, model_dir) -> None:
    loader = ModelLoader()
    first = loader.get_model("challenger")
    assert first is loader.get_model("challenger")  # cached
    assert first.version == "1"

    store = LocalModelStore(model_dir)
    pipeline, metadata = store.load("1")
    new_version = store.save_version(
        pipeline,
        {
            "metrics": metadata.get("metrics", {}),
            "feature_names": FEATURE_COLUMNS,
            "trained_at": datetime.now(UTC).isoformat(),
            "data_source": "copy-of-v1",
        },
        store.load_baseline("1") or {},
    )
    assert new_version == "2"

    # Still cached until invalidated.
    assert loader.get_model("challenger").version == "1"
    loader.invalidate()
    assert loader.get_model("challenger").version == "2"
