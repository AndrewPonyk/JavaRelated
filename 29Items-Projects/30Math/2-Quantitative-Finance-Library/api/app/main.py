"""FastAPI application factory for the quantfinlib API service.

Run locally:  uvicorn app.main:app --reload --app-dir api
"""

from __future__ import annotations

import asyncio
import logging
import uuid
from contextlib import asynccontextmanager
from pathlib import Path

import structlog
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text

from app.config import Settings, get_settings
from app.db.session import dispose_engine, get_session_factory, init_db
from app.routers import admin, options, risk, volatility
from app.services.job_runner import JobRunner
from quantfinlib import (
    HAS_NATIVE,
    ConvergenceError,
    ExtensionNotAvailable,
    InvalidInputError,
    __version__,
)

log = structlog.get_logger("quantfinlib.api")

WEB_DIR = Path(__file__).resolve().parents[2] / "web"


def configure_logging(settings: Settings) -> None:
    """structlog: JSON lines in prod, human-readable console elsewhere
    (TECH-NOTES §3.4). request_id is bound per request via contextvars."""
    level = getattr(logging, settings.log_level.upper(), logging.INFO)
    logging.basicConfig(level=level, format="%(message)s")
    renderer = (
        structlog.processors.JSONRenderer()
        if settings.env == "prod"
        else structlog.dev.ConsoleRenderer()
    )
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso"),
            renderer,
        ],
        wrapper_class=structlog.make_filtering_bound_logger(level),
        cache_logger_on_first_use=True,
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    if settings.require_native and not HAS_NATIVE:
        raise ExtensionNotAvailable(
            "QF_REQUIRE_NATIVE=1 but the compiled core is unavailable — refusing to "
            "start with the slow fallback in this environment."
        )
    await init_db()
    app.state.job_runner = JobRunner()
    log.info("quantfinlib API started", native=HAS_NATIVE, env=settings.env, version=__version__)
    yield
    await app.state.job_runner.shutdown()
    await dispose_engine()
    log.info("quantfinlib API stopped")


def create_app() -> FastAPI:
    settings = get_settings()
    configure_logging(settings)
    app = FastAPI(
        title="quantfinlib API",
        version=__version__,
        lifespan=lifespan,
        docs_url="/docs" if settings.env != "prod" else None,  # no docs UI in prod
        redoc_url=None,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["GET", "POST"],
        allow_headers=["X-API-Key", "Content-Type"],
    )
    # Priced chains and audit pages are large, repetitive JSON — compresses ~10x.
    app.add_middleware(GZipMiddleware, minimum_size=1024)

    @app.middleware("http")
    async def request_id_middleware(request: Request, call_next):
        # Correlates response ↔ logs ↔ audit rows; see ARCHITECTURE.md §2.6.
        request.state.request_id = str(uuid.uuid4())
        structlog.contextvars.bind_contextvars(request_id=request.state.request_id)
        try:
            response = await call_next(request)
        finally:
            structlog.contextvars.unbind_contextvars("request_id")
        response.headers["X-Request-ID"] = request.state.request_id
        return response

    # Library errors → RFC 7807 problem details. Unexpected errors fall through
    # to FastAPI's 500 handler (opaque to the client, full trace in logs).
    @app.exception_handler(InvalidInputError)
    async def invalid_input_handler(request: Request, exc: InvalidInputError):
        return JSONResponse(
            status_code=422,
            content={
                "type": "about:blank",
                "title": "Invalid input",
                "status": 422,
                "detail": str(exc),
                "request_id": request.state.request_id,
            },
        )

    @app.exception_handler(ConvergenceError)
    async def convergence_handler(request: Request, exc: ConvergenceError):
        return JSONResponse(
            status_code=409,
            content={
                "type": "about:blank",
                "title": "Numerical routine did not converge",
                "status": 409,
                "detail": str(exc),
                "diagnostics": {"iterations": exc.iterations, "residual": exc.residual},
                "request_id": request.state.request_id,
            },
        )

    @app.get("/health/live", tags=["health"])
    async def health_live():
        return {"status": "ok"}

    @app.get("/health/ready", tags=["health"])
    async def health_ready():
        async def probe() -> None:
            async with get_session_factory()() as session:
                await session.execute(text("SELECT 1"))

        try:
            await asyncio.wait_for(probe(), timeout=1.0)
        except Exception:
            log.warning("readiness probe failed: database unreachable")
            return JSONResponse(
                status_code=503, content={"status": "unavailable", "reason": "database unreachable"}
            )
        return {"status": "ok", "native_core": HAS_NATIVE, "version": __version__}

    app.include_router(options.router, prefix="/v1/options", tags=["options"])
    app.include_router(risk.router, prefix="/v1/risk", tags=["risk"])
    app.include_router(volatility.router, prefix="/v1/vol-surface", tags=["volatility"])
    app.include_router(admin.router, prefix="/v1/admin", tags=["admin"])

    if WEB_DIR.is_dir():  # demo UI (web/index.html) at /
        app.mount("/", StaticFiles(directory=WEB_DIR, html=True), name="web")

    return app


app = create_app()
