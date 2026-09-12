import json
import time

import pytest
from processor_fakes import FakeKafkaProducer, FakeRedis

from processor.anomaly.detector import AnomalyResult
from processor.metrics_aggregator import MetricPoint
from processor.sinks.alert_publisher import AlertPublisher
from processor.sinks.dead_letter import DeadLetterPublisher
from processor.sinks.redis_sink import RedisMetricSink


@pytest.fixture()
def fake_redis():
    return FakeRedis()


def recent_window_start() -> int:
    return (int(time.time() * 1000) // 500) * 500


async def test_redis_sink_writes_current_history_and_publishes(fake_redis):
    sink = RedisMetricSink("redis://unused", client=fake_redis, history_retention_minutes=60)
    start_ms = recent_window_start()
    point = MetricPoint("orders_per_second", 12.0, start_ms, 500)

    await sink.write(point)

    current = fake_redis.hashes["metric:orders_per_second"]
    assert current["value"] == "12.0"
    assert current["window_start_ms"] == str(start_ms)

    history = fake_redis.zsets["metric:orders_per_second:history"]
    member, score = next(iter(history.items()))
    assert score == start_ms
    assert json.loads(member)["value"] == 12.0

    channel, message = fake_redis.published[0]
    assert channel == "metrics.updates"
    assert json.loads(message)["metric"] == "orders_per_second"


async def test_redis_sink_write_is_idempotent_per_window(fake_redis):
    sink = RedisMetricSink("redis://unused", client=fake_redis)
    point = MetricPoint("orders_per_second", 12.0, recent_window_start(), 500)

    await sink.write(point)
    await sink.write(point)  # at-least-once redelivery

    history = fake_redis.zsets["metric:orders_per_second:history"]
    assert len(history) == 1  # same member, same score — no duplicates


async def test_redis_sink_trims_history_older_than_retention(fake_redis):
    sink = RedisMetricSink("redis://unused", client=fake_redis, history_retention_minutes=60)
    stale_ms = int(time.time() * 1000) - 2 * 60 * 60 * 1000  # 2 h ago > 60 min retention

    await sink.write(MetricPoint("orders_per_second", 5.0, stale_ms, 500))
    await sink.write(MetricPoint("orders_per_second", 12.0, recent_window_start(), 500))

    history = fake_redis.zsets["metric:orders_per_second:history"]
    values = [json.loads(m)["value"] for m in history]
    assert values == [12.0]  # stale point evicted, fresh point kept


def _anomaly(metric="orders_per_second", score=5.0):
    return AnomalyResult(
        metric=metric, value=500.0, score=score, is_anomaly=True, baseline_mean=100.0
    )


async def test_alert_publisher_sends_envelope():
    producer = FakeKafkaProducer()
    publisher = AlertPublisher(topic="alerts.anomaly.v1", producer=producer)

    assert await publisher.publish(_anomaly()) is True

    topic, payload = producer.sent[0]
    alert = json.loads(payload)
    assert topic == "alerts.anomaly.v1"
    assert alert["metric"] == "orders_per_second"
    assert alert["severity"] == "warning"
    assert alert["alert_id"]


async def test_alert_publisher_cooldown_suppresses_repeats():
    producer = FakeKafkaProducer()
    publisher = AlertPublisher(topic="t", cooldown_seconds=60, producer=producer)

    assert await publisher.publish(_anomaly()) is True
    assert await publisher.publish(_anomaly()) is False  # within cooldown
    assert await publisher.publish(_anomaly(metric="revenue_per_second")) is True  # other metric

    assert len(producer.sent) == 2


async def test_alert_severity_escalates_on_extreme_score():
    producer = FakeKafkaProducer()
    publisher = AlertPublisher(topic="t", producer=producer)

    await publisher.publish(_anomaly(score=12.0))

    assert json.loads(producer.sent[0][1])["severity"] == "critical"


async def test_dead_letter_envelope_preserves_raw_payload():
    producer = FakeKafkaProducer()
    dlq = DeadLetterPublisher(producer, "events.deadletter.v1", source_topic="events.orders.v1")

    await dlq.publish(b"\xff{not json", "Expecting value")

    topic, payload = producer.sent[0]
    envelope = json.loads(payload)
    assert topic == "events.deadletter.v1"
    assert envelope["source_topic"] == "events.orders.v1"
    assert envelope["error"] == "Expecting value"
    import base64

    assert base64.b64decode(envelope["payload_b64"]) == b"\xff{not json"
    assert dlq.dead_lettered == 1
