"""Catalog & governance API — application factory.

Run locally:
    uvicorn app.main:app --reload --port 8000
"""

from __future__ import annotations

import logging
import time
import uuid

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse

from app.core.config import get_settings
from app.core.logging import configure_logging
from app.routers import audit, datasets, health
from app.services.dataset_service import (
    ConcurrentUpdateError,
    DatasetNotFoundError,
    DuplicateDatasetError,
)
from app.services.trino_client import TrinoUnavailableError

log = logging.getLogger("api")


def _problem(request: Request, status_code: int, title: str, detail: str) -> JSONResponse:
    """RFC 7807 problem response — clients never see stack traces."""
    return JSONResponse(
        status_code=status_code,
        media_type="application/problem+json",
        content={
            "type": "about:blank",
            "title": title,
            "status": status_code,
            "detail": detail,
            "instance": str(request.url.path),
        },
    )


def create_app() -> FastAPI:
    configure_logging()
    settings = get_settings()

    if not settings.auth_jwks_url:
        # Deliberate and loud: this mode must never reach production.
        log.warning(
            "AUTH DISABLED — development mode (no AUTH_JWKS_URL): "
            "all requests act as a local platform-admin"
        )

    app = FastAPI(
        title="Lakehouse Catalog API",
        version="1.0.0",
        description="Dataset registry, governance metadata, and audit trail "
        "for the Data Lakehouse Platform.",
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.add_middleware(GZipMiddleware, minimum_size=1024)

    @app.middleware("http")
    async def request_context(request: Request, call_next):
        request_id = request.headers.get("X-Request-ID") or uuid.uuid4().hex[:16]
        request.state.request_id = request_id
        started = time.perf_counter()
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        log.info(
            "request",
            extra={
                "request_id": request_id,
                "method": request.method,
                "path": request.url.path,
                "status_code": response.status_code,
                "duration_ms": round((time.perf_counter() - started) * 1000, 1),
            },
        )
        return response

    # --- Domain errors → problem responses (routers stay free of try/except) ---

    @app.exception_handler(DatasetNotFoundError)
    async def dataset_not_found(request: Request, exc: DatasetNotFoundError):
        return _problem(request, 404, "Dataset not found", str(exc))

    @app.exception_handler(DuplicateDatasetError)
    async def duplicate_dataset(request: Request, exc: DuplicateDatasetError):
        return _problem(request, 409, "Dataset already exists", f"dataset '{exc}' already exists")

    @app.exception_handler(ConcurrentUpdateError)
    async def concurrent_update(request: Request, exc: ConcurrentUpdateError):
        return _problem(request, 409, "Concurrent modification", str(exc))

    @app.exception_handler(TrinoUnavailableError)
    async def trino_unavailable(request: Request, exc: TrinoUnavailableError):
        return _problem(request, 502, "Query engine unavailable", str(exc))

    @app.exception_handler(Exception)
    async def unhandled_error(request: Request, exc: Exception):
        log.exception("unhandled error", extra={"path": request.url.path})
        return _problem(request, 500, "Internal server error", "An unexpected error occurred.")

    app.include_router(health.router)
    app.include_router(datasets.router, prefix="/api/v1")
    app.include_router(audit.router, prefix="/api/v1")
    return app


app = create_app()
