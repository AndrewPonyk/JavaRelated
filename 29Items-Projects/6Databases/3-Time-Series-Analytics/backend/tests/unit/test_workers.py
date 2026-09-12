"""Worker orchestration against fake repositories (no databases)."""

from datetime import UTC, datetime, timedelta

import pytest

from app.schemas.device import Device
from app.schemas.metric import SeriesPoint
from app.workers import anomaly_worker, downsampler


def _rollup_series(values: list[float]) -> list[SeriesPoint]:
    base = datetime(2026, 7, 1, tzinfo=UTC)
    return [SeriesPoint(ts=base + timedelta(hours=i), value=v) for i, v in enumerate(values)]


def _device(device_id: str, enabled: bool = True) -> Device:
    return Device(device_id=device_id, name=device_id, site="s", device_type="t", enabled=enabled)


@pytest.fixture()
def fleet(monkeypatch):
    """Two enabled devices + one disabled; one metric each; spiky history."""
    devices = [_device("dev-a"), _device("dev-b"), _device("dev-off", enabled=False)]
    history = _rollup_series([10.0] * 59 + [100.0])  # 60 points, one spike
    inserted: list = []
    published: list = []

    async def fake_list_all():
        return devices

    async def fake_metrics_for(device_id):
        return ["temperature"]

    async def fake_rollup(device_id, metric, start, end):
        return history

    async def fake_raw(device_id, metric, start, end):
        return []

    async def fake_insert_many(items):
        inserted.extend(items)

    async def fake_publish(device_id, payload):
        published.append(device_id)

    monkeypatch.setattr("app.repositories.devices.list_all", fake_list_all)
    monkeypatch.setattr("app.repositories.series_catalog.metrics_for", fake_metrics_for)
    monkeypatch.setattr("app.repositories.metrics.query_rollup_1h", fake_rollup)
    monkeypatch.setattr("app.repositories.metrics.query_raw", fake_raw)
    monkeypatch.setattr("app.repositories.anomalies.insert_many", fake_insert_many)
    monkeypatch.setattr("app.services.aggregation.publish_anomaly_event", fake_publish)
    monkeypatch.setattr("app.core.config.settings.detection_method", "zscore")
    return {"inserted": inserted, "published": published}


async def test_scan_once_detects_and_persists(fleet):
    found = await anomaly_worker.scan_once()
    # one spike per enabled device; the disabled device is skipped
    assert found == 2
    assert len(fleet["inserted"]) == 2
    assert set(fleet["published"]) == {"dev-a", "dev-b"}
    assert all(a.value == 100.0 and a.method == "zscore" for a in fleet["inserted"])


async def test_scan_survives_a_broken_series(fleet, monkeypatch):
    async def explode(device_id, metric, start, end):
        if device_id == "dev-a":
            raise RuntimeError("boom")
        return _rollup_series([10.0] * 59 + [100.0])

    monkeypatch.setattr("app.repositories.metrics.query_rollup_1h", explode)
    found = await anomaly_worker.scan_once()
    assert found == 1  # dev-b still scanned


def test_in_shard_partitions_the_fleet(monkeypatch):
    monkeypatch.setattr("app.core.config.settings.worker_shard_count", 2)
    ids = [f"dev-{i}" for i in range(50)]

    monkeypatch.setattr("app.core.config.settings.worker_shard_index", 0)
    shard0 = {d for d in ids if anomaly_worker.in_shard(d)}
    monkeypatch.setattr("app.core.config.settings.worker_shard_index", 1)
    shard1 = {d for d in ids if anomaly_worker.in_shard(d)}

    assert shard0 | shard1 == set(ids)
    assert shard0 & shard1 == set()
    assert shard0 and shard1  # both shards get work


def test_previous_full_hour():
    now = datetime(2026, 7, 2, 13, 42, 31, tzinfo=UTC)
    start, end = downsampler.previous_full_hour(now)
    assert start == datetime(2026, 7, 2, 12, 0, tzinfo=UTC)
    assert end == datetime(2026, 7, 2, 13, 0, tzinfo=UTC)


async def test_rollup_series_computes_aggregates(monkeypatch):
    base = datetime(2026, 7, 2, 12, 0, tzinfo=UTC)
    raw = [
        SeriesPoint(ts=base + timedelta(minutes=i), value=v) for i, v in enumerate([2.0, 8.0, 5.0])
    ]
    captured: dict = {}

    async def fake_raw(device_id, metric, start, end):
        return raw

    async def fake_insert(device_id, metric, ts, *, vmin, vmax, avg, vsum, count):
        captured.update(vmin=vmin, vmax=vmax, avg=avg, vsum=vsum, count=count, ts=ts)

    monkeypatch.setattr("app.repositories.metrics.query_raw", fake_raw)
    monkeypatch.setattr("app.repositories.metrics.insert_rollup_1h", fake_insert)

    wrote = await downsampler.rollup_series("dev-a", "temperature", base, base + timedelta(hours=1))
    assert wrote
    assert captured == {"vmin": 2.0, "vmax": 8.0, "avg": 5.0, "vsum": 15.0, "count": 3, "ts": base}


async def test_rollup_series_skips_empty_windows(monkeypatch):
    async def fake_raw(device_id, metric, start, end):
        return []

    monkeypatch.setattr("app.repositories.metrics.query_raw", fake_raw)
    base = datetime(2026, 7, 2, 12, 0, tzinfo=UTC)
    assert not await downsampler.rollup_series(
        "dev-a", "temperature", base, base + timedelta(hours=1)
    )


async def test_backfill_walks_each_hour(monkeypatch):
    windows: list = []

    async def fake_run_window(start, end):
        windows.append((start, end))
        return 0

    monkeypatch.setattr(downsampler, "run_window", fake_run_window)
    await downsampler.backfill(5)
    assert len(windows) == 5
    assert all(end - start == timedelta(hours=1) for start, end in windows)
    assert windows == sorted(windows)  # oldest first
