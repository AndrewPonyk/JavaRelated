import json

from processor.config import Settings
from processor.consumer import EventConsumer


def make_consumer(collector=None):
    async def dead_letter(raw, error):
        collector.append((raw, error))

    return EventConsumer(Settings(), dead_letter=dead_letter if collector is not None else None)


async def test_parse_valid_event_extracts_envelope_and_attrs():
    consumer = make_consumer()
    raw = json.dumps(
        {
            "event_id": "e1",
            "event_type": "order_placed",
            "ts_ms": 1234,
            "amount": 9.5,
            "customer_id": "c1",
            "currency": "EUR",
        }
    ).encode()

    event = await consumer._parse(raw)

    assert event is not None
    assert event.event_id == "e1"
    assert event.amount == 9.5
    assert event.attrs == {"customer_id": "c1", "currency": "EUR"}


async def test_parse_poison_message_goes_to_dlq():
    quarantined = []
    consumer = make_consumer(quarantined)

    assert await consumer._parse(b"not json at all") is None
    assert await consumer._parse(json.dumps({"event_type": "x"}).encode()) is None  # no event_id
    assert (
        await consumer._parse(
            json.dumps({"event_id": "e", "event_type": "x", "ts_ms": "NaN?"}).encode()
        )
        is None
    )

    assert consumer.malformed_messages == 3
    assert len(quarantined) == 3
    assert all(error for _raw, error in quarantined)


async def test_dlq_failure_never_raises():
    async def broken_dlq(raw, error):
        raise ConnectionError("kafka down")

    consumer = EventConsumer(Settings(), dead_letter=broken_dlq)

    assert await consumer._parse(b"junk") is None  # swallowed, counted
    assert consumer.malformed_messages == 1
