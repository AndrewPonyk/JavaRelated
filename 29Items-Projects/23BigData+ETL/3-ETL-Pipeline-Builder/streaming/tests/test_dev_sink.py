"""Dev warehouse sink: buffering/flush behavior (the SQL body needs a live
Snowflake account and is exercised only when DEV_SNOWFLAKE_SINK_ENABLED)."""

from processor.metrics_aggregator import Event
from processor.sinks.snowflake_sink import SnowflakeDevSink


def make_event(i: int) -> Event:
    return Event(event_id=f"e{i}", event_type="order_placed", ts_ms=1000 + i, amount=1.0)


async def test_buffers_until_threshold_then_flushes(monkeypatch):
    flushed: list[list[Event]] = []
    sink = SnowflakeDevSink(flush_every=3)
    monkeypatch.setattr(sink, "_flush_sync", lambda batch: flushed.append(batch))

    await sink.add(make_event(1))
    await sink.add(make_event(2))
    assert flushed == []  # below threshold

    await sink.add(make_event(3))
    assert len(flushed) == 1 and len(flushed[0]) == 3

    await sink.add(make_event(4))
    await sink.flush()  # explicit drain (shutdown path)
    assert len(flushed) == 2 and len(flushed[1]) == 1

    await sink.flush()  # empty flush is a no-op
    assert len(flushed) == 2
