"""Influx telemetry client: line building, no-op when down, close ordering."""

from app.db import influx


class FakeWriteApi:
    def __init__(self, log: list):
        self.records: list = []
        self._log = log

    def write(self, bucket, record):
        self.records.append((bucket, record))

    def close(self):
        self._log.append("write_api")


class FakeClient:
    def __init__(self, log: list):
        self._log = log

    def close(self):
        self._log.append("client")


def test_write_point_builds_tags_and_fields(monkeypatch):
    fake = FakeWriteApi([])
    monkeypatch.setattr(influx, "_write_api", fake)

    influx.write_point("ingest", fields={"points": 3, "latency_ms": 1.5}, tags={"env": "test"})

    assert len(fake.records) == 1
    _, record = fake.records[0]
    line = record.to_line_protocol()
    assert line.startswith("ingest,env=test ")
    assert "points=3i" in line and "latency_ms=1.5" in line


def test_write_point_noops_when_disconnected(monkeypatch):
    monkeypatch.setattr(influx, "_write_api", None)
    monkeypatch.setattr(influx, "_client", None)
    influx.write_point("m", fields={"v": 1})  # must not raise
    assert not influx.is_connected()


def test_write_point_swallows_backend_errors(monkeypatch):
    class ExplodingApi:
        def write(self, bucket, record):
            raise RuntimeError("influx hiccup")

    monkeypatch.setattr(influx, "_write_api", ExplodingApi())
    influx.write_point("m", fields={"v": 1})  # telemetry never fails the caller


async def test_close_flushes_write_api_before_client(monkeypatch):
    order: list[str] = []
    monkeypatch.setattr(influx, "_write_api", FakeWriteApi(order))
    monkeypatch.setattr(influx, "_client", FakeClient(order))

    await influx.close()

    assert order == ["write_api", "client"]
    assert influx._client is None and influx._write_api is None
    assert not influx.is_connected()
