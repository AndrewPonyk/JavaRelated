"""Application error hierarchy and RFC-7807 (problem+json) handlers.

Endpoints and services raise AppError subclasses; the handlers below are the only
place errors are translated to HTTP. Clients never see stack traces or ES internals.
`problem_response` is the single constructor for problem bodies (also used by the
rate-limit middleware), so the wire shape and the documented ProblemDetail schema
cannot drift apart.
"""

import structlog
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.schemas.common import ProblemDetail

logger = structlog.get_logger(__name__)

PROBLEM_CONTENT_TYPE = "application/problem+json"

# Namespace for machine-readable error types; point at hosted error docs when they exist.
ERROR_TYPE_BASE = "urn:search-backend:error"


class AppError(Exception):
    status_code: int = 500
    code: str = "internal_error"
    title: str = "Internal Server Error"

    def __init__(self, detail: str | None = None) -> None:
        self.detail = detail or self.title
        super().__init__(self.detail)


class NotFoundError(AppError):
    status_code = 404
    code = "not_found"
    title = "Resource not found"


class UnauthorizedError(AppError):
    status_code = 401
    code = "unauthorized"
    title = "Missing or invalid credentials"


class InvalidSearchQueryError(AppError):
    status_code = 422
    code = "invalid_search_query"
    title = "Invalid search query"


class SearchBackendUnavailableError(AppError):
    status_code = 503
    code = "search_unavailable"
    title = "Search is temporarily unavailable"


def problem_response(
    *,
    request_id: str,
    status: int,
    code: str,
    title: str,
    detail: str,
    retry_after: int | None = None,
) -> JSONResponse:
    headers = {"Retry-After": str(retry_after)} if retry_after is not None else None
    body = ProblemDetail(
        type=f"{ERROR_TYPE_BASE}:{code}",
        title=title,
        status=status,
        detail=detail,
        code=code,
        request_id=request_id,
    )
    return JSONResponse(
        status_code=status,
        media_type=PROBLEM_CONTENT_TYPE,
        headers=headers,
        content=body.model_dump(),
    )


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def app_error_handler(request: Request, exc: AppError) -> JSONResponse:
        log = logger.warning if exc.status_code < 500 else logger.error
        log("request_failed", code=exc.code, status=exc.status_code, detail=exc.detail)
        return problem_response(
            request_id=request.headers.get("x-request-id", "-"),
            status=exc.status_code,
            code=exc.code,
            title=exc.title,
            detail=exc.detail,
            retry_after=5 if exc.status_code == 503 else None,
        )

    @app.exception_handler(Exception)
    async def unhandled_error_handler(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("unhandled_error")
        return problem_response(
            request_id=request.headers.get("x-request-id", "-"),
            status=500,
            code="internal_error",
            title="Internal Server Error",
            detail="An unexpected error occurred.",
        )
