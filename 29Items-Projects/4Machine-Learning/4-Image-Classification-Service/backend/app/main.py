"""FastAPI application factory.

Responsibilities wired here:
- Load the ONNX model + Redis cache ONCE via the lifespan (not per request).
- Initialize the database schema.
- Assign a request_id to every request for log correlation.
- Per-client rate limiting and request metrics.
- Map domain exceptions to the single error envelope.
"""

from __future__ import annotations

import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes import auth, categories, classification, health
from app.core.config import get_settings
from app.core.logging import configure_logging, get_logger, request_id_ctx
from app.core.metrics import REQUESTS
from app.core.ratelimit import RateLimiter
from app.db.session import init_db
from app.models.schemas import ErrorDetail, ErrorResponse
from app.services.cache import CacheService
from app.services.inference import InferenceError, InferenceService
from app.services.preprocessing import UnsupportedImageError

logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup/shutdown. Load heavy singletons once."""
    settings = get_settings()
    configure_logging(settings.log_level)

    init_db()

    # Cache: always safe to construct (degrades gracefully if Redis is down).
    app.state.cache_service = CacheService.from_url(
        settings.redis_url, settings.cache_ttl_seconds, settings.model_version
    )

    # Model: best-effort load. If artifacts are missing (e.g. local dev without a
    # model), the app still boots but /ready reports not-ready until loaded.
    app.state.inference_service = None
    try:
        app.state.inference_service = InferenceService.from_settings(settings)
        logger.info("model_loaded", extra={"extra": {"model_version": settings.model_version}})
    except Exception as exc:  # noqa: BLE001
        logger.error("model_load_failed", extra={"extra": {"error": str(exc)}})

    yield


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="Image Classification Service",
        version="1.0.0",
        description="Multi-label product image categorization (ViT/CLIP + ONNX).",
        lifespan=lifespan,
    )
    app.state.rate_limiter = RateLimiter(
        capacity=settings.rate_limit_capacity,
        refill_per_sec=settings.rate_limit_refill_per_sec,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"] if not settings.is_production else [],
        allow_methods=["*"],
        allow_headers=["*"],
    )

    _register_middleware(app, settings)
    _register_exception_handlers(app)

    app.include_router(health.router)
    app.include_router(auth.router)
    app.include_router(classification.router)
    app.include_router(categories.router)
    return app


def _client_key(request: Request) -> str:
    api_key = request.headers.get("X-API-Key")
    if api_key:
        return f"key:{api_key}"
    client = request.client.host if request.client else "unknown"
    return f"ip:{client}"


def _register_middleware(app: FastAPI, settings) -> None:
    @app.middleware("http")
    async def add_request_id(request: Request, call_next):
        request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        token = request_id_ctx.set(request_id)
        try:
            # Rate limiting (skip health/metrics probes).
            limited = (
                settings.rate_limit_enabled
                and request.url.path not in ("/health", "/ready", "/metrics")
                and not request.app.state.rate_limiter.allow(_client_key(request))
            )
            if limited:
                return _error(
                    status.HTTP_429_TOO_MANY_REQUESTS,
                    "RATE_LIMITED",
                    "Too many requests",
                )

            response = await call_next(request)
        finally:
            request_id_ctx.reset(token)

        response.headers["X-Request-ID"] = request_id
        REQUESTS.labels(
            method=request.method,
            path=request.url.path,
            status=str(response.status_code),
        ).inc()
        return response


def _error(status_code: int, code: str, message: str) -> JSONResponse:
    body = ErrorResponse(
        error=ErrorDetail(code=code, message=message, request_id=request_id_ctx.get())
    )
    return JSONResponse(status_code=status_code, content=body.model_dump())


def _register_exception_handlers(app: FastAPI) -> None:
    from fastapi.exceptions import RequestValidationError
    from starlette.exceptions import HTTPException as StarletteHTTPException

    @app.exception_handler(UnsupportedImageError)
    async def _unsupported(_: Request, exc: UnsupportedImageError):
        return _error(415, "UNSUPPORTED_IMAGE", str(exc))

    @app.exception_handler(InferenceError)
    async def _inference(_: Request, exc: InferenceError):
        logger.error("inference_error", extra={"extra": {"error": str(exc)}})
        return _error(500, "INFERENCE_ERROR", "Failed to classify image")

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError):
        return _error(422, "VALIDATION_ERROR", str(exc.errors()))

    @app.exception_handler(StarletteHTTPException)
    async def _http(_: Request, exc: StarletteHTTPException):
        code = {
            400: "BAD_REQUEST",
            401: "UNAUTHORIZED",
            403: "FORBIDDEN",
            404: "NOT_FOUND",
            409: "CONFLICT",
            413: "PAYLOAD_TOO_LARGE",
            415: "UNSUPPORTED_IMAGE",
            429: "RATE_LIMITED",
        }.get(exc.status_code, "ERROR")
        return _error(exc.status_code, code, str(exc.detail))

    @app.exception_handler(Exception)
    async def _unhandled(_: Request, _exc: Exception):
        logger.exception("unhandled_error")
        return _error(500, "INTERNAL_ERROR", "An unexpected error occurred")


app = create_app()
