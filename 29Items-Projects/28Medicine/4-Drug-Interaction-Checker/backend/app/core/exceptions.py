"""Domain errors and centralized FastAPI exception handlers.

The wire format is always: {"error": {"code": "...", "message": "..."}}.
"""

from fastapi import FastAPI, Request, status
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.core.logging import get_logger

logger = get_logger(__name__)


class DomainError(Exception):
    """Base class for business/domain errors."""

    status_code: int = status.HTTP_400_BAD_REQUEST
    error_code: str = "domain_error"

    def __init__(self, message: str) -> None:
        self.message = message
        super().__init__(message)


class DrugNotFoundError(DomainError):
    status_code = status.HTTP_404_NOT_FOUND
    error_code = "drug_not_found"


class RxNormUnavailableError(DomainError):
    status_code = status.HTTP_502_BAD_GATEWAY
    error_code = "rxnorm_unavailable"


class MLServiceError(DomainError):
    status_code = status.HTTP_502_BAD_GATEWAY
    error_code = "ml_service_error"


class TooManyDrugsError(DomainError):
    status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
    error_code = "too_many_drugs"


def _error_body(code: str, message: str) -> dict:
    return {"error": {"code": code, "message": message}}


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(DomainError)
    async def _domain_handler(request: Request, exc: DomainError) -> JSONResponse:
        logger.warning(
            "domain_error",
            error_code=exc.error_code,
            message=exc.message,
            path=request.url.path,
        )
        return JSONResponse(
            status_code=exc.status_code, content=_error_body(exc.error_code, exc.message)
        )

    @app.exception_handler(RequestValidationError)
    async def _validation_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        # jsonable_encoder + custom encoder makes the underlying ValueError in
        # pydantic's error ctx JSON-serializable.
        details = jsonable_encoder(exc.errors(), custom_encoder={Exception: str})
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={
                "error": {
                    "code": "validation_error",
                    "message": "Invalid request",
                    "details": details,
                }
            },
        )

    @app.exception_handler(Exception)
    async def _unhandled_handler(request: Request, exc: Exception) -> JSONResponse:
        # Log full context; never leak internals to the client.
        logger.error("unhandled_error", error=str(exc), path=request.url.path, exc_info=exc)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=_error_body("internal_error", "An unexpected error occurred."),
        )
