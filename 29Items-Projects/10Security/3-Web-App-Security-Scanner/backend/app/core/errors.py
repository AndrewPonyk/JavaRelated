"""Domain exceptions and their FastAPI error-envelope mappings.

Every error response has one shape (ARCHITECTURE 2.6):
    {"error": {"code": str, "message": str, "details": …, "request_id": str}}
No stack traces ever reach clients.
"""

from __future__ import annotations

from fastapi import Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

# HTTP exception already carries a code; envelope keeps detail as message.
from app.core.metrics import metrics


class DomainError(Exception):
    """Base for typed service-layer errors."""

    status_code = status.HTTP_400_BAD_REQUEST
    code = "domain_error"

    def __init__(self, message: str, *, details=None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details


class TargetNotAllowed(DomainError):
    status_code = status.HTTP_403_FORBIDDEN
    code = "target_not_allowed"


class ScanTimeout(DomainError):
    status_code = status.HTTP_504_GATEWAY_TIMEOUT
    code = "scan_timeout"


class ScannerUnavailable(DomainError):
    status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    code = "scanner_unavailable"


class ClassificationError(DomainError):
    status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
    code = "classification_error"


def _envelope(request: Request, code: str, message: str, http_status: int, details=None):
    metrics.count("http_errors_total", labels={"code": code})
    return JSONResponse(
        status_code=http_status,
        content={
            "error": {
                "code": code,
                "message": message,
                "details": details,
                "request_id": getattr(request.state, "request_id", None),
            }
        },
    )


def install_exception_handlers(app) -> None:
    @app.exception_handler(DomainError)
    async def domain_error_handler(request: Request, exc: DomainError):
        return _envelope(request, exc.code, exc.message, exc.status_code, exc.details)

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request: Request, exc: RequestValidationError):
        return _envelope(
            request,
            "validation_error",
            "Request validation failed",
            status.HTTP_422_UNPROCESSABLE_ENTITY,
            details=[
                {"loc": e.get("loc"), "msg": e.get("msg"), "type": e.get("type")}
                for e in exc.errors()
            ],
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_error_handler(request: Request, exc: StarletteHTTPException):
        return _envelope(request, f"http_{exc.status_code}", str(exc.detail), exc.status_code)

    @app.exception_handler(Exception)
    async def unhandled_handler(request: Request, exc: Exception):
        # Full trace only in server logs (with request_id); client gets a stub.
        import logging

        logging.getLogger(__name__).exception(
            "unhandled error request_id=%s", getattr(request.state, "request_id", "-")
        )
        return _envelope(
            request,
            "internal_error",
            "Internal server error",
            status.HTTP_500_INTERNAL_SERVER_ERROR,
        )
