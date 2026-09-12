"""Unit tests for ml/features.py — shared by training and the streaming scorer."""

from __future__ import annotations

import pandas as pd

from log_analytics.ml.features import FEATURE_COLUMNS, build_features


def test_empty_input_yields_empty_frame_with_columns() -> None:
    result = build_features(pd.DataFrame(columns=["timestamp", "service", "level", "message"]))
    assert result.empty
    assert list(result.columns) == ["window_start", "service", *FEATURE_COLUMNS]


def test_per_service_window_aggregation(sample_log_records: list[dict]) -> None:
    result = build_features(pd.DataFrame(sample_log_records), window="1min")

    # Both services fall into the same single 1-minute window.
    assert set(result["service"]) == {"checkout", "payments"}
    assert len(result) == 2

    checkout = result[result["service"] == "checkout"].iloc[0]
    payments = result[result["service"] == "payments"].iloc[0]

    assert checkout["log_count"] == 20.0
    assert checkout["error_count"] == 2.0  # i % 10 == 0 → i in {0, 10}
    assert checkout["error_ratio"] == _approx(0.1)

    assert payments["log_count"] == 10.0
    assert payments["error_count"] == 5.0
    assert payments["error_ratio"] == _approx(0.5)
    assert payments["warn_ratio"] == _approx(0.5)
    # payments logs one identical message repeatedly — cardinality collapses to 1
    assert payments["unique_messages"] == 1.0


def test_features_are_floats_without_nans(sample_log_records: list[dict]) -> None:
    result = build_features(pd.DataFrame(sample_log_records))
    for column in FEATURE_COLUMNS:
        assert result[column].dtype == float
        assert not result[column].isna().any()


def _approx(value: float):  # tiny local helper to keep asserts readable
    import pytest

    return pytest.approx(value, rel=1e-9)
