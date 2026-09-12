"""Behavioral tests for the Isolation Forest wrapper: outliers score higher,
persistence round-trips exactly, registry latest-pointer works."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd
import pytest

pytest.importorskip("sklearn")

from log_analytics.ml.features import FEATURE_COLUMNS
from log_analytics.ml.isolation_forest import LogAnomalyDetector, ModelMetadata
from log_analytics.ml.registry import LocalModelRegistry, new_version


def _frame(rows: np.ndarray) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=FEATURE_COLUMNS)


@pytest.fixture(scope="module")
def normal_traffic() -> pd.DataFrame:
    rng = np.random.default_rng(7)
    n = 300
    log_count = rng.normal(1000, 50, n).clip(min=1)
    error_count = rng.normal(10, 3, n).clip(min=0)
    return _frame(
        np.column_stack(
            [
                log_count,
                error_count,
                error_count / log_count,
                rng.normal(0.05, 0.01, n).clip(0, 1),
                rng.normal(120, 10, n).clip(min=1),
                rng.normal(80, 5, n).clip(min=1),
            ]
        )
    )


@pytest.fixture(scope="module")
def outage_traffic() -> pd.DataFrame:
    # Error storm: volume x3, errors x60, huge messages (stack traces).
    return _frame(
        np.array(
            [
                [3000.0, 600.0, 0.2, 0.3, 400.0, 400.0],
                [2800.0, 700.0, 0.25, 0.28, 380.0, 420.0],
                [3200.0, 900.0, 0.28, 0.35, 500.0, 500.0],
            ]
        )
    )


def test_outliers_score_higher_than_normal(
    normal_traffic: pd.DataFrame, outage_traffic: pd.DataFrame
) -> None:
    detector = LogAnomalyDetector(random_state=42).fit(normal_traffic)
    normal_scores = detector.score(normal_traffic)
    outage_scores = detector.score(outage_traffic)

    assert normal_scores.shape == (len(normal_traffic),)
    assert float(outage_scores.min()) > float(np.median(normal_scores))
    assert float(outage_scores.mean()) > 0.8  # clearly anomalous vs training distribution
    assert normal_scores.min() >= 0.0 and outage_scores.max() <= 1.0


def test_scoring_before_fit_raises() -> None:
    with pytest.raises(RuntimeError, match="not fitted"):
        LogAnomalyDetector().score(_frame(np.zeros((1, len(FEATURE_COLUMNS)))))


def test_missing_feature_columns_rejected(normal_traffic: pd.DataFrame) -> None:
    detector = LogAnomalyDetector().fit(normal_traffic)
    with pytest.raises(ValueError, match="missing columns"):
        detector.score(pd.DataFrame({"log_count": [1.0]}))


def test_save_load_roundtrip_scores_identically(
    tmp_path: Path, normal_traffic: pd.DataFrame, outage_traffic: pd.DataFrame
) -> None:
    detector = LogAnomalyDetector(random_state=42).fit(normal_traffic)
    detector.save(tmp_path / "model.joblib")

    reloaded = LogAnomalyDetector.load(tmp_path / "model.joblib")
    np.testing.assert_allclose(reloaded.score(outage_traffic), detector.score(outage_traffic))


def test_local_registry_latest_pointer(tmp_path: Path, normal_traffic: pd.DataFrame) -> None:
    registry = LocalModelRegistry(tmp_path)
    detector = LogAnomalyDetector(random_state=42).fit(normal_traffic)

    v1 = registry.save(detector, ModelMetadata(version="20260101-000000", n_samples=300))
    v2 = registry.save(detector, ModelMetadata(version="20260201-000000", n_samples=300))
    assert v1 != v2

    loaded, metadata = registry.load()  # latest
    assert metadata.version == v2
    assert loaded.score(normal_traffic).shape == (len(normal_traffic),)

    _, old = registry.load(version=v1)
    assert old.version == v1


def test_new_version_is_sortable() -> None:
    assert len(new_version()) == 15  # YYYYMMDD-HHMMSS
