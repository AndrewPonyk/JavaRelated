"""Hermetic async Redis fake covering the commands the API services use."""

from __future__ import annotations


class FakeRedis:
    def __init__(self) -> None:
        self.hashes: dict[str, dict[str, str]] = {}
        self.zsets: dict[str, dict[str, float]] = {}
        self.sets: dict[str, set[str]] = {}
        self.published: list[tuple[str, str]] = []

    # ── hashes ──────────────────────────────────────────────────────────────
    async def hset(self, name, key=None, value=None, mapping=None):
        target = self.hashes.setdefault(name, {})
        if mapping:
            target.update({k: str(v) for k, v in mapping.items()})
        if key is not None:
            target[key] = str(value)
        return 1

    async def hsetnx(self, name, key, value):
        target = self.hashes.setdefault(name, {})
        if key in target:
            return 0
        target[key] = str(value)
        return 1

    async def hget(self, name, key):
        return self.hashes.get(name, {}).get(key)

    async def hgetall(self, name):
        return dict(self.hashes.get(name, {}))

    async def hvals(self, name):
        return list(self.hashes.get(name, {}).values())

    async def hmget(self, name, keys):
        target = self.hashes.get(name, {})
        return [target.get(k) for k in keys]

    async def hexists(self, name, key):
        return key in self.hashes.get(name, {})

    async def hdel(self, name, *keys):
        target = self.hashes.get(name, {})
        removed = 0
        for key in keys:
            if key in target:
                del target[key]
                removed += 1
        return removed

    # ── sorted sets ─────────────────────────────────────────────────────────
    async def zadd(self, name, mapping):
        self.zsets.setdefault(name, {}).update({m: float(s) for m, s in mapping.items()})
        return len(mapping)

    def _sorted(self, name, reverse=False):
        return sorted(
            self.zsets.get(name, {}).items(), key=lambda kv: (kv[1], kv[0]), reverse=reverse
        )

    @staticmethod
    def _slice(items, start, end):
        n = len(items)
        if end < 0:
            end = n + end
        if start < 0:
            start = n + start
        if end < start:
            return []
        return items[start : end + 1]

    async def zrange(self, name, start, end):
        return [m for m, _ in self._slice(self._sorted(name), start, end)]

    async def zrevrange(self, name, start, end):
        return [m for m, _ in self._slice(self._sorted(name, reverse=True), start, end)]

    async def zrangebyscore(self, name, min_score, max_score, start=None, num=None):
        low = float("-inf") if min_score in ("-inf",) else float(min_score)
        high = float("inf") if max_score in ("+inf", "inf") else float(max_score)
        matches = [m for m, s in self._sorted(name) if low <= s <= high]
        if start is not None and num is not None:
            matches = matches[start : start + num]
        return matches

    async def zremrangebyscore(self, name, min_score, max_score):
        low = float("-inf") if min_score in ("-inf",) else float(min_score)
        high = float("inf") if max_score in ("+inf", "inf") else float(max_score)
        zset = self.zsets.get(name, {})
        doomed = [m for m, s in zset.items() if low <= s <= high]
        for member in doomed:
            del zset[member]
        return len(doomed)

    async def zrem(self, name, *members):
        zset = self.zsets.get(name, {})
        removed = 0
        for member in members:
            if member in zset:
                del zset[member]
                removed += 1
        return removed

    # ── sets ────────────────────────────────────────────────────────────────
    async def sadd(self, name, *members):
        target = self.sets.setdefault(name, set())
        before = len(target)
        target.update(members)
        return len(target) - before

    async def smembers(self, name):
        return set(self.sets.get(name, set()))

    async def srem(self, name, *members):
        target = self.sets.get(name, set())
        removed = len(target & set(members))
        target.difference_update(members)
        return removed

    # ── misc ────────────────────────────────────────────────────────────────
    async def expire(self, name, seconds):
        return 1

    async def publish(self, channel, message):
        self.published.append((channel, message))
        return 1

    async def aclose(self):
        return None

    def pubsub(self):
        return FakePubSub(self)

    def pipeline(self, transaction=False):
        return FakePipeline(self)


class FakePubSub:
    """Replays messages published to the fake before listen() was called."""

    def __init__(self, parent: FakeRedis) -> None:
        self._parent = parent
        self._channels: list[str] = []

    async def subscribe(self, *channels):
        self._channels.extend(channels)

    async def unsubscribe(self, *channels):
        return None

    async def aclose(self):
        return None

    async def listen(self):
        for channel in self._channels:
            yield {"type": "subscribe", "channel": channel, "data": 1}
        for channel, message in list(self._parent.published):
            if channel in self._channels:
                yield {"type": "message", "channel": channel, "data": message}


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
