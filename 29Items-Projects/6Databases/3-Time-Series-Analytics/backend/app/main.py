"""Application factory and process entrypoint.

Run locally:  uvicorn app.main:app --reload
"""

from __future__ import annotations

import logging
import time
import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from http import HTTPStatus

from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app import __version__
from app.api.v1.router import api_router
from app.core.config import settings
from app.core.logging import configure_logging
from app.core.security import hash_password
from app.db import BackendUnavailableError, cassandra, influx, redis
from app.repositories import users as users_repo

logger = logging.getLogger(__name__)


async def _bootstrap_admin() -> None:
    """Create the dashboard admin once (LWT insert — never overwrites)."""
    if not settings.admin_password:
        return
    created = await users_repo.create_if_absent(
        settings.admin_username,
        hash_password(settings.admin_password),
        ["admin"],
    )
    if created:
        logger.info("bootstrap admin user %r created", settings.admin_username)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    """Connect data-plane clients; tolerate absence (degraded mode).

    A down backend keeps liveness green: only the endpoints that need it
    return 503, and /health/ready reports per-dependency status.
    """
    connected: set[str] = set()
    for name, connect in (
        ("cassandra", cassandra.connect),
        ("redis", redis.connect),
        ("influxdb", influx.connect),
    ):
        try:
            await connect()
            connected.add(name)
            logger.info("connected: %s", name)
        except Exception as exc:
            logger.warning("degraded mode — %s unavailable: %s", name, exc)

    if "cassandra" in connected:
        try:
            await _bootstrap_admin()
        except Exception as exc:
            logger.warning("admin bootstrap failed: %s", exc)

    yield
    for name, close in (
        ("influxdb", influx.close),
        ("redis", redis.close),
        ("cassandra", cassandra.close),
    ):
        try:
            await close()
        except Exception as exc:
            logger.warning("shutdown: closing %s failed: %s", name, exc)


def _error_body(request: Request, code: str, message: str, detail: object = None) -> dict:
    body: dict = {
        "error": {
            "code": code,
            "message": message,
            "request_id": getattr(request.state, "request_id", None),
        }
    }
    if detail is not None:
        body["error"]["detail"] = detail
    return body


def create_app() -> FastAPI:
    configure_logging(settings.log_level, settings.log_format)

    app = FastAPI(
        title="Time-Series Analytics API",
        version=__version__,
        lifespan=lifespan,
        docs_url="/docs" if settings.environment != "production" else None,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    # Metric series responses are large and highly compressible JSON.
    # (SSE responses are exempt automatically: streams carry no content-length
    # and events are smaller than minimum_size per flush.)
    app.add_middleware(GZipMiddleware, minimum_size=1024)

    @app.middleware("http")
    async def request_context(request: Request, call_next):
        """Request id for log/error correlation + API latency telemetry."""
        request.state.request_id = uuid.uuid4().hex[:12]
        started = time.perf_counter()
        response = await call_next(request)
        response.headers["X-Request-ID"] = request.state.request_id

        route = request.scope.get("route")
        path = getattr(route, "path", None)
        if path is not None and not path.startswith("/api/v1/health"):
            # Route TEMPLATE as the tag (low cardinality), never the raw URL.
            influx.write_point(
                "api_request",
                fields={
                    "count": 1,
                    "latency_ms": (time.perf_counter() - started) * 1000.0,
                },
                tags={
                    "method": request.method,
                    "path": path,
                    "status": f"{response.status_code // 100}xx",
                    "env": settings.environment,
                },
            )
        return response

    # --- single error shape for the whole API (docs/ARCHITECTURE.md §2.6) ---

    @app.exception_handler(BackendUnavailableError)
    async def backend_unavailable(request: Request, exc: BackendUnavailableError):
        return JSONResponse(
            status_code=503,
            content=_error_body(request, "backend_unavailable", str(exc)),
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_error(request: Request, exc: StarletteHTTPException):
        code = HTTPStatus(exc.status_code).name.lower()
        return JSONResponse(
            status_code=exc.status_code,
            content=_error_body(request, code, str(exc.detail)),
            headers=getattr(exc, "headers", None),
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError):
        return JSONResponse(
            status_code=422,
            content=_error_body(
                request,
                "validation_error",
                "Request validation failed.",
                detail=jsonable_encoder(exc.errors()),
            ),
        )

    @app.exception_handler(Exception)
    async def unhandled(request: Request, exc: Exception):
        rid = getattr(request.state, "request_id", None)
        logger.exception("unhandled error request_id=%s", rid)
        return JSONResponse(
            status_code=500,
            content=_error_body(request, "internal_error", "Internal server error."),
        )

    app.include_router(api_router, prefix="/api/v1")
    return app


app = create_app()
