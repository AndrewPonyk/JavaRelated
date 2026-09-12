"""Lightweight in-memory rate-limit middleware (sliding window, per client IP).

Suitable for a single instance / local dev. In multi-replica production this
should be backed by Redis (e.g. a sorted-set sliding window) so the limit is
global rather than per-task; the middleware interface stays the same.
"""

from __future__ import annotations

import time
from collections import defaultdict, deque

from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

from app.core.logging import request_id_ctx

_EXEMPT_PATHS = {"/healthz", "/readyz"}


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, limit_per_minute: int = 600) -> None:
        super().__init__(app)
        self.limit = limit_per_minute
        self.window = 60.0
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    async def dispatch(self, request: Request, call_next):
        if request.url.path in _EXEMPT_PATHS:
            return await call_next(request)

        client = request.client.host if request.client else "anonymous"
        now = time.monotonic()
        bucket = self._hits[client]
        while bucket and now - bucket[0] > self.window:
            bucket.popleft()

        if len(bucket) >= self.limit:
            retry_after = int(self.window - (now - bucket[0])) + 1
            return JSONResponse(
                status_code=429,
                headers={"Retry-After": str(retry_after)},
                content={
                    "error": {
                        "code": "rate_limited",
                        "message": "Too many requests",
                        "request_id": request_id_ctx.get(),
                    }
                },
            )

        bucket.append(now)
        return await call_next(request)
