"""FastAPI dependencies: authentication (API key + scopes), rate limiting,
DB sessions.

Auth is a single seam — swapping API keys for OIDC/JWT later touches only
this file (see ARCHITECTURE.md §2.5). Rate limiting is an in-process
sliding window per caller: correct per replica, which is the deliberate
scope for a stateless service (a shared store would be the next step if
per-fleet limits are ever needed).
"""

from __future__ import annotations

import hashlib
import threading
import time
from collections import deque
from typing import Annotated

from fastapi import Depends, HTTPException, Request, Security, status
from fastapi.security import APIKeyHeader
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import ALL_SCOPES, Settings, get_settings
from app.db.session import get_session

api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


class SlidingWindowRateLimiter:
    """Thread-safe (handlers run in the threadpool as well as the event loop)."""

    def __init__(self) -> None:
        self._events: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def check(self, caller: str, max_calls: int, window_seconds: float) -> float | None:
        """Record one call; returns None if allowed, else seconds until retry."""
        now = time.monotonic()
        with self._lock:
            events = self._events.setdefault(caller, deque())
            while events and events[0] <= now - window_seconds:
                events.popleft()
            if len(events) >= max_calls:
                return events[0] + window_seconds - now
            events.append(now)
            return None

    def reset(self) -> None:
        with self._lock:
            self._events.clear()


rate_limiter = SlidingWindowRateLimiter()


def _authenticate(api_key: str | None, settings: Settings) -> tuple[str, frozenset[str]]:
    """Returns (caller id, granted scopes). Caller id is the key's hash —
    stable, loggable, and never the secret itself."""
    key_table = settings.api_keys()
    if not key_table:
        if settings.env == "dev":
            return "dev-anonymous", ALL_SCOPES  # frictionless local dev
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail="No API keys configured")
    if api_key is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Missing X-API-Key header")
    digest = hashlib.sha256(api_key.encode()).hexdigest()
    scopes = key_table.get(digest)
    if scopes is None:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Invalid API key")
    return digest, scopes


def require_scope(scope: str):
    """Dependency factory: authenticate, check the scope, apply the rate limit."""
    assert scope in ALL_SCOPES, f"unknown scope {scope!r}"

    async def dependency(
        request: Request,
        api_key: Annotated[str | None, Security(api_key_header)],
        settings: Annotated[Settings, Depends(get_settings)],
    ) -> str:
        caller, scopes = _authenticate(api_key, settings)
        if scope not in scopes:
            raise HTTPException(
                status.HTTP_403_FORBIDDEN, detail=f"API key lacks the {scope!r} scope"
            )
        max_calls, window = settings.rate_limit_parsed()
        retry_after = rate_limiter.check(caller, max_calls, window)
        if retry_after is not None:
            raise HTTPException(
                status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Rate limit exceeded ({settings.rate_limit})",
                headers={"Retry-After": str(max(1, int(retry_after + 0.5)))},
            )
        request.state.caller = caller
        return caller

    return dependency


PriceCaller = Annotated[str, Depends(require_scope("price"))]
FitCaller = Annotated[str, Depends(require_scope("fit"))]
AdminCaller = Annotated[str, Depends(require_scope("admin"))]
Db = Annotated[AsyncSession, Depends(get_session)]
SettingsDep = Annotated[Settings, Depends(get_settings)]
