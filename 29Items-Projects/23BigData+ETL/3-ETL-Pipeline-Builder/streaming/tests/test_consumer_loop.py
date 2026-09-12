"""Consumer stream loop + poison-event validation (fake Kafka via factory injection)."""

import asyncio
import json
import time
from types import SimpleNamespace

from processor.config import Settings
from processor.consumer import EventConsumer


class FakeAIOConsumer:
    """Yields queued batches once, then empty polls; records lifecycle + commits."""

    def __init__(self, batches: list[list[bytes]]) -> None:
        self._batches = list(batches)
        self.commits = 0
        self.started = False
        self.stopped = False

    async def start(self):
        self.started = True

    async def stop(self):
        self.stopped = True

    async def commit(self):
        self.commits += 1

    async def getmany(self, timeout_ms, max_records):
        if not self._batches:
            return {}
        raw = self._batches.pop(0)
        return {("events.orders.v1", 0): [SimpleNamespace(value=v) for v in raw]}


def valid_event(event_id="e1", **overrides) -> bytes:
    data = {
        "event_id": event_id,
        "event_type": "order_placed",
        "ts_ms": int(time.time() * 1000),
        "amount": 10.0,
        **overrides,
    }
    return json.dumps(data).encode()


async def test_stream_batches_parses_quarantines_and_commits():
    quarantined = []

    async def dlq(raw, error):
        quarantined.append(error)

    fake = FakeAIOConsumer([[valid_event("e1"), b"garbage", valid_event("e2")]])
    consumer = EventConsumer(Settings(), dead_letter=dlq, consumer_factory=lambda: fake)
    stop = asyncio.Event()

    gen = consumer.stream_batches(stop)
    batch = await anext(gen)
    assert [e.event_id for e in batch] == ["e1", "e2"]

    stop.set()  # drain: empty poll → loop exits
    async for _ in gen:
        raise AssertionError("no further batches expected")

    assert fake.commits == 1  # committed only after the batch was consumed
    assert fake.started and fake.stopped
    assert consumer.events_consumed == 2
    assert consumer.malformed_messages == 1
    assert quarantined  # the garbage went to the DLQ


async def test_non_finite_amount_is_poison():
    quarantined = []

    async def dlq(raw, error):
        quarantined.append(error)

    consumer = EventConsumer(Settings(), dead_letter=dlq)

    assert await consumer._parse(valid_event(amount="NaN")) is None
    assert await consumer._parse(valid_event(amount="Infinity")) is None

    assert consumer.malformed_messages == 2
    assert all("poison metric baselines" in e for e in quarantined)


async def test_far_future_timestamp_is_poison_but_small_drift_is_fine():
    consumer = EventConsumer(Settings())
    now_ms = int(time.time() * 1000)

    # 30 s of clock drift is tolerated…
    assert await consumer._parse(valid_event(ts_ms=now_ms + 30_000)) is not None
    # …an hour in the future would poison the watermark → quarantined.
    assert await consumer._parse(valid_event(ts_ms=now_ms + 3_600_000)) is None
    assert consumer.malformed_messages == 1
