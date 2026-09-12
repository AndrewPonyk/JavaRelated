"""Request-context middleware: correlation IDs + structured access logs."""

from __future__ import annotations

import time
import uuid

import structlog
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

from app.core.config import get_settings
from app.core.logging import get_logger

logger = get_logger("http")


class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        header = get_settings().request_id_header
        request_id = request.headers.get(header) or uuid.uuid4().hex

        structlog.contextvars.clear_contextvars()
        structlog.contextvars.bind_contextvars(
            request_id=request_id, method=request.method, path=request.url.path
        )

        start = time.perf_counter()
        response = await call_next(request)
        duration_ms = round((time.perf_counter() - start) * 1000, 2)

        response.headers[header] = request_id
        logger.info("request_completed", status_code=response.status_code, duration_ms=duration_ms)
        structlog.contextvars.clear_contextvars()
        return response
