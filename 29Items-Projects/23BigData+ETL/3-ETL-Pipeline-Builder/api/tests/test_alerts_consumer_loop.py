"""Alerts background consumer: consume loop + resilient retry (fake Kafka)."""

import asyncio
import json
from types import SimpleNamespace

from test_alerts_api import make_alert

from app.config import get_settings
from app.services.alert_service import AlertService
from app.services.alerts_consumer import AlertsConsumer


class FakeAlertKafkaConsumer:
    """Serves queued batches; sets `stop` once drained so tests terminate."""

    def __init__(self, batches, stop: asyncio.Event) -> None:
        self._batches = list(batches)
        self._stop = stop
        self.started = False
        self.stopped = False

    async def start(self):
        self.started = True

    async def stop(self):
        self.stopped = True

    async def getmany(self, timeout_ms, max_records):
        if not self._batches:
            self._stop.set()
            return {}
        raw = self._batches.pop(0)
        return {("alerts.anomaly.v1", 0): [SimpleNamespace(value=v) for v in raw]}


async def test_consume_mirrors_alerts_into_store(fake_redis):
    stop = asyncio.Event()
    payloads = [
        json.dumps(make_alert("a1")).encode(),
        b"not json",
        json.dumps(make_alert("a2")).encode(),
    ]
    fake_kafka = FakeAlertKafkaConsumer([payloads], stop)
    store = AlertService(client=fake_redis)
    consumer = AlertsConsumer(get_settings(), store, consumer_factory=lambda: fake_kafka)

    await consumer._consume(stop)

    assert consumer.alerts_ingested == 2  # bad envelope dropped, not fatal
    assert {a.alert_id for a in await store.recent()} == {"a1", "a2"}
    assert fake_kafka.started and fake_kafka.stopped


async def test_run_retries_after_broker_failure_then_recovers(fake_redis):
    stop = asyncio.Event()
    store = AlertService(client=fake_redis)
    attempts = {"n": 0}

    def flaky_factory():
        attempts["n"] += 1
        if attempts["n"] == 1:
            raise ConnectionError("broker unavailable")
        return FakeAlertKafkaConsumer([[json.dumps(make_alert("a9")).encode()]], stop)

    consumer = AlertsConsumer(
        get_settings(), store, consumer_factory=flaky_factory, retry_seconds=0.01
    )

    await asyncio.wait_for(consumer.run(stop), timeout=5)

    assert attempts["n"] == 2  # failed once, reconnected, consumed
    assert [a.alert_id for a in await store.recent()] == ["a9"]


async def test_run_exits_promptly_when_stopped(fake_redis):
    stop = asyncio.Event()
    stop.set()
    consumer = AlertsConsumer(get_settings(), AlertService(client=fake_redis))

    await asyncio.wait_for(consumer.run(stop), timeout=1)  # no kafka touched
