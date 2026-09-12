"""ASGI application factory.

Wires middleware, structured logging, exception handlers, and the v1 router.
Run with: `uvicorn app.main:app`.
"""

from __future__ import annotations

import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager

import structlog
from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware

from app.api.v1.router import api_router
from app.core.config import settings
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging, get_logger
from app.db.init_db import startup_bootstrap

log = get_logger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    configure_logging(settings.log_level, json_output=settings.is_production)
    log.info("startup", app_env=settings.app_env)
    await startup_bootstrap()  # ensure buckets + seed dev admin (best-effort)
    yield
    log.info("shutdown")


def create_app() -> FastAPI:
    app = FastAPI(
        title="Medical Imaging Platform API",
        version="0.1.0",
        description="HIPAA-aligned PACS: DICOM ingestion, archive, DICOMweb, ML assist.",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    # Compress worklist/QIDO JSON (and any text payload) over ~1 KB.
    app.add_middleware(GZipMiddleware, minimum_size=1024)

    @app.middleware("http")
    async def correlation_id_mw(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        """Bind a correlation_id for the whole request so logs are traceable."""
        cid = request.headers.get("x-correlation-id", str(uuid.uuid4()))
        structlog.contextvars.bind_contextvars(correlation_id=cid)
        try:
            response = await call_next(request)
        finally:
            structlog.contextvars.unbind_contextvars("correlation_id")
        response.headers["x-correlation-id"] = cid
        return response

    register_exception_handlers(app)
    app.include_router(api_router, prefix=settings.api_v1_prefix)
    return app


app = create_app()
