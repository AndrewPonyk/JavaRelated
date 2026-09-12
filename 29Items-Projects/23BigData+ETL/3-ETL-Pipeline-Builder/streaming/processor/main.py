"""Processor entrypoint: consume → window → detect → sink.

Wiring only — every stage lives in its own testable module.

Graceful shutdown (SIGTERM/SIGINT → ECS rolling deploys):
  stop consuming → flush open windows to the hot store → exit 0.
Note the at-least-once tradeoff: offsets for events sitting in still-open
windows were already committed, so a hard kill (no SIGTERM) loses those
windows in the HOT PATH only — the warehouse branch (MSK Connect) is
unaffected and the hot store self-heals on the next window.
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
import signal

from processor.anomaly.detector import EwmaAnomalyDetector
from processor.config import Settings
from processor.consumer import EventConsumer
from processor.metrics_aggregator import TumblingWindowAggregator
from processor.model_reloader import ModelReloader
from processor.sinks.alert_publisher import AlertPublisher
from processor.sinks.dead_letter import DeadLetterPublisher
from processor.sinks.redis_sink import RedisMetricSink

log = logging.getLogger(__name__)


def _install_signal_handlers(stop: asyncio.Event) -> None:
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGTERM, signal.SIGINT):
        # Windows dev runs lack add_signal_handler — Ctrl+C raises instead.
        with contextlib.suppress(NotImplementedError):
            loop.add_signal_handler(sig, stop.set)


async def _stats_reporter(stop: asyncio.Event, interval: int, sources: dict) -> None:
    while not stop.is_set():
        with contextlib.suppress(TimeoutError):
            await asyncio.wait_for(stop.wait(), timeout=interval)
        log.info(
            "stats: consumed=%d late_dropped=%d malformed=%d dead_lettered=%d "
            "alerts=%d model_version=%s",
            sources["consumer"].events_consumed,
            sources["aggregator"].late_events_dropped,
            sources["consumer"].malformed_messages,
            sources["dlq"].dead_lettered,
            sources["alerts"].alerts_published,
            sources["reloader"].current_version,
        )


async def run() -> None:
    settings = Settings()
    stop = asyncio.Event()
    _install_signal_handlers(stop)

    aggregator = TumblingWindowAggregator(
        window_ms=settings.window_ms,
        allowed_lateness_ms=settings.allowed_lateness_ms,
    )
    detector = EwmaAnomalyDetector(
        alpha=settings.anomaly_alpha,
        z_threshold=settings.anomaly_z_threshold,
        warmup=settings.anomaly_warmup,
    )
    redis_sink = RedisMetricSink(
        settings.redis_url,
        channel=settings.metrics_channel,
        key_prefix=settings.metrics_key_prefix,
        ttl_seconds=settings.metrics_ttl_seconds,
        history_retention_minutes=settings.history_retention_minutes,
    )
    reloader = ModelReloader(
        settings.redis_url,
        detector,
        key=settings.model_key,
        poll_seconds=settings.model_poll_seconds,
    )

    from aiokafka import AIOKafkaProducer

    producer = AIOKafkaProducer(bootstrap_servers=settings.kafka_bootstrap_servers)
    await producer.start()
    alerts = AlertPublisher(
        topic=settings.kafka_alerts_topic,
        cooldown_seconds=settings.alert_cooldown_seconds,
        producer=producer,
    )
    dlq = DeadLetterPublisher(
        producer, settings.kafka_dlq_topic, source_topic=settings.kafka_events_topic
    )
    consumer = EventConsumer(settings, dead_letter=dlq.publish)

    dev_sink = None
    if settings.dev_snowflake_sink_enabled:
        from processor.sinks.snowflake_sink import SnowflakeDevSink

        dev_sink = SnowflakeDevSink()

    background = [
        asyncio.create_task(reloader.run(stop)),
        asyncio.create_task(
            _stats_reporter(
                stop,
                settings.stats_interval_seconds,
                {
                    "consumer": consumer,
                    "aggregator": aggregator,
                    "dlq": dlq,
                    "alerts": alerts,
                    "reloader": reloader,
                },
            )
        ),
    ]

    log.info(
        "processor up: window=%dms lateness=%dms z>=%.1f cooldown=%ds",
        settings.window_ms,
        settings.allowed_lateness_ms,
        settings.anomaly_z_threshold,
        settings.alert_cooldown_seconds,
    )

    async def sink_points(points) -> None:
        for point in points:
            await redis_sink.write(point)
            result = detector.score(point.metric, point.value)
            if result.is_anomaly:
                await alerts.publish(result)

    try:
        async for batch in consumer.stream_batches(stop):
            closed: list = []
            for event in batch:
                closed.extend(aggregator.add(event))
                if dev_sink is not None:
                    await dev_sink.add(event)
            await sink_points(closed)
    finally:
        log.info("draining: flushing %s", "open windows")
        await sink_points(aggregator.flush())
        if dev_sink is not None:
            await dev_sink.flush()
        for task in background:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task
        await producer.stop()
        await redis_sink.close()
        log.info("processor shut down cleanly")


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format='{"ts":"%(asctime)s","level":"%(levelname)s","logger":"%(name)s","msg":"%(message)s"}',
    )
    asyncio.run(run())


if __name__ == "__main__":
    main()
