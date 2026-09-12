"""Hermetic fakes for streaming unit tests (no Redis/Kafka servers)."""

from __future__ import annotations


class FakeRedis:
    """Implements the async redis commands the processor uses."""

    def __init__(self) -> None:
        self.hashes: dict[str, dict[str, str]] = {}
        self.zsets: dict[str, dict[str, float]] = {}
        self.published: list[tuple[str, str]] = []
        self.expirations: dict[str, int] = {}

    # -- commands ---------------------------------------------------------
    async def hset(self, name, key=None, value=None, mapping=None):
        target = self.hashes.setdefault(name, {})
        if mapping:
            target.update({k: str(v) for k, v in mapping.items()})
        if key is not None:
            target[key] = str(value)
        return 1

    async def hgetall(self, name):
        return dict(self.hashes.get(name, {}))

    async def expire(self, name, seconds):
        self.expirations[name] = int(seconds)
        return 1

    async def zadd(self, name, mapping):
        self.zsets.setdefault(name, {}).update({m: float(s) for m, s in mapping.items()})
        return len(mapping)

    async def zremrangebyscore(self, name, min_score, max_score):
        zset = self.zsets.get(name, {})
        low = float("-inf") if min_score in ("-inf", "-INF") else float(min_score)
        high = float("inf") if max_score in ("+inf", "inf") else float(max_score)
        doomed = [m for m, s in zset.items() if low <= s <= high]
        for member in doomed:
            del zset[member]
        return len(doomed)

    async def publish(self, channel, message):
        self.published.append((channel, message))
        return 1

    async def aclose(self):
        return None

    # -- pipeline ----------------------------------------------------------
    def pipeline(self, transaction=False):
        return FakePipeline(self)


class FakePipeline:
    def __init__(self, parent: FakeRedis) -> None:
        self._parent = parent
        self._ops: list[tuple[str, tuple, dict]] = []

    def __getattr__(self, command):
        def queue(*args, **kwargs):
            self._ops.append((command, args, kwargs))
            return self

        return queue

    async def execute(self):
        results = []
        for command, args, kwargs in self._ops:
            results.append(await getattr(self._parent, command)(*args, **kwargs))
        self._ops.clear()
        return results


class FakeKafkaProducer:
    """Records send_and_wait calls; raises when told to (DLQ failure paths)."""

    def __init__(self, fail: bool = False) -> None:
        self.sent: list[tuple[str, bytes]] = []
        self.fail = fail
        self.started = False

    async def start(self):
        self.started = True

    async def stop(self):
        self.started = False

    async def send_and_wait(self, topic, value):
        if self.fail:
            raise ConnectionError("broker unavailable (fake)")
        self.sent.append((topic, value))
