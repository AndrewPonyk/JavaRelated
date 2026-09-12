"""FastAPI application factory.

Wires middleware, structured logging, exception handling, health probes, and
mounts the platform routers. Kept thin: all logic lives in ``services/``.
"""
from __future__ import annotations

import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware

from src.api.routers import ab_testing, drift, experiments, models, serving
from src.core.config import get_settings
from src.core.errors import register_exception_handlers
from src.core.logging import configure_logging, correlation_id


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup/shutdown hooks (logging, auth, DB schema, connection pools)."""
    settings = get_settings()
    configure_logging(settings.log_level)
    # Validate auth wiring at startup so misconfiguration fails fast.
    from src.api.dependencies import get_token_verifier
    from src.db.session import dispose_engine, init_models

    get_token_verifier()
    # Create tables if missing (SQLite dev / first run; Alembic owns prod schema).
    await init_models()
    yield
    await dispose_engine()
    # Production: also dispose MLflow/Redis pools here.


def create_app() -> FastAPI:
    """Construct and configure the FastAPI application."""
    settings = get_settings()
    app = FastAPI(
        title="Enterprise ML Platform",
        version="0.1.0",
        lifespan=lifespan,
    )

    app.add_middleware(GZipMiddleware, minimum_size=1024)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_allow_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.middleware("http")
    async def add_context_and_headers(request: Request, call_next) -> Response:
        cid = request.headers.get("X-Correlation-ID", str(uuid.uuid4()))
        correlation_id.set(cid)
        response = await call_next(request)
        response.headers["X-Correlation-ID"] = cid
        # Baseline security headers (HTTPS/HSTS is terminated at the ingress/LB).
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Referrer-Policy"] = "no-referrer"
        return response

    register_exception_handlers(app)

    @app.get("/health", tags=["ops"])
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/ready", tags=["ops"])
    async def ready() -> dict[str, str]:
        # Production: check DB, MLflow, Redis connectivity before reporting ready.
        return {"status": "ready"}

    app.include_router(experiments.router)
    app.include_router(models.router)
    app.include_router(serving.router)
    app.include_router(ab_testing.router)
    app.include_router(drift.router)
    return app


app = create_app()
