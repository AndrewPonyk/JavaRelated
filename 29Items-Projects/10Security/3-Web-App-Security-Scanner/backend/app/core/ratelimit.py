"""In-memory token-bucket rate limiting for sensitive endpoints.

Single-process by design: compose/prod runs one API worker (pinned), and
the limiter guards abuse, not distributed floods — swap for Redis when
workers scale (documented in ARCHITECTURE 2.4).
"""

from __future__ import annotations

import threading
import time

from fastapi import HTTPException, Request, status

from app.core.config import settings
from app.core.metrics import metrics


class TokenBucket:
    def __init__(self, capacity: int, refill_per_minute: float) -> None:
        self.capacity = capacity
        self.tokens = float(capacity)
        self.refill_rate = refill_per_minute / 60.0  # tokens per second
        self.updated = time.monotonic()

    def try_consume(self, amount: float = 1.0) -> bool:
        now = time.monotonic()
        self.tokens = min(self.capacity, self.tokens + (now - self.updated) * self.refill_rate)
        self.updated = now
        if self.tokens >= amount:
            self.tokens -= amount
            return True
        return False


class RateLimiter:
    """Bucket pool keyed by (bucket_name, client_id)."""

    def __init__(self) -> None:
        self._buckets: dict[tuple[str, str], TokenBucket] = {}
        self._lock = threading.Lock()

    def check(self, bucket: str, client_id: str) -> None:
        limit = settings.RATE_LIMITS.get(bucket)
        if limit is None:
            return
        capacity, refill = limit
        with self._lock:
            bucket_key = (bucket, client_id)
            if bucket_key not in self._buckets:
                self._buckets[bucket_key] = TokenBucket(capacity, refill)
            allowed = self._buckets[bucket_key].try_consume()
        if not allowed:
            metrics.count("ratelimit_rejected_total", labels={"bucket": bucket})
            raise HTTPException(
                status.HTTP_429_TOO_MANY_REQUESTS,
                "Rate limit exceeded — retry later",
                headers={"Retry-After": "60"},
            )


limiter = RateLimiter()


def _client_id(request: Request) -> str:
    """Prefer the authenticated principal; fall back to client IP."""
    principal = getattr(request.state, "principal", None)
    return (
        principal.id
        if principal is not None
        else (request.client.host if request.client else "unknown")
    )


async def auth_rate_limit(request: Request) -> None:
    limiter.check("auth", _client_id(request))


async def scan_rate_limit(request: Request) -> None:
    limiter.check("scan_launch", _client_id(request))
