"""Late-data / clock-skew policy (pure function, explicit `now`)."""

from datetime import UTC, datetime, timedelta

from app.core.config import settings
from app.schemas.metric import MetricPoint
from app.services.ingestion import partition_by_age

NOW = datetime(2026, 7, 2, 12, 0, 0, tzinfo=UTC)


def _point(ts: datetime) -> MetricPoint:
    return MetricPoint(device_id="dev-1", metric="temperature", ts=ts, value=1.0)


def test_fresh_points_pass():
    fresh, stale = partition_by_age([_point(NOW), _point(NOW - timedelta(minutes=5))], now=NOW)
    assert len(fresh) == 2 and stale == []


def test_too_old_points_are_rejected():
    too_old = NOW - timedelta(hours=settings.ingest_max_age_hours, minutes=1)
    fresh, stale = partition_by_age([_point(too_old)], now=NOW)
    assert fresh == [] and len(stale) == 1


def test_boundary_age_is_accepted():
    boundary = NOW - timedelta(hours=settings.ingest_max_age_hours)
    fresh, stale = partition_by_age([_point(boundary)], now=NOW)
    assert len(fresh) == 1 and stale == []


def test_future_points_within_skew_tolerance_pass():
    slightly_ahead = NOW + timedelta(minutes=settings.ingest_future_tolerance_minutes - 1)
    fresh, stale = partition_by_age([_point(slightly_ahead)], now=NOW)
    assert len(fresh) == 1 and stale == []


def test_far_future_points_are_rejected():
    far_ahead = NOW + timedelta(minutes=settings.ingest_future_tolerance_minutes + 1)
    fresh, stale = partition_by_age([_point(far_ahead)], now=NOW)
    assert fresh == [] and len(stale) == 1


def test_mixed_batch_partitions_correctly():
    points = [
        _point(NOW),
        _point(NOW - timedelta(hours=999)),
        _point(NOW + timedelta(hours=1)),
    ]
    fresh, stale = partition_by_age(points, now=NOW)
    assert len(fresh) == 1 and len(stale) == 2
