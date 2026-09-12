"""HTTP request instrumentation middleware."""

from __future__ import annotations

import time
from collections.abc import Awaitable, Callable

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from fraud_detection.monitoring.metrics import (
    HTTP_REQUEST_DURATION_SECONDS,
    HTTP_REQUESTS_TOTAL,
)


class MetricsMiddleware(BaseHTTPMiddleware):
    """Times every request and updates the canonical HTTP metrics.

    Uses the matched route's path template (e.g. ``/api/v1/predictions``)
    instead of the raw URL path to keep label cardinality bounded.
    """

    async def dispatch(
        self, request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        start = time.perf_counter()
        status = "500"
        try:
            response = await call_next(request)
            status = str(response.status_code)
            return response
        finally:
            duration = time.perf_counter() - start
            path = self._path_template(request)
            HTTP_REQUESTS_TOTAL.labels(method=request.method, path=path, status=status).inc()
            HTTP_REQUEST_DURATION_SECONDS.labels(method=request.method, path=path).observe(duration)

    @staticmethod
    def _path_template(request: Request) -> str:
        """Return the matched route template, or a bounded fallback."""
        route = request.scope.get("route")
        template = getattr(route, "path_format", None) or getattr(route, "path", None)
        return str(template) if template else "unmatched"
