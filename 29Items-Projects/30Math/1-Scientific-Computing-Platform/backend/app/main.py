"""Application factory: middleware, routers, and the error-handling contract.

Error philosophy (docs/ARCHITECTURE.md §2.6): every non-2xx response is RFC
7807 ``application/problem+json``; sciengine error codes map to statuses here
and nowhere else.

Middleware onion (outermost first): security headers → request-ID → rate
limit → CORS/GZip → routes. The request ID is set before rate limiting so
even 429s are correlated.
"""

from __future__ import annotations

import hashlib
import logging
import re
import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse

from app.api.v1.endpoints.health import router as health_router
from app.api.v1.router import api_router
from app.core.config import Settings, get_settings
from app.core.logging import configure_logging, request_id_var
from app.core.ratelimit import get_rate_limiter
from sciengine.exceptions import SciEngineError
from sciengine.runtime import shutdown_runner

logger = logging.getLogger(__name__)

_STATUS_BY_CODE: dict[str, int] = {
    "expression_parse_error": 422,
    "unsupported_expression": 422,
    "convergence_error": 422,
    "computation_timeout": 504,
    "computation_error": 500,
    "sciengine_error": 500,
}

_TIMEOUT_GUIDANCE = " Sign in and retry to run it as a background job, or simplify the input."

_REQUEST_ID_UNSAFE = re.compile(r"[^A-Za-z0-9._-]")
_SECURITY_HEADERS = {
    # HSTS is set at the edge (ALB/CloudFront) where TLS terminates.
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "strict-origin-when-cross-origin",
}


@asynccontextmanager
async def _lifespan(app: FastAPI) -> AsyncIterator[None]:
    yield
    shutdown_runner()  # terminate the sandbox child on clean shutdown


def _guard_production_secrets(settings: Settings) -> None:
    """Refuse to boot outside local/test with placeholder credentials."""
    if settings.environment in {"staging", "prod"} and settings.jwt_secret_key.startswith(
        "change-me"
    ):
        raise RuntimeError(
            "JWT_SECRET_KEY still has its placeholder value; set a real secret "
            "(Secrets Manager /scp/{env}/jwt_secret_key) before deploying."
        )


def _sanitized_request_id(raw: str | None) -> str:
    if not raw:
        return uuid.uuid4().hex
    cleaned = _REQUEST_ID_UNSAFE.sub("", raw)[:64]
    return cleaned or uuid.uuid4().hex


def _rate_limit_identity(request: Request) -> str:
    """JWT holders are limited per token; anonymous callers per client IP.

    X-Forwarded-For is only meaningful behind our own ALB (which overwrites
    the last hop); we take the first entry as the original client.
    """
    authorization = request.headers.get("Authorization")
    if authorization:
        return "tok:" + hashlib.sha256(authorization.encode()).hexdigest()[:24]
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded:
        return "ip:" + forwarded.split(",")[0].strip()
    return "ip:" + (request.client.host if request.client else "unknown")


def create_app() -> FastAPI:
    settings = get_settings()
    _guard_production_secrets(settings)
    configure_logging(level=settings.log_level, debug=settings.debug)

    app = FastAPI(
        title=settings.app_name,
        version="0.1.0",
        openapi_url=f"{settings.api_v1_prefix}/openapi.json",
        lifespan=_lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,  # explicit allowlist — never "*" with credentials
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "X-Request-ID"],
    )
    app.add_middleware(GZipMiddleware, minimum_size=1024)  # JSON + SVG compress well

    @app.middleware("http")
    async def rate_limit_middleware(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        limit = settings.rate_limit_per_minute
        if (
            limit > 0
            and request.method == "POST"
            and request.url.path.startswith(settings.api_v1_prefix)
        ):
            decision = get_rate_limiter().hit(_rate_limit_identity(request), limit)
            if not decision.allowed:
                response = _problem(
                    429,
                    "rate_limited",
                    "Too many requests; slow down and retry shortly.",
                )
                response.headers["Retry-After"] = str(decision.retry_after_seconds)
                return response
        return await call_next(request)

    @app.middleware("http")
    async def request_id_middleware(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        request_id = _sanitized_request_id(request.headers.get("X-Request-ID"))
        request_id_var.set(request_id)
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        return response

    @app.middleware("http")
    async def security_headers_middleware(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        response = await call_next(request)
        for header, value in _SECURITY_HEADERS.items():
            response.headers.setdefault(header, value)
        return response

    @app.exception_handler(SciEngineError)
    async def sciengine_error_handler(request: Request, exc: SciEngineError) -> JSONResponse:
        status = _STATUS_BY_CODE.get(exc.code, 500)
        # 4xx (and the user-actionable 504) teach the user what to fix;
        # other 5xx stay opaque and get logged loudly.
        if exc.code == "computation_timeout":
            detail = str(exc) + _TIMEOUT_GUIDANCE
        elif status < 500:
            detail = str(exc)
        else:
            detail = "Internal computation error."
        log = logger.warning if status < 500 else logger.error
        log("sciengine error: %s (%s)", exc, exc.code, exc_info=status >= 500)
        return _problem(status, exc.code, detail)

    @app.exception_handler(Exception)
    async def unhandled_error_handler(request: Request, exc: Exception) -> JSONResponse:
        logger.error("unhandled error on %s", request.url.path, exc_info=True)
        return _problem(500, "internal_error", "An unexpected error occurred.")

    app.include_router(health_router)
    app.include_router(api_router, prefix=settings.api_v1_prefix)
    return app


def _problem(status: int, code: str, detail: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        content={
            "type": f"https://scp.example.com/problems/{code}",
            "title": code.replace("_", " "),
            "status": status,
            "detail": detail,
            "request_id": request_id_var.get(),
        },
    )


app = create_app()
