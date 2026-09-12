"""Domain exception hierarchy and the FastAPI handlers that render them.

Services raise these semantic errors; a single set of handlers maps them to a
consistent JSON envelope: ``{"error": {"code", "message", "request_id"}}``.
Internal details are logged, never leaked to clients.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse

from app.core.logging import request_id_ctx

logger = logging.getLogger("app")


class AppError(Exception):
    """Base class for all expected, handled application errors."""

    status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR
    code: str = "internal_error"

    def __init__(self, message: str = "An unexpected error occurred"):
        super().__init__(message)
        self.message = message


class NotFoundError(AppError):
    status_code = status.HTTP_404_NOT_FOUND
    code = "not_found"


class ValidationError(AppError):
    status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
    code = "validation_error"


class AuthError(AppError):
    status_code = status.HTTP_401_UNAUTHORIZED
    code = "unauthorized"


class PermissionError(AppError):  # noqa: A001 - intentional domain name
    status_code = status.HTTP_403_FORBIDDEN
    code = "forbidden"


class DomainError(AppError):
    """Quant/business-rule violation (e.g. non-PSD covariance, bad weights)."""

    status_code = status.HTTP_400_BAD_REQUEST
    code = "domain_error"


def _envelope(code: str, message: str) -> dict:
    return {"error": {"code": code, "message": message, "request_id": request_id_ctx.get()}}


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def _handle_app_error(_: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=_envelope(exc.code, exc.message))

    @app.exception_handler(Exception)
    async def _handle_unexpected(_: Request, exc: Exception) -> JSONResponse:
        # Log full detail server-side; return a generic envelope to the client.
        logger.exception("Unhandled exception: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=_envelope("internal_error", "An unexpected error occurred"),
        )
