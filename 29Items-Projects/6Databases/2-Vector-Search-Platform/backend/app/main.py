"""ASGI application factory.

Run locally:  ``uvicorn app.main:app --reload``
"""

from __future__ import annotations

import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

import structlog
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.middleware.gzip import GZipMiddleware
from starlette.responses import Response

from app import __version__
from app.api.v1.router import api_router
from app.core.config import get_settings
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging, get_logger
from app.services.embedding_service import EmbeddingService

log = get_logger(__name__)


async def _connect_redis(redis_url: str | None) -> Any | None:
    """Best-effort Redis connection; the cache is optional and never blocks startup."""
    if not redis_url:
        return None
    try:
        import redis.asyncio as aioredis

        client = aioredis.from_url(redis_url, decode_responses=True)
        await client.ping()
        log.info("redis.connected", url=redis_url)
        return client
    except Exception as exc:
        log.warning("redis.unavailable", error=str(exc))
        return None


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Startup/shutdown: open cache + model, warm the backend, then clean up."""
    settings = get_settings()
    configure_logging(settings.log_level, json_logs=settings.is_production)
    log.info("startup", app_env=settings.app_env, default_backend=settings.default_backend)

    app.state.redis = await _connect_redis(settings.redis_url)
    app.state.embedder = EmbeddingService(settings, redis=app.state.redis)
    log.info("embedding.provider", provider=app.state.embedder.provider_name)

    # Convenience for local/sqlite dev; production uses Alembic migrations.
    if settings.is_sqlite:
        from app.db.session import create_all

        await create_all()

    # Warm the default backend, but don't crash startup if it's temporarily down.
    try:
        from app.vectorstores import get_vector_store

        await get_vector_store().ensure_ready()
    except Exception as exc:
        log.warning("backend.warmup_failed", error=str(exc))

    yield

    if app.state.redis is not None:
        await app.state.redis.aclose()
    try:
        from app.vectorstores import get_vector_store

        await get_vector_store().close()
    except Exception:
        pass
    from app.db.session import engine

    await engine.dispose()
    log.info("shutdown")


class RequestIDMiddleware(BaseHTTPMiddleware):
    """Bind a request id to logs and echo it back in the response headers."""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        request.state.request_id = request_id
        structlog.contextvars.bind_contextvars(request_id=request_id)
        try:
            response = await call_next(request)
        finally:
            structlog.contextvars.clear_contextvars()
        response.headers["X-Request-ID"] = request_id
        return response


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="Vector Search Platform",
        version=__version__,
        summary="Comparative vector database search + recall@k benchmarking.",
        lifespan=lifespan,
    )

    app.add_middleware(RequestIDMiddleware)
    app.add_middleware(GZipMiddleware, minimum_size=1000)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    register_exception_handlers(app)
    app.include_router(api_router, prefix=settings.api_v1_prefix)
    return app


app = create_app()
