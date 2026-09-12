"""Message bus abstraction + in-process implementation.

The whole control plane talks through this interface, so it can run either:
  * single-process over :class:`InMemoryBus` (tests, local paper trading), or
  * distributed over :class:`~trading_common.messaging.kafka_bus.KafkaBus` (prod).

Messages are pydantic models. They are JSON-serialized on publish and validated
back on consume — even in-memory — so the round-trip mirrors Kafka exactly and
catches serialization bugs (Decimal/UUID/datetime) in unit tests.
"""

from __future__ import annotations

import abc
import asyncio
from collections.abc import AsyncIterator
from typing import Generic, TypeVar

from pydantic import BaseModel

M = TypeVar("M", bound=BaseModel)


def encode(model: BaseModel) -> bytes:
    return model.model_dump_json().encode("utf-8")


def decode(raw: bytes, model_type: type[M]) -> M:
    return model_type.model_validate_json(raw.decode("utf-8"))


class Subscription(Generic[M]):
    """Async-iterator stream of decoded messages from one topic.

    Implemented as a direct iterator (``__anext__``) rather than a generator so
    there is no suspended generator to ``aclose()`` on shutdown.
    """

    def __init__(self, topic: str, model_type: type[M]) -> None:
        self.topic = topic
        self._model_type = model_type
        self._queue: asyncio.Queue[bytes | None] = asyncio.Queue()
        self._closed = False

    def _offer(self, raw: bytes) -> None:
        if not self._closed:
            self._queue.put_nowait(raw)

    def close(self) -> None:
        self._closed = True
        self._queue.put_nowait(None)  # wake the iterator with a stop sentinel

    def __aiter__(self) -> AsyncIterator[M]:
        return self

    async def __anext__(self) -> M:
        raw = await self._queue.get()
        if raw is None:  # sentinel => end of stream
            raise StopAsyncIteration
        return decode(raw, self._model_type)


class MessageBus(abc.ABC):
    """Publish/subscribe interface."""

    @abc.abstractmethod
    async def start(self) -> None: ...

    @abc.abstractmethod
    async def stop(self) -> None: ...

    @abc.abstractmethod
    async def publish(self, topic: str, value: BaseModel, key: str | None = None) -> None: ...

    @abc.abstractmethod
    def subscribe(
        self, topic: str, model_type: type[M], group: str | None = None
    ) -> Subscription[M]: ...


class InMemoryBus(MessageBus):
    """Single-process pub/sub. Each subscription receives every message on its
    topic (fan-out across consumer groups), matching how each service in this
    platform is its own consumer group.
    """

    def __init__(self) -> None:
        self._subs: dict[str, list[Subscription]] = {}
        self._started = False

    async def start(self) -> None:
        self._started = True

    async def stop(self) -> None:
        for subs in self._subs.values():
            for sub in subs:
                sub.close()
        self._subs.clear()
        self._started = False

    async def publish(self, topic: str, value: BaseModel, key: str | None = None) -> None:
        raw = encode(value)  # serialize once; deliver immutable copies
        for sub in self._subs.get(topic, ()):
            sub._offer(raw)
        await asyncio.sleep(0)  # yield so consumers can interleave

    def subscribe(
        self, topic: str, model_type: type[M], group: str | None = None
    ) -> Subscription[M]:
        sub: Subscription[M] = Subscription(topic, model_type)
        self._subs.setdefault(topic, []).append(sub)
        return sub
