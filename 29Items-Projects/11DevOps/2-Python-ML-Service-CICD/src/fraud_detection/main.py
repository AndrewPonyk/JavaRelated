"""Application factory for the Fraud Detection API."""

from __future__ import annotations

import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from http import HTTPStatus
from typing import Any

from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.middleware.gzip import GZipMiddleware

from fraud_detection import __version__
from fraud_detection.api.routes import admin, health, models, predictions
from fraud_detection.core.config import get_settings
from fraud_detection.core.errors import AppError
from fraud_detection.core.logging import (
    bind_request_id,
    clear_request_context,
    configure_logging,
    get_logger,
)
from fraud_detection.monitoring.metrics import render_metrics
from fraud_detection.monitoring.middleware import MetricsMiddleware

logger = get_logger(__name__)

PROBLEM_CONTENT_TYPE = "application/problem+json"


def _problem_response(
    status: int, title: str, detail: Any, extra: dict[str, Any] | None = None
) -> JSONResponse:
    """Build an RFC 7807 problem+json response."""
    body: dict[str, Any] = {
        "type": "about:blank",
        "title": title,
        "status": status,
        "detail": detail,
    }
    if extra:
        body.update(extra)
    return JSONResponse(status_code=status, content=body, media_type=PROBLEM_CONTENT_TYPE)


class RequestIDMiddleware(BaseHTTPMiddleware):
    """Accepts/generates an ``X-Request-ID`` and binds it to the log context."""

    async def dispatch(
        self, request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        request_id = request.headers.get("X-Request-ID") or uuid.uuid4().hex
        bind_request_id(request_id)
        try:
            response = await call_next(request)
            response.headers["X-Request-ID"] = request_id
            return response
        finally:
            clear_request_context()


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    """Application startup/shutdown hooks.

    Startup is deliberately fault-tolerant: the service must come up and
    serve scoring traffic (with the fallback model and without persistence
    if need be) even when the database or model store is unavailable.
    """
    settings = get_settings()
    configure_logging(settings.log_level)

    if settings.db_auto_create:
        try:
            from fraud_detection.db.session import init_db

            init_db()
        except Exception:  # noqa: BLE001 - policy: boot without a database
            logger.warning("db_init_failed - continuing without persistence", exc_info=True)

    try:
        from fraud_detection.api.deps import get_prediction_service

        get_prediction_service()
    except Exception:  # noqa: BLE001 - policy: warmup is best-effort
        logger.warning("model_warmup_failed - models will load lazily", exc_info=True)

    yield

    try:
        from fraud_detection.db.session import reset_engine

        reset_engine()
    except Exception:  # noqa: BLE001 - shutdown must never raise
        logger.warning("engine_dispose_failed", exc_info=True)


def create_app() -> FastAPI:
    """Build and configure the FastAPI application."""
    settings = get_settings()

    application = FastAPI(
        title=settings.app_name,
        version=__version__,
        debug=settings.debug,
        lifespan=lifespan,
    )

    application.include_router(health.router)
    application.include_router(predictions.router, prefix=settings.api_prefix)
    application.include_router(models.router, prefix=settings.api_prefix)
    application.include_router(admin.router, prefix=settings.api_prefix)

    if settings.metrics_enabled:
        application.add_middleware(MetricsMiddleware)
    application.add_middleware(RequestIDMiddleware)
    # Outermost: compress list/metrics payloads for clients that accept it.
    application.add_middleware(GZipMiddleware, minimum_size=1024)

    @application.exception_handler(AppError)
    async def app_error_handler(_: Request, exc: AppError) -> JSONResponse:
        return _problem_response(exc.status, exc.title, exc.detail)

    @application.exception_handler(StarletteHTTPException)
    async def http_error_handler(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        try:
            title = HTTPStatus(exc.status_code).phrase
        except ValueError:  # pragma: no cover - non-standard status codes
            title = "Error"
        return _problem_response(exc.status_code, title, exc.detail)

    @application.exception_handler(RequestValidationError)
    async def validation_error_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
        return _problem_response(
            422,
            "Unprocessable Entity",
            "Request validation failed.",
            extra={"errors": jsonable_encoder(exc.errors())},
        )

    @application.get("/metrics", include_in_schema=False)
    def metrics() -> Response:
        """Prometheus scrape endpoint (text exposition format)."""
        payload, content_type = render_metrics()
        return Response(content=payload, media_type=content_type)

    return application


app = create_app()
