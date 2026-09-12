"""z-score detector: the always-available fallback must be trustworthy."""

from datetime import UTC, datetime, timedelta

from app.services.anomaly_detection import detect, zscore_anomalies


def _series(values: list[float]):
    base = datetime(2026, 7, 1, 0, 0, tzinfo=UTC)
    return [(base + timedelta(minutes=i), v) for i, v in enumerate(values)]


def test_flags_a_spike():
    history = _series([10.0] * 29 + [100.0])
    found = zscore_anomalies("dev-1", "temperature", history, threshold=3.0)

    assert len(found) == 1
    anomaly = found[0]
    assert anomaly.value == 100.0
    assert anomaly.method == "zscore"
    assert anomaly.score >= 3.0
    assert anomaly.upper is not None and anomaly.value > anomaly.upper


def test_constant_series_yields_nothing():
    assert zscore_anomalies("dev-1", "temperature", _series([21.5] * 50)) == []


def test_series_too_short_yields_nothing():
    assert zscore_anomalies("dev-1", "temperature", _series([1.0, 2.0])) == []


def test_normal_noise_within_threshold_yields_nothing():
    values = [20.0 + (0.5 if i % 2 else -0.5) for i in range(60)]
    assert zscore_anomalies("dev-1", "temperature", _series(values), threshold=3.0) == []


def test_detect_skips_series_below_min_history():
    # settings.min_history_points defaults to 48; 10 points must short-circuit
    # before any model (incl. Prophet import) is touched.
    assert detect("dev-1", "temperature", _series([10.0] * 10)) == []
