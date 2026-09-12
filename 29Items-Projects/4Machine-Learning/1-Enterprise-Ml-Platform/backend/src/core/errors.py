"""Domain error hierarchy and centralized FastAPI exception handlers.

Services raise framework-agnostic exceptions; a single registrar maps them to
HTTP status codes and a consistent RFC 7807 ``problem+json`` body. Routers and
services therefore never import ``HTTPException`` or hand-craft responses.
"""
from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from src.core.logging import correlation_id

logger = logging.getLogger(__name__)


class PlatformError(Exception):
    """Base class for all expected, mapped domain errors."""

    status_code: int = 500
    title: str = "Internal Server Error"


class BadRequestError(PlatformError):
    status_code = 400
    title = "Bad Request"


class AuthenticationError(PlatformError):
    """Caller is unauthenticated (missing/invalid credentials) -> 401."""

    status_code = 401
    title = "Unauthorized"


class AuthorizationError(PlatformError):
    """Caller is authenticated but lacks the required scope -> 403."""

    status_code = 403
    title = "Forbidden"


class NotFoundError(PlatformError):
    status_code = 404
    title = "Not Found"


class ConflictError(PlatformError):
    status_code = 409
    title = "Conflict"


class UnprocessableError(PlatformError):
    """Semantically invalid request the schema can't express -> 422."""

    status_code = 422
    title = "Unprocessable Entity"


def _problem(status: int, title: str, detail: str) -> JSONResponse:
    """Build an RFC 7807 problem+json response with the request correlation id."""
    return JSONResponse(
        status_code=status,
        content={
            "type": "about:blank",
            "title": title,
            "status": status,
            "detail": detail,
            "correlation_id": correlation_id.get(),
        },
    )


def register_exception_handlers(app: FastAPI) -> None:
    """Install handlers for domain errors, validation errors, and a 500 catch-all."""

    @app.exception_handler(PlatformError)
    async def _platform(_: Request, exc: PlatformError) -> JSONResponse:
        # Expected errors: log at INFO/WARNING, never as a 500 stack trace.
        if exc.status_code >= 500:
            logger.error("platform error: %s", exc)
        else:
            logger.info("handled %s: %s", exc.__class__.__name__, exc)
        return _problem(exc.status_code, exc.title, str(exc) or exc.title)

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError) -> JSONResponse:
        return _problem(422, "Unprocessable Entity", _summarize_validation(exc))

    @app.exception_handler(Exception)
    async def _unhandled(_: Request, exc: Exception) -> JSONResponse:
        # Last resort: log the full trace, return an opaque 500 (no internals leak).
        logger.exception("unhandled error: %s", exc)
        return _problem(500, "Internal Server Error", "An unexpected error occurred.")


def _summarize_validation(exc: RequestValidationError) -> str:
    """Render Pydantic validation failures into a compact, safe message."""
    parts = []
    for err in exc.errors():
        loc = ".".join(str(p) for p in err.get("loc", ()) if p != "body")
        parts.append(f"{loc or 'body'}: {err.get('msg', 'invalid')}")
    return "; ".join(parts) or "Request validation failed."
