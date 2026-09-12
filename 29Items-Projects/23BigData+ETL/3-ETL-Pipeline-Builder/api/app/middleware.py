"""Request-ID propagation + structured JSON access logs (ARCHITECTURE §2.6)."""

from __future__ import annotations

import json
import logging
import time
import uuid

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

access_log = logging.getLogger("app.access")

REQUEST_ID_HEADER = "X-Request-ID"


class RequestContextMiddleware(BaseHTTPMiddleware):
    """Attaches/propagates a request id and emits one JSON access-log line.

    WebSocket connections bypass BaseHTTPMiddleware by design (Starlette only
    routes `http` scopes through it) — WS observability comes from the metrics
    service logs instead.
    """

    async def dispatch(self, request: Request, call_next):
        request_id = request.headers.get(REQUEST_ID_HEADER) or uuid.uuid4().hex[:16]
        request.state.request_id = request_id
        started = time.perf_counter()

        try:
            response = await call_next(request)
        except Exception:
            access_log.error(
                json.dumps(
                    {
                        "request_id": request_id,
                        "method": request.method,
                        "path": request.url.path,
                        "status": 500,
                        "duration_ms": round((time.perf_counter() - started) * 1000, 2),
                    }
                )
            )
            raise

        response.headers[REQUEST_ID_HEADER] = request_id
        access_log.info(
            json.dumps(
                {
                    "request_id": request_id,
                    "method": request.method,
                    "path": request.url.path,
                    "status": response.status_code,
                    "duration_ms": round((time.perf_counter() - started) * 1000, 2),
                }
            )
        )
        return response
