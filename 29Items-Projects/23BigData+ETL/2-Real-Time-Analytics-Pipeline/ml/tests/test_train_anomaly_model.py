"""Unit tests for the trainer's pure math — no DB/Kafka/S3 needed."""
import datetime as dt

import pandas as pd
import pytest

from training.train_anomaly_model import (
    HOURS_PER_WEEK,
    build_params,
    fit_seasonal_baseline,
    hour_of_week,
)

MONDAY = dt.datetime(2024, 1, 1, tzinfo=dt.timezone.utc)  # 2024-01-01 is a Monday


def frame_from(rows):
    frame = pd.DataFrame(rows, columns=["window_start", "value_sum"])
    frame["window_start"] = pd.to_datetime(frame["window_start"], utc=True)
    return frame


def test_hour_of_week_matches_java_contract():
    stamps = pd.Series(pd.to_datetime([
        MONDAY,                                  # Monday 00:00 → 0
        MONDAY + dt.timedelta(hours=1),          # Monday 01:00 → 1
        MONDAY + dt.timedelta(days=1),           # Tuesday 00:00 → 24
        MONDAY - dt.timedelta(hours=1),          # Sunday 23:00 → 167
    ], utc=True))

    assert list(hour_of_week(stamps)) == [0, 1, 24, 167]


def test_fits_per_bucket_medians_with_full_data():
    # Four weeks of data: Monday 00:xx is consistently ~200, everything else ~50.
    rows = []
    for week in range(4):
        for hour in range(HOURS_PER_WEEK):
            base = MONDAY + dt.timedelta(weeks=week, hours=hour)
            level = 200.0 if hour == 0 else 50.0
            for minute in (0, 20, 40):  # 3 samples per bucket per week
                rows.append((base + dt.timedelta(minutes=minute), level + minute / 100))

    means, stds = fit_seasonal_baseline(frame_from(rows))

    assert len(means) == HOURS_PER_WEEK
    assert len(stds) == HOURS_PER_WEEK
    assert means[0] == pytest.approx(200.2, abs=0.5)   # the busy Monday-midnight bucket
    assert means[25] == pytest.approx(50.2, abs=0.5)   # a quiet bucket
    assert all(s >= 0 for s in stds)


def test_sparse_buckets_fall_back_to_global_stats():
    # Only two Monday-00 samples (below MIN_SAMPLES_PER_BUCKET) amid rich other data.
    rows = [(MONDAY + dt.timedelta(minutes=m), 999.0) for m in (0, 30)]
    for day in range(1, 22):  # 3 weeks of hourly samples at level 50
        for hour in range(24):
            rows.append((MONDAY + dt.timedelta(days=day, hours=hour), 50.0))

    means, _ = fit_seasonal_baseline(frame_from(rows))

    assert means[0] == pytest.approx(50.0)  # sparse bucket got the global median, not 999


def test_outliers_do_not_poison_the_baseline():
    rows = []
    for week in range(4):
        for sample in range(5):
            rows.append((MONDAY + dt.timedelta(weeks=week, minutes=sample * 10), 100.0 + sample))
    rows.append((MONDAY + dt.timedelta(weeks=1, minutes=5), 10_000.0))  # the anomaly itself

    means, _ = fit_seasonal_baseline(frame_from(rows))

    assert means[0] < 150.0  # median shrugged off the 10k spike


def test_refuses_to_train_on_empty_history():
    with pytest.raises(ValueError, match="no history"):
        fit_seasonal_baseline(pd.DataFrame(columns=["window_start", "value_sum"]))


def test_build_params_matches_model_params_contract():
    means = [1.0] * HOURS_PER_WEEK
    stds = [0.5] * HOURS_PER_WEEK

    params = build_params("orders.completed", means, stds, 4.0)

    # Exact field names consumed by ModelParams.java via Jackson
    assert set(params) == {"metricKey", "modelVersion", "zThreshold", "seasonalMeans", "seasonalStds"}
    assert params["metricKey"] == "orders.completed"
    assert params["zThreshold"] == 4.0
    assert len(params["seasonalMeans"]) == HOURS_PER_WEEK
    assert len(params["seasonalStds"]) == HOURS_PER_WEEK
    assert params["modelVersion"]  # timestamped + unique suffix
