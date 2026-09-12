"""HTTP middleware: request IDs, access logs, HTTP metrics."""

from __future__ import annotations

import logging
import time
import uuid

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from app.core.metrics import metrics

logger = logging.getLogger("http")


class RequestContextMiddleware(BaseHTTPMiddleware):
    """Assigns request_id, records HTTP metrics, emits one structured access log."""

    async def dispatch(self, request: Request, call_next) -> Response:
        request_id = request.headers.get("X-Request-ID") or uuid.uuid4().hex[:12]
        request.state.request_id = request_id
        start = time.perf_counter()

        response: Response | None = None
        try:
            response = await call_next(request)
        finally:
            elapsed = time.perf_counter() - start
            metrics.count("http_requests_total", labels={"method": request.method})
            metrics.observe(
                "http_request_seconds",
                elapsed,
                labels={
                    "method": request.method,
                    "status": str(response.status_code if response else 500),
                },
            )
        if response is not None:
            response.headers["X-Request-ID"] = request_id
        # Structured access log — credentials never logged (only method/path/status)
        logger.info(
            "access",
            extra={
                "request_id": request_id,
                "method": request.method,
                "path": request.url.path,
                "status": response.status_code,
                "duration_ms": round(elapsed * 1000, 1),
            },
        )
        return response
