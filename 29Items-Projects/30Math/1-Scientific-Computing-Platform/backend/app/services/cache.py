"""Result cache for idempotent computations.

Key = sha256 of the *canonical* SymPy form (srepr), not the raw text — so
"x^2-4=0" and "x**2 - 4 = 0" share one entry and 30 students asking the same
homework question cost one solve (docs/ARCHITECTURE.md §2.4).

Backend: Redis when reachable, else an in-process LRU (local dev, tests).
A cache failure must NEVER fail a request — every operation degrades to a miss.
"""

from __future__ import annotations

import hashlib
import json
import logging
import threading
import time
from collections import OrderedDict
from functools import lru_cache
from typing import cast

from app.core.config import get_settings

logger = logging.getLogger(__name__)

_MEMORY_CACHE_MAX_ENTRIES = 1024


class MemoryCache:
    """Thread-safe LRU with per-entry TTL. The no-Redis fallback."""

    def __init__(self, max_entries: int = _MEMORY_CACHE_MAX_ENTRIES) -> None:
        self._entries: OrderedDict[str, tuple[float, dict]] = OrderedDict()
        self._lock = threading.Lock()
        self._max_entries = max_entries

    def get(self, key: str) -> dict | None:
        with self._lock:
            entry = self._entries.get(key)
            if entry is None:
                return None
            expires_at, value = entry
            if expires_at < time.monotonic():
                del self._entries[key]
                return None
            self._entries.move_to_end(key)
            return value

    def set(self, key: str, value: dict, ttl_seconds: int) -> None:
        with self._lock:
            self._entries[key] = (time.monotonic() + ttl_seconds, value)
            self._entries.move_to_end(key)
            while len(self._entries) > self._max_entries:
                self._entries.popitem(last=False)


class RedisCache:
    def __init__(self, url: str) -> None:
        import redis

        self._client = redis.Redis.from_url(
            url, socket_timeout=0.5, socket_connect_timeout=0.5, decode_responses=True
        )
        self._client.ping()  # fail fast at construction → fallback kicks in

    def get(self, key: str) -> dict | None:
        # decode_responses=True + sync client ⇒ str | None at runtime.
        raw = cast("str | None", self._client.get(key))
        return json.loads(raw) if raw else None

    def set(self, key: str, value: dict, ttl_seconds: int) -> None:
        self._client.set(key, json.dumps(value), ex=ttl_seconds)


class SafeCache:
    """Wraps a backend so cache trouble degrades to a miss, never an error."""

    def __init__(self, backend: MemoryCache | RedisCache) -> None:
        self._backend = backend
        self.backend_name = type(backend).__name__

    def get(self, key: str) -> dict | None:
        try:
            return self._backend.get(key)
        except Exception:
            logger.warning("cache get failed; treating as miss", exc_info=True)
            return None

    def set(self, key: str, value: dict, ttl_seconds: int) -> None:
        try:
            self._backend.set(key, value, ttl_seconds)
        except Exception:
            logger.warning("cache set failed; result not cached", exc_info=True)


@lru_cache
def get_cache() -> SafeCache:
    settings = get_settings()
    try:
        backend: MemoryCache | RedisCache = RedisCache(settings.redis_url)
        logger.info("result cache: redis")
    except Exception:
        backend = MemoryCache()
        logger.info("result cache: in-process memory (redis unreachable)")
    return SafeCache(backend)


def computation_cache_key(operation: str, canonical_srepr: str, *parts: str) -> str:
    material = "\x1f".join([operation, canonical_srepr, *parts])
    return "scp:result:" + hashlib.sha256(material.encode()).hexdigest()
