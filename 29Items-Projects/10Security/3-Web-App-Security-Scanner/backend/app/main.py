"""Web App Security Scanner — FastAPI application entrypoint.

App factory pattern: keeps wiring explicit and tests import-light.
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager, suppress

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import PlainTextResponse

from app.api.v1.router import api_router
from app.core.config import settings
from app.core.errors import install_exception_handlers
from app.core.logging import setup_logging
from app.core.metrics import metrics
from app.core.middleware import RequestContextMiddleware
from app.db.seed import seed_admin
from app.db.session import SessionFactory, ping_database
from app.services.ml_classifier import classifier
from app.services.retention import purge_expired_evidence

logger = logging.getLogger(__name__)

DESCRIPTION = """
Automated DAST platform: OWASP ZAP scanning, SQLMap SQL-injection testing,
custom XSS payload engine, and ML-based severity classification.
"""

_RETENTION_INTERVAL_SECONDS = 24 * 3600


async def _retention_loop(stop: asyncio.Event) -> None:
    """Strip expired evidence daily (ARCHITECTURE 2.5). Never crashes the app."""
    while not stop.is_set():
        try:
            async with SessionFactory() as session:
                await purge_expired_evidence(session)
        except Exception:
            logger.exception("evidence retention purge failed")
        with suppress(TimeoutError):
            await asyncio.wait_for(stop.wait(), timeout=_RETENTION_INTERVAL_SECONDS)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Startup/shutdown hooks. Fails fast on a misconfigured environment."""
    setup_logging()
    await ping_database()  # fail fast if DB is unreachable
    async with SessionFactory() as session:
        await seed_admin(session)  # idempotent first-run admin
    await classifier.load()  # ML artifact if trained; rules baseline otherwise

    retention_stop: asyncio.Event | None = None
    retention_task: asyncio.Task | None = None
    if settings.EVIDENCE_RETENTION_DAYS > 0:
        retention_stop = asyncio.Event()
        retention_task = asyncio.create_task(_retention_loop(retention_stop))
    try:
        yield
    finally:
        if retention_task is not None and retention_stop is not None:
            retention_stop.set()
            retention_task.cancel()
            with suppress(asyncio.CancelledError):
                await retention_task


def create_app() -> FastAPI:
    app = FastAPI(
        title="Web App Security Scanner",
        description=DESCRIPTION,
        version="0.1.0",
        lifespan=lifespan,
        # OpenAPI only exposed when auth is enforced (prod leak guard)
        openapi_url="/openapi.json" if settings.ENV != "production" else None,
        docs_url="/docs" if settings.ENV != "production" else None,
    )

    # RequestContextMiddleware added last → outermost: every request (incl.
    # 404s and CORS preflights) gets an id, metrics, and an access log.
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    # Compress JSON lists / reports / metrics above 1 KiB (TLS terminates at nginx)
    app.add_middleware(GZipMiddleware, minimum_size=1024)
    app.add_middleware(RequestContextMiddleware)
    install_exception_handlers(app)

    @app.get("/health", tags=["meta"])
    async def health() -> dict[str, str]:
        """Liveness probe used by Docker/Compose and CI."""
        return {"status": "ok", "env": settings.ENV}

    @app.get("/metrics", tags=["meta"], response_class=PlainTextResponse)
    async def prometheus_metrics() -> str:
        """Prometheus scrape endpoint (text format 0.0.4)."""
        return metrics.render()

    app.include_router(api_router, prefix=settings.API_PREFIX)
    return app


app = create_app()
