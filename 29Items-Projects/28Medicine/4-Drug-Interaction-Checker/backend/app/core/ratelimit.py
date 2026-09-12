"""Simple in-memory sliding-window rate limiter (per client IP).

Per-pod only. For multi-pod fairness use a shared store (Redis) — see
TECH-NOTES.md (Phase 3). Health/metrics paths are exempt.
"""

from __future__ import annotations

import time
from collections import defaultdict, deque

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.types import ASGIApp

from app.core.config import get_settings

_WINDOW_SECONDS = 60.0
_EXEMPT_PREFIXES = ("/api/v1/health", "/health", "/metrics", "/docs", "/openapi.json")


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(self, app: ASGIApp, limit_per_minute: int | None = None) -> None:
        super().__init__(app)
        self._override = limit_per_minute
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    def _limit(self) -> int:
        if self._override is not None:
            return self._override
        return get_settings().rate_limit_per_minute

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        limit = self._limit()
        if limit <= 0 or request.url.path.startswith(_EXEMPT_PREFIXES):
            return await call_next(request)

        client = request.client.host if request.client else "anonymous"
        now = time.monotonic()
        hits = self._hits[client]
        while hits and now - hits[0] > _WINDOW_SECONDS:
            hits.popleft()

        if len(hits) >= limit:
            retry_after = int(_WINDOW_SECONDS - (now - hits[0])) + 1
            return JSONResponse(
                status_code=429,
                headers={"Retry-After": str(retry_after)},
                content={"error": {"code": "rate_limited", "message": "Too many requests"}},
            )

        hits.append(now)
        return await call_next(request)
