"""Production Kafka backend (aiokafka).

Same interface as :class:`InMemoryBus`, so services are written once and the
backend is chosen by configuration. Exercised in a full integration environment
with a live broker (excluded from unit-test coverage).
"""

from __future__ import annotations

import asyncio
from typing import TYPE_CHECKING

from pydantic import BaseModel

from trading_common.messaging.bus import M, MessageBus, Subscription, encode

if TYPE_CHECKING:  # avoid importing aiokafka at module import time
    from aiokafka import AIOKafkaConsumer, AIOKafkaProducer


class KafkaBus(MessageBus):
    def __init__(self, bootstrap_servers: str, group_id: str = "trading-platform") -> None:
        self._bootstrap = bootstrap_servers
        self._group = group_id
        self._producer: AIOKafkaProducer | None = None
        self._consumers: list[AIOKafkaConsumer] = []
        self._tasks: list[asyncio.Task] = []

    async def start(self) -> None:
        from aiokafka import AIOKafkaProducer

        self._producer = AIOKafkaProducer(bootstrap_servers=self._bootstrap)
        await self._producer.start()

    async def stop(self) -> None:
        for task in self._tasks:
            task.cancel()
        for consumer in self._consumers:
            await consumer.stop()
        if self._producer is not None:
            await self._producer.stop()

    async def publish(self, topic: str, value: BaseModel, key: str | None = None) -> None:
        assert self._producer is not None, "bus not started"
        await self._producer.send_and_wait(
            topic, value=encode(value), key=key.encode() if key else None
        )

    def subscribe(
        self, topic: str, model_type: type[M], group: str | None = None
    ) -> Subscription[M]:
        from aiokafka import AIOKafkaConsumer

        sub: Subscription[M] = Subscription(topic, model_type)
        consumer = AIOKafkaConsumer(
            topic,
            bootstrap_servers=self._bootstrap,
            group_id=group or self._group,
            enable_auto_commit=True,
            auto_offset_reset="latest",
        )
        self._consumers.append(consumer)

        async def pump() -> None:
            await consumer.start()
            try:
                async for msg in consumer:
                    sub._offer(msg.value)
            except asyncio.CancelledError:  # graceful shutdown
                pass

        self._tasks.append(asyncio.ensure_future(pump()))
        return sub
