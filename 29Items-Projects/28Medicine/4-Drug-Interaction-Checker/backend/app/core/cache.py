"""A tiny in-process TTL cache.

Used to memoize slow/external RxNorm lookups. This is intentionally simple
(per-pod, no eviction beyond size + TTL). Swap for Redis in Phase 2 if a shared
cache is required — see TECH-NOTES.md.
"""

from __future__ import annotations

import time
from typing import Generic, TypeVar

T = TypeVar("T")


class TTLCache(Generic[T]):
    def __init__(self, ttl_seconds: float, maxsize: int = 1024) -> None:
        self._ttl = ttl_seconds
        self._maxsize = maxsize
        self._store: dict[str, tuple[float, T]] = {}

    def get(self, key: str) -> T | None:
        item = self._store.get(key)
        if item is None:
            return None
        expires_at, value = item
        if time.monotonic() >= expires_at:
            self._store.pop(key, None)
            return None
        return value

    def set(self, key: str, value: T) -> None:
        if len(self._store) >= self._maxsize:
            # Evict the soonest-to-expire entry (cheap approximation of LRU/LFU).
            oldest = min(self._store, key=lambda k: self._store[k][0])
            self._store.pop(oldest, None)
        self._store[key] = (time.monotonic() + self._ttl, value)

    def clear(self) -> None:
        self._store.clear()

    def __len__(self) -> int:
        return len(self._store)
