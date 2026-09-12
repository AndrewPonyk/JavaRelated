"""Shared fixtures. Unit tests import only Spark-free packages (common/ml/alerting/api)."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest


@pytest.fixture()
def sample_log_records() -> list[dict]:
    """Two services, one minute of traffic: checkout healthy-ish, payments erroring."""
    base = datetime(2026, 7, 1, 12, 0, 0, tzinfo=timezone.utc)
    records: list[dict] = []
    for i in range(20):
        records.append(
            {
                "timestamp": base + timedelta(seconds=3 * i),
                "service": "checkout",
                "level": "ERROR" if i % 10 == 0 else "INFO",
                "message": f"request completed status=200 duration_ms={i}",
            }
        )
    for i in range(10):
        records.append(
            {
                "timestamp": base + timedelta(seconds=6 * i),
                "service": "payments",
                "level": "ERROR" if i % 2 == 0 else "WARN",
                "message": "upstream timeout calling payments-db",
            }
        )
    return records
