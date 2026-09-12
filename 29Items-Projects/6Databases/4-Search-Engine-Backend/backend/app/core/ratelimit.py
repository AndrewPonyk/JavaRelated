"""In-process sliding-window rate limiter for the public read endpoints.

This is a per-instance backstop against accidental client loops and cheap abuse;
the authoritative limiter in cloud environments is the edge (Cloud Armor). Single
event loop -> no locking needed (no awaits inside the critical section).
"""

import time
from collections import deque


class SlidingWindowLimiter:
    def __init__(self, max_requests: int, window_s: float, max_clients: int = 10_000) -> None:
        self._max_requests = max_requests
        self._window_s = window_s
        self._max_clients = max_clients
        self._hits: dict[str, deque[float]] = {}

    def allow(self, client_id: str, now: float | None = None) -> bool:
        now = time.monotonic() if now is None else now
        window = self._hits.get(client_id)
        if window is None:
            self._evict_if_full()
            window = deque()
            self._hits[client_id] = window

        cutoff = now - self._window_s
        while window and window[0] <= cutoff:
            window.popleft()

        if len(window) >= self._max_requests:
            return False
        window.append(now)
        return True

    def _evict_if_full(self) -> None:
        # Bounded memory: drop the oldest half of tracked clients under pressure.
        if len(self._hits) >= self._max_clients:
            for key in list(self._hits)[: self._max_clients // 2]:
                del self._hits[key]


def client_ip(headers: dict[str, str], fallback: str) -> str:
    """First X-Forwarded-For hop (set by the Cloud Run/LB edge) or the socket peer."""
    forwarded = headers.get("x-forwarded-for", "")
    return forwarded.split(",")[0].strip() if forwarded else fallback
