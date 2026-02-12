"""
Logging Middleware

Custom middleware for request/response logging with tracing.
"""

import time
import uuid
import logging
from typing import Callable
from django.http import HttpRequest, HttpResponse

logger = logging.getLogger("backend")


class LoggingMiddleware:
    """
    Middleware for logging all requests and responses.
    """

    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]):
        self.get_response = get_response

    def __call__(self, request: HttpRequest) -> HttpResponse:
        """
        Log request and response with timing and tracing.
        """
        # Generate request ID
        request_id = request.META.get("HTTP_X_REQUEST_ID", str(uuid.uuid4()))
        request.META["HTTP_X_REQUEST_ID"] = request_id

        # Start timing
        start_time = time.time()

        # Log request
        self._log_request(request, request_id)

        # Process request
        response = self.get_response(request)

        # Calculate duration
        duration = (time.time() - start_time) * 1000  # ms

        # Add request ID to response
        response["X-Request-ID"] = request_id

        # Log response
        self._log_response(request, response, request_id, duration)

        return response

    def _log_request(self, request: HttpRequest, request_id: str) -> None:
        """Log incoming request details."""
        logger.info(
            "Incoming request",
            extra={
                "request_id": request_id,
                "method": request.method,
                "path": request.path,
                "query_params": dict(request.GET),
                "user_id": request.user.id if request.user.is_authenticated else None,
                "ip": self._get_client_ip(request),
                "user_agent": request.META.get("HTTP_USER_AGENT", ""),
            },
        )

    def _log_response(
        self,
        request: HttpRequest,
        response: HttpResponse,
        request_id: str,
        duration: float,
    ) -> None:
        """Log response details."""
        log_level = logging.INFO

        # Log error responses as WARNING
        if 400 <= response.status_code < 500:
            log_level = logging.WARNING
        # Log server errors as ERROR
        elif response.status_code >= 500:
            log_level = logging.ERROR

        logger.log(
            log_level,
            "Request completed",
            extra={
                "request_id": request_id,
                "method": request.method,
                "path": request.path,
                "status_code": response.status_code,
                "duration_ms": round(duration, 2),
                "user_id": request.user.id if request.user.is_authenticated else None,
            },
        )

    def _get_client_ip(self, request: HttpRequest) -> str:
        """Get client IP address."""
        x_forwarded_for = request.META.get("HTTP_X_FORWARDED_FOR")
        if x_forwarded_for:
            return x_forwarded_for.split(",")[0].strip()
        return request.META.get("REMOTE_ADDR", "")
