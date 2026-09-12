"""Attach a correlation ID to every request and expose it for logging.

See ARCHITECTURE.md §2.6 — the same request_id flows from ingress, into logs,
into Celery tasks, and back to the client in the error envelope.
"""

from __future__ import annotations

import logging
import uuid
from collections.abc import Callable

from django.http import HttpRequest, HttpResponse

logger = logging.getLogger("request")

REQUEST_ID_HEADER = "X-Request-ID"


class RequestIDMiddleware:
    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]) -> None:
        self.get_response = get_response

    def __call__(self, request: HttpRequest) -> HttpResponse:
        request_id = request.headers.get(REQUEST_ID_HEADER) or uuid.uuid4().hex
        request.request_id = request_id  # type: ignore[attr-defined]

        response = self.get_response(request)
        response[REQUEST_ID_HEADER] = request_id

        logger.info(
            "request.completed",
            extra={
                "request_id": request_id,
                "method": request.method,
                "path": request.path,
                "status_code": response.status_code,
            },
        )
        return response
