"""Partition-bucket enumeration drives which Cassandra partitions get queried."""

from datetime import UTC, date, datetime

from app.repositories.metrics import day_buckets, month_buckets


def test_day_buckets_single_day():
    start = datetime(2026, 7, 2, 1, 0, tzinfo=UTC)
    end = datetime(2026, 7, 2, 23, 0, tzinfo=UTC)
    assert day_buckets(start, end) == [date(2026, 7, 2)]


def test_day_buckets_spans_midnight():
    start = datetime(2026, 6, 30, 23, 0, tzinfo=UTC)
    end = datetime(2026, 7, 2, 1, 0, tzinfo=UTC)
    assert day_buckets(start, end) == [date(2026, 6, 30), date(2026, 7, 1), date(2026, 7, 2)]


def test_month_buckets_within_one_month():
    start = datetime(2026, 7, 1, tzinfo=UTC)
    end = datetime(2026, 7, 31, tzinfo=UTC)
    assert month_buckets(start, end) == ["2026-07"]


def test_month_buckets_across_year_boundary():
    start = datetime(2025, 11, 15, tzinfo=UTC)
    end = datetime(2026, 2, 1, tzinfo=UTC)
    assert month_buckets(start, end) == ["2025-11", "2025-12", "2026-01", "2026-02"]
