"""Series decimation: bounded responses that keep the newest point."""

from datetime import UTC, datetime, timedelta

from app.api.v1.metrics import decimate
from app.schemas.metric import SeriesPoint


def _series(n: int) -> list[SeriesPoint]:
    base = datetime(2026, 7, 1, tzinfo=UTC)
    return [SeriesPoint(ts=base + timedelta(seconds=i), value=float(i)) for i in range(n)]


def test_small_series_untouched():
    points = _series(50)
    sampled, was_decimated = decimate(points, 100)
    assert sampled == points and not was_decimated


def test_exact_limit_untouched():
    points = _series(100)
    sampled, was_decimated = decimate(points, 100)
    assert sampled == points and not was_decimated


def test_oversized_series_is_decimated_and_bounded():
    points = _series(2500)
    sampled, was_decimated = decimate(points, 100)
    assert was_decimated
    assert len(sampled) <= 101  # stride sample + preserved final point


def test_last_point_is_always_preserved():
    points = _series(1001)
    sampled, _ = decimate(points, 100)
    assert sampled[-1].ts == points[-1].ts


def test_empty_series():
    sampled, was_decimated = decimate([], 100)
    assert sampled == [] and not was_decimated
