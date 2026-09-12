"""Typed domain exceptions + FastAPI handlers.

Services raise these framework-agnostic errors; a single handler layer maps them
to RFC-7807-style problem responses. Internal details never leak to clients.
"""

from __future__ import annotations

from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse

from app.core.logging import get_logger


class DomainError(Exception):
    """Base for all expected, mapped application errors."""

    status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR
    code: str = "internal_error"

    def __init__(self, detail: str | None = None) -> None:
        self.detail = detail or self.__class__.__doc__ or "Unexpected error"
        super().__init__(self.detail)


class NotFoundError(DomainError):
    """Requested resource does not exist."""

    status_code = status.HTTP_404_NOT_FOUND
    code = "not_found"


class StudyNotFoundError(NotFoundError):
    """No study matches the given StudyInstanceUID."""

    code = "study_not_found"


class InvalidDicomError(DomainError):
    """Uploaded payload is not a valid/parseable DICOM object."""

    status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
    code = "invalid_dicom"


class UnauthorizedError(DomainError):
    """Authentication is missing or invalid."""

    status_code = status.HTTP_401_UNAUTHORIZED
    code = "unauthorized"


class ForbiddenError(DomainError):
    """Authenticated, but the role lacks the required scope."""

    status_code = status.HTTP_403_FORBIDDEN
    code = "forbidden"


class ConflictError(DomainError):
    """Request conflicts with current state (e.g. a duplicate resource)."""

    status_code = status.HTTP_409_CONFLICT
    code = "conflict"


class StorageError(DomainError):
    """Object-store operation failed."""

    status_code = status.HTTP_502_BAD_GATEWAY
    code = "storage_error"


def _problem(exc: DomainError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"type": exc.code, "title": exc.code, "detail": exc.detail},
    )


def register_exception_handlers(app: FastAPI) -> None:
    """Wire domain errors into FastAPI; called from the app factory."""

    @app.exception_handler(DomainError)
    async def _handle_domain(_: Request, exc: DomainError) -> JSONResponse:
        return _problem(exc)

    @app.exception_handler(Exception)
    async def _handle_unexpected(_: Request, exc: Exception) -> JSONResponse:
        # Log full detail server-side (correlation_id comes from log context);
        # return a generic message so internals never leak to the client.
        get_logger(__name__).error(
            "unhandled_exception", error=str(exc), error_type=type(exc).__name__, exc_info=True
        )
        return _problem(DomainError("Internal server error"))
