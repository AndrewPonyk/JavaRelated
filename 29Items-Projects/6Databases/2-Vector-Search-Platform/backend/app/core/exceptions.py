"""Domain exception hierarchy + FastAPI handlers.

Domain code raises these; handlers convert them into a **single, consistent JSON
error envelope** so clients only ever parse one shape:

    {"error": {"code": "...", "message": "...", "request_id": "..."}}
"""

from __future__ import annotations

import structlog
from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

log = structlog.get_logger(__name__)


class AppError(Exception):
    """Base class for expected, mapped application errors."""

    code: str = "internal_error"
    status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR

    def __init__(self, message: str | None = None) -> None:
        self.message = message or self.__doc__ or self.code
        super().__init__(self.message)


class NotFoundError(AppError):
    """Requested resource was not found."""

    code = "not_found"
    status_code = status.HTTP_404_NOT_FOUND


class AuthError(AppError):
    """Authentication or authorization failed."""

    code = "unauthorized"
    status_code = status.HTTP_401_UNAUTHORIZED


class RateLimitError(AppError):
    """Rate limit exceeded."""

    code = "rate_limited"
    status_code = status.HTTP_429_TOO_MANY_REQUESTS


class EmbeddingError(AppError):
    """Embedding generation failed."""

    code = "embedding_failed"
    status_code = status.HTTP_502_BAD_GATEWAY


class BackendUnavailableError(AppError):
    """A vector backend is unavailable or timed out."""

    code = "backend_unavailable"
    status_code = status.HTTP_503_SERVICE_UNAVAILABLE


def _envelope(request: Request, code: str, message: str, status_code: int) -> JSONResponse:
    request_id = getattr(request.state, "request_id", None)
    return JSONResponse(
        status_code=status_code,
        content={"error": {"code": code, "message": message, "request_id": request_id}},
    )


def register_exception_handlers(app: FastAPI) -> None:
    """Attach all exception handlers to the app."""

    @app.exception_handler(AppError)
    async def _handle_app_error(request: Request, exc: AppError) -> JSONResponse:
        # 5xx are unexpected enough to log at error; 4xx at info.
        logger = log.error if exc.status_code >= 500 else log.info
        logger("app_error", code=exc.code, message=exc.message)
        return _envelope(request, exc.code, exc.message, exc.status_code)

    @app.exception_handler(RequestValidationError)
    async def _handle_validation(request: Request, exc: RequestValidationError) -> JSONResponse:
        return _envelope(
            request,
            "validation_error",
            "Request payload failed validation.",
            status.HTTP_422_UNPROCESSABLE_ENTITY,
        )

    @app.exception_handler(Exception)
    async def _handle_unexpected(request: Request, exc: Exception) -> JSONResponse:
        # Never leak internals to the client; log with the request_id for triage.
        log.exception("unhandled_exception")
        return _envelope(
            request,
            "internal_error",
            "An unexpected error occurred.",
            status.HTTP_500_INTERNAL_SERVER_ERROR,
        )
