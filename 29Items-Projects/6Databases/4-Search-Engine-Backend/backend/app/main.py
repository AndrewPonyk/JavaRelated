"""Application entrypoint: app factory, lifespan-managed clients, middleware."""

import asyncio
import contextlib
import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import structlog
from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware

from app.api.v1.endpoints.health import router as health_router
from app.api.v1.router import api_router
from app.cache.redis_client import create_redis_client
from app.core.config import get_settings
from app.core.exceptions import problem_response, register_exception_handlers
from app.core.logging import configure_logging
from app.core.ratelimit import SlidingWindowLimiter, client_ip
from app.search.es_client import create_es_client
from app.services.outbox_worker import run_outbox_loop

logger = structlog.get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Open shared infrastructure clients once per process; close them on shutdown."""
    settings = get_settings()
    app.state.es = create_es_client(settings)
    app.state.redis = create_redis_client(settings)

    outbox_task: asyncio.Task[None] | None = None
    if settings.outbox_enabled:
        outbox_task = asyncio.create_task(run_outbox_loop(app.state.es, settings))

    logger.info("startup_complete", environment=settings.environment)
    yield

    if outbox_task is not None:
        outbox_task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await outbox_task
    await app.state.es.close()
    await app.state.redis.aclose()
    logger.info("shutdown_complete")


def create_app() -> FastAPI:
    settings = get_settings()
    configure_logging(debug=settings.debug)

    app = FastAPI(
        title="Search Engine Backend",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/docs" if settings.environment != "prod" else None,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=[
            "Authorization",
            "Content-Type",
            "X-API-Key",
            "X-Request-ID",
            "X-Session-ID",
        ],
    )
    # Search responses (hits + facets) compress ~5x; skip tiny payloads.
    app.add_middleware(GZipMiddleware, minimum_size=1024)

    limiter = SlidingWindowLimiter(settings.rate_limit_requests, settings.rate_limit_window_s)
    limited_paths = (
        f"{settings.api_v1_prefix}/search",
        f"{settings.api_v1_prefix}/suggest",
        f"{settings.api_v1_prefix}/events",  # click spam pollutes LTR training data
    )

    @app.middleware("http")
    async def request_context(request: Request, call_next) -> Response:  # type: ignore[no-untyped-def]
        """Bind a request id to the logging context, echo it back, rate-limit reads."""
        request_id = request.headers.get("x-request-id") or uuid.uuid4().hex[:12]
        structlog.contextvars.clear_contextvars()
        structlog.contextvars.bind_contextvars(request_id=request_id, path=request.url.path)

        if settings.rate_limit_enabled and request.url.path.startswith(limited_paths):
            ip = client_ip(
                dict(request.headers),
                request.client.host if request.client else "unknown",
            )
            if not limiter.allow(ip):
                logger.warning("rate_limited", ip=ip)
                return problem_response(
                    request_id=request_id,
                    status=429,
                    code="rate_limited",
                    title="Too Many Requests",
                    detail="Rate limit exceeded; slow down.",
                    retry_after=int(settings.rate_limit_window_s),
                )

        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        return response

    register_exception_handlers(app)
    app.include_router(health_router)  # /healthz, /readyz — unversioned
    app.include_router(api_router, prefix=settings.api_v1_prefix)
    return app


app = create_app()
