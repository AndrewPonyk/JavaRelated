"""Request rate limiting (fixed one-minute windows).

Protects the expensive resources: the solver endpoints against classroom
bursts and ``/auth/login`` against credential stuffing. Identity is the JWT
subject when a bearer token is present, else the client IP (first
``X-Forwarded-For`` hop behind the ALB — set by infrastructure we trust,
docs/ARCHITECTURE.md §2.5).

Backend mirrors the result cache: Redis (``INCR`` + ``EXPIRE``, atomic and
shared across API replicas) with an in-process fallback for local dev/tests.
A limiter failure must never take down requests — errors degrade to "allow".

Fixed windows admit up to 2× the nominal rate at a window boundary; that is an
accepted, documented tradeoff for classroom-scale traffic.
"""

from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass
from functools import lru_cache

from app.core.config import get_settings

logger = logging.getLogger(__name__)

WINDOW_SECONDS = 60


@dataclass(frozen=True)
class RateDecision:
    allowed: bool
    retry_after_seconds: int


class MemoryRateLimiter:
    """Per-process fixed-window counters (local dev / tests)."""

    def __init__(self) -> None:
        self._windows: dict[str, tuple[int, int]] = {}  # key -> (window_id, count)
        self._lock = threading.Lock()

    def hit(self, key: str, limit: int) -> RateDecision:
        now = time.time()
        window_id = int(now // WINDOW_SECONDS)
        with self._lock:
            current_window, count = self._windows.get(key, (window_id, 0))
            if current_window != window_id:
                count = 0
            count += 1
            self._windows[key] = (window_id, count)
            if len(self._windows) > 10_000:  # bound memory under key churn
                self._windows = {k: v for k, v in self._windows.items() if v[0] == window_id}
        if count <= limit:
            return RateDecision(True, 0)
        return RateDecision(False, WINDOW_SECONDS - int(now % WINDOW_SECONDS) or 1)


class RedisRateLimiter:
    """Shared fixed-window counters across API replicas."""

    def __init__(self, url: str) -> None:
        import redis

        self._client = redis.Redis.from_url(url, socket_timeout=0.5, socket_connect_timeout=0.5)
        self._client.ping()  # fail fast → fallback at construction time

    def hit(self, key: str, limit: int) -> RateDecision:
        window_id = int(time.time() // WINDOW_SECONDS)
        redis_key = f"scp:rl:{key}:{window_id}"
        pipe = self._client.pipeline()
        pipe.incr(redis_key)
        pipe.expire(redis_key, WINDOW_SECONDS + 5)
        count = int(pipe.execute()[0])
        if count <= limit:
            return RateDecision(True, 0)
        return RateDecision(False, WINDOW_SECONDS - int(time.time() % WINDOW_SECONDS) or 1)


class SafeRateLimiter:
    """Failure-tolerant wrapper: limiter trouble always degrades to allow."""

    def __init__(self, backend: MemoryRateLimiter | RedisRateLimiter) -> None:
        self._backend = backend
        self.backend_name = type(backend).__name__

    def hit(self, key: str, limit: int) -> RateDecision:
        try:
            return self._backend.hit(key, limit)
        except Exception:
            logger.warning("rate limiter failed; allowing request", exc_info=True)
            return RateDecision(True, 0)


@lru_cache
def get_rate_limiter() -> SafeRateLimiter:
    settings = get_settings()
    try:
        backend: MemoryRateLimiter | RedisRateLimiter = RedisRateLimiter(settings.redis_url)
        logger.info("rate limiter: redis")
    except Exception:
        backend = MemoryRateLimiter()
        logger.info("rate limiter: in-process memory (redis unreachable)")
    return SafeRateLimiter(backend)
